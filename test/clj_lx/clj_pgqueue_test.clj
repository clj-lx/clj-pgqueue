(ns clj-lx.clj-pgqueue-test
  (:require [clojure.test :refer :all]
            [clj-lx.impl.pgqueue :as pgqueue]
            [clj-lx.queue :as q]
            [clj-lx.helper :as test.helper]))

(defn setup-db [f]
  (test.helper/setup-database)
  (f)
  (test.helper/stop-database))

(use-fixtures :once setup-db)

(defn build-queue [queue-opts]
   (-> (pgqueue/new->PGQueue (merge {:table-name "jobs" :polling-interval 100} queue-opts))
       (q/start)))

(deftest test-listen-emits-notification
  (testing "should notify subscriber once new message arrives"
    (let [queue (build-queue {:datasource (test.helper/datasource)})
          spy (atom {})]

     (q/subscribe queue (fn [job] (reset! spy job)))
     (q/push queue nil)
     @(future (Thread/sleep 500)
        (is @spy)
        (is (= "success" (:status (test.helper/fetch-job (:id @spy)))))
        (q/stop queue))))

  (testing "should mark job with error status once exception appear on subscriber"
    (let [queue (build-queue {:datasource (test.helper/datasource)})
          job-id (atom nil)]
      (q/subscribe queue (fn [job]
                           (reset! job-id (:id job))
                           (throw (ex-info "boom!" {:error :test-failed}))))
      (q/push queue nil)
      @(future
         (Thread/sleep 500)
         (let [job (test.helper/fetch-job @job-id)]
           (is (= "error" (:status job)))
           (q/stop queue)))))



  (testing "should claim for available jobs once a new subscriber is attached to que queue"
    (let [queue (build-queue {:datasource (test.helper/datasource)})
            job-id (atom nil)]

        (is (zero? (count (test.helper/fetch-new-jobs))))
        (test.helper/insert-job (.getBytes "payload")) ;; insert payload before any subscriber attached
        (is (= 1 (count (test.helper/fetch-new-jobs))))

        (q/subscribe queue (fn [job] (reset! job-id (:id job))))

        @(future
           (Thread/sleep 500)
           (let [job (test.helper/fetch-job @job-id)]
             (is (= "success" (:status job)))
             (q/stop queue)))))


  (testing "should update job status to running while subscriber is processing"
    (let [queue (build-queue {:datasource (test.helper/datasource)})
          job-id (atom nil)]

      (q/push queue (.getBytes "payload"))
      (q/subscribe queue (fn [job]
                           (is (= "payload" (String. (:payload job))))
                           (is (= "running" (:status job)))
                           (reset! job-id (:id job))))

      @(future
         (Thread/sleep 500)
         (let [job (test.helper/fetch-job @job-id)]
           (is (= "success" (:status job)))
           (q/stop queue)))))

  (testing "should respect insertion order when fetching new jobs"
    (let [queue (build-queue {:datasource (test.helper/datasource)})
          spy (atom [])]

      (test.helper/insert-job (.getBytes "payload #3") 0)
      (test.helper/insert-job (.getBytes "payload #1") -2)
      (test.helper/insert-job (.getBytes "payload #2") -1)

      (println (test.helper/fetch-new-jobs))
      ;; insert payload before any subscriber attached
      (is (= 3 (count (test.helper/fetch-new-jobs))))

      (q/subscribe queue (fn [job] (swap! spy conj (String. (:payload job)))))
      (q/push queue (.getBytes "payload #4"))

      @(future
         (Thread/sleep 800)
         (is (= ["payload #1" "payload #2" "payload #3" "payload #4"] @spy))
         (q/stop queue))))

  (testing "should support multiple queues"
    (let [invoicing-queue (build-queue {:queue-name "invoicing-queue" :datasource (test.helper/datasource)})
          campaign-queue  (build-queue {:queue-name "campaign-queue" :datasource (test.helper/datasource)})
          invoicing-spy (atom [])
          campaign-spy (atom [])]

      (q/subscribe invoicing-queue (fn [job]
                                     (Thread/sleep 100) ;; force long task
                                     (swap! invoicing-spy conj (String. (:payload job)))))

      (q/subscribe campaign-queue  (fn [job]
                                     (swap! campaign-spy conj (String. (:payload job)))))

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