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

(defn build-queue [datasource]
  (-> (pgqueue/new->PGQueue {:datasource       datasource
                             :table-name       "jobs"
                             :polling-interval 100})
     (q/start)))

(deftest test-listen-emits-notification
  (testing "should notify subscriber once new message arrives"
    (let [queue (build-queue (test.helper/datasource))
          spy (atom {})]

     (q/subscribe queue (fn [job] (reset! spy job)))
     (q/push queue nil)
     @(future (Thread/sleep 500)
        (is @spy)
        (is (= "success" (:status (test.helper/fetch-job (:id @spy)))))
        (q/stop queue))))

  (testing "should mark job with error status once exception appear on subscriber"
    (let [queue (build-queue (test.helper/datasource))
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
    (let [queue (build-queue (test.helper/datasource))
          job-id (atom nil)]

      (is (zero? (count (test.helper/fetch-new-jobs))))
      (test.helper/insert-job (.getBytes "payload")) ;; insert payload before any subscriber attached
      (is (= 1 (count (test.helper/fetch-new-jobs))))

      (q/subscribe queue (fn [job] (reset! job-id (:id job))))

      @(future
         (Thread/sleep 500)
         (let [job (test.helper/fetch-job @job-id)]
           (is (= "success" (:status job)))
           (q/stop queue))))))
