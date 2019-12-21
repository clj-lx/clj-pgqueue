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
   (-> (q/new->queue (merge {:table-name "jobs" :polling-interval 50} queue-opts))))

(deftest test-listen-emits-notification
  (testing "should notify subscriber once new message arrives"
    (let [spy   (atom {})
          queue (build-queue {:worker (fn [job] (reset! spy job))
                              :datasource (test.helper/datasource)})
          queue (q/start queue)]

     (q/push queue nil)
     @(future (Thread/sleep 100)
        (is @spy)
        (is (= "success" (:status (test.helper/fetch-job (:id @spy)))))
        (q/stop queue)))))

(deftest test-exception-marks-error-status
  (testing "should mark job with error status once exception appear on subscriber"
    (let [job-id (atom nil)
          queue  (build-queue {:worker (fn [job]
                                         (reset! job-id (:id job))
                                         (throw (ex-info "boom!" {:error :test-failed})))
                               :datasource (test.helper/datasource)})]
      (q/start queue)
      (q/push queue nil)
      @(future
         (Thread/sleep 100)
         (let [job (test.helper/fetch-job @job-id)]
           (is (= "error" (:status job)))
           (q/stop queue))))))


(deftest test-subscribe-process
  (testing "should update job status to running while subscriber is processing"
    (let [spy (atom nil)
          queue (build-queue {:worker (fn [job] (is (= "running" (:status job))) (reset! spy (:id job)))
                              :datasource (test.helper/datasource)})
          queue (q/start queue)]

      (q/push queue (.getBytes "payload"))

      @(future
         (Thread/sleep 100)
         (let [job (test.helper/fetch-job @spy)]
           (is (= "success" (:status job)))
           (q/stop queue))))))

(deftest test-ordered-payload
 (testing "should respect insertion order when fetching new jobs"
   (let [spy   (atom [])
         queue (build-queue {:worker (fn [job] (swap! spy conj (String. (:payload job))))
                             :datasource (test.helper/datasource)})]

    (test.helper/insert-job (.getBytes "payload #3") 0)
    (test.helper/insert-job (.getBytes "payload #1") -2)
    (test.helper/insert-job (.getBytes "payload #2") -1)

    ;; insert payload before any subscriber attached
    (is (= 3 (count (test.helper/fetch-new-jobs))))

    (let [queue (q/start queue)]
      (q/push queue (.getBytes "payload #4"))
      @(future
         (Thread/sleep 250)
         (is (= ["payload #1" "payload #2" "payload #3" "payload #4"] @spy))
         (q/stop queue))))))

(deftest test-multiple-queues
  (testing "should support multiple queues"
    (let [invoicing-spy (atom [])
          campaign-spy (atom [])
          invoicing-queue (build-queue {:worker (fn [job]
                                                  (Thread/sleep 20) ;; force long task
                                                  (swap! invoicing-spy conj (String. (:payload job))))
                                        :queue-name "invoicing-queue"
                                        :datasource (test.helper/datasource)})
          campaign-queue  (build-queue {:worker (fn [job] (swap! campaign-spy conj (String. (:payload job))))
                                        :queue-name "campaign-queue"
                                        :datasource (test.helper/datasource)})
          invoicing-queue (q/start invoicing-queue)
          campaign-queue  (q/start campaign-queue)]

      (q/push invoicing-queue (.getBytes "invoicing #1"))
      (q/push invoicing-queue (.getBytes "invoicing #2"))
      (q/push invoicing-queue (.getBytes "invoicing #3"))

      (q/push campaign-queue (.getBytes "campaign #1"))

      @(future
         (Thread/sleep 200)
         (is (= ["invoicing #1" "invoicing #2" "invoicing #3"] @invoicing-spy))
         (is (= ["campaign #1"] @campaign-spy))
         (q/stop invoicing-queue)
         (q/stop campaign-queue)))))


(deftest test-parallel-processing
  (testing "should handle N jobs in parallel"
    (let [spy   (atom [])
          queue (build-queue {:n-workers 6
                              :worker (fn [job] (Thread/sleep 200)
                                                (swap! spy conj (String. (:payload job))))
                              :datasource (test.helper/datasource)})]

      (test.helper/insert-job (.getBytes "payload #1"))
      (test.helper/insert-job (.getBytes "payload #2"))
      (test.helper/insert-job (.getBytes "payload #3"))
      (test.helper/insert-job (.getBytes "payload #4"))
      (test.helper/insert-job (.getBytes "payload #5"))
      (test.helper/insert-job (.getBytes "payload #6"))

      (let [queue (q/start queue)]
        @(future
             (Thread/sleep 250)
             (is (= (count @spy) 6))
             (q/stop queue))))))