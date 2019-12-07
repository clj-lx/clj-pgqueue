(ns clj-lx.clj-pgqueue-test
  (:require [clojure.test :refer :all]
            [clj-lx.queue :as q]
            [clj-lx.helper :as test.helper]))

(defn setup-db [f]
  (test.helper/setup-database)
  (f)
  (test.helper/stop-database))

(use-fixtures :each setup-db)

(defn build-queue [queue-opts]
   (-> (q/new->queue (merge {:table-name "jobs" :polling-interval 100} queue-opts))))

(deftest test-listen-emits-notification
  (testing "should notify subscriber once new message arrives"
    (let [queue (build-queue {:datasource (test.helper/datasource)})
          spy   (atom {})
          queue (q/start queue {:callback (fn [job] (reset! spy job))})]

     (q/push queue nil)
     @(future (Thread/sleep 2000)
        (is @spy)
        (is (= "success" (:status (test.helper/fetch-job (:id @spy)))))
        (q/stop queue)))))

(deftest test-exception-marks-error-status
  (testing "should mark job with error status once exception appear on subscriber"
    (let [queue  (build-queue {:datasource (test.helper/datasource)})
          job-id (atom nil)]

      (q/start queue {:callback (fn [job]
                                  (reset! job-id (:id job))
                                  (throw (ex-info "boom!" {:error :test-failed})))})
      (q/push queue nil)
      @(future
         (Thread/sleep 1000)
         (let [job (test.helper/fetch-job @job-id)]
           (is (= "error" (:status job)))
           (q/stop queue))))))


(deftest test-subscribe-process
  (testing "should update job status to running while subscriber is processing"
    (let [spy (atom nil)
          queue (build-queue {:datasource (test.helper/datasource)})
          queue (q/start queue {:callback (fn [job]
                                            (is (= "running" (:status job)))
                                            (reset! spy (:id job)))})]

      (q/push queue (.getBytes "payload"))

      @(future
         (Thread/sleep 500)
         (let [job (test.helper/fetch-job @spy)]
           (is (= "success" (:status job)))
           (q/stop queue))))))

(deftest test-ordered-payload
 (testing "should respect insertion order when fetching new jobs"
   (let [queue (build-queue {:polling-interval 100 :datasource (test.helper/datasource)})
         spy   (atom [])]

    (test.helper/insert-job (.getBytes "payload #3") 0)
    (test.helper/insert-job (.getBytes "payload #1") -2)
    (test.helper/insert-job (.getBytes "payload #2") -1)

    ;; insert payload before any subscriber attached
    (is (= 3 (count (test.helper/fetch-new-jobs))))

    (let [queue (q/start queue {:callback (fn [job]
                                            (swap! spy conj (String. (:payload job))))})]

      (q/push queue (.getBytes "payload #4"))
      @(future
         (Thread/sleep 800)
         (is (= ["payload #1" "payload #2" "payload #3" "payload #4"] @spy))
         (q/stop queue))))))

(deftest test-multiple-queues
  (testing "should support multiple queues"
    (let [invoicing-spy (atom [])
          campaign-spy (atom [])
          invoicing-queue (build-queue {:polling-interval 100 :queue-name "invoicing-queue" :datasource (test.helper/datasource)})
          campaign-queue  (build-queue {:polling-interval 100 :queue-name "campaign-queue" :datasource (test.helper/datasource)})
          invoicing-queue (q/start invoicing-queue {:callback (fn [job]
                                                                (Thread/sleep 100) ;; force long task
                                                                (swap! invoicing-spy conj (String. (:payload job))))})
          campaign-queue  (q/start campaign-queue  {:callback (fn [job]
                                                                (swap! campaign-spy conj (String. (:payload job))))})]

      (q/push invoicing-queue (.getBytes "invoicing #1"))
      (q/push invoicing-queue (.getBytes "invoicing #2"))
      (q/push invoicing-queue (.getBytes "invoicing #3"))

      (q/push campaign-queue (.getBytes "campaign #1"))

      @(future
         (Thread/sleep 800)
         (is (= ["invoicing #1" "invoicing #2" "invoicing #3"] @invoicing-spy))
         (is (= ["campaign #1"] @campaign-spy))
         (q/stop invoicing-queue)
         (q/stop campaign-queue)))))
