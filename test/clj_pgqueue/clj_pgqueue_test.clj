(ns clj-pgqueue.clj-pgqueue-test
  (:require [clojure.test :refer :all]
            [clj-pgqueue.queue :as q]
            [clj-pgqueue.helper :as test.helper]))

(def table-name "jobs")

(defn setup-db [f]
  (test.helper/setup-database table-name)
  (f)
  (test.helper/stop-database))

(use-fixtures :each setup-db)

(defn build-queue [datasource worker options]
  (q/new->queue datasource worker
                 (merge {:table-name table-name :polling-interval 50} options)))

(deftest test-listen-emits-notification
  (testing "should notify worker once new message arrives"
    (let [spy   (atom {})
          worker (fn [job] (reset! spy job))
          queue (build-queue (test.helper/datasource) worker {})
          queue (q/start queue)]

     (q/push queue nil)
     @(future (Thread/sleep 100)
        (is @spy)
        (is (= "success" (:status (test.helper/fetch-job (:id @spy)))))
        (q/stop queue)))))

(deftest test-exception-marks-error-status
  (testing "should mark job with error status once exception raised by worker"
    (let [job-id (atom nil)
          worker (fn [job]
                   (reset! job-id (:id job))
                   (throw (ex-info "boom!" {:error :test-failed})))
          queue  (build-queue (test.helper/datasource) worker {})]
      (q/start queue)
      (q/push queue nil)
      @(future
         (Thread/sleep 100)
         (let [job (test.helper/fetch-job @job-id)]
           (is (= "error" (:status job)))
           (q/stop queue))))))


(deftest test-subscribe-process
  (testing "should update job status to running while worker is processing"
    (let [spy (atom nil)
          worker (fn [job] (is (= "running" (:status job))) (reset! spy (:id job)))
          queue (build-queue (test.helper/datasource) worker {})
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
         worker (fn [job] (swap! spy conj (String. (:payload job))))
         queue (build-queue (test.helper/datasource) worker {})]

    (test.helper/insert-job (.getBytes "payload #3") 0)
    (test.helper/insert-job (.getBytes "payload #1") -2)
    (test.helper/insert-job (.getBytes "payload #2") -1)

    (is (= 3 (count (test.helper/fetch-new-jobs))))

    (let [queue (q/start queue)]
      (q/push queue (.getBytes "payload #4"))
      @(future
         (Thread/sleep 350)
         (is (= ["payload #1" "payload #2" "payload #3" "payload #4"] @spy))
         (q/stop queue))))))

(deftest test-multiple-queues
  (testing "should support multiple queues"
    (let [invoicing-spy (atom [])
          campaign-spy (atom [])
          invoicing-worker (fn [job]
                             (Thread/sleep 20) ;; force long task
                             (swap! invoicing-spy conj (String. (:payload job))))
          invoicing-queue (build-queue (test.helper/datasource) invoicing-worker {:queue-name "invoicing-queue"})

          campaign-worker (fn [job] (swap! campaign-spy conj (String. (:payload job))))
          campaign-queue  (build-queue (test.helper/datasource) campaign-worker {:queue-name "campaign-queue"})
          invoicing-queue (q/start invoicing-queue)
          campaign-queue  (q/start campaign-queue)]

      (q/push invoicing-queue (.getBytes "invoicing #1"))
      (q/push invoicing-queue (.getBytes "invoicing #2"))
      (q/push invoicing-queue (.getBytes "invoicing #3"))

      (q/push campaign-queue (.getBytes "campaign #1"))

      @(future
         (Thread/sleep 350)
         (is (= ["invoicing #1" "invoicing #2" "invoicing #3"] @invoicing-spy))
         (is (= ["campaign #1"] @campaign-spy))
         (q/stop invoicing-queue)
         (q/stop campaign-queue)))))


(deftest test-parallel-processing
  (testing "should handle N jobs in parallel"
    (let [spy   (atom [])
          worker(fn [job] (Thread/sleep 200)
                  (swap! spy conj (String. (:payload job))))
          queue (build-queue (test.helper/datasource) worker {:n-workers 6})]

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


(deftest enqueing-to-future
  (testing "should not fetch job before 'run-at' time"
    (let [spy   (atom [])
          worker(fn [job] (Thread/sleep 200)
                  (swap! spy conj (String. (:payload job))))
          queue (build-queue (test.helper/datasource) worker {:n-workers 6})]

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
