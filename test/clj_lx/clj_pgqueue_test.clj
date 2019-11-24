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

(deftest test-listen-emits-notification
  (testing "should notify subscriber once new message arrives"
    (let [spy (atom {})
          queue (-> (pgqueue/new->PGQueue {:datasource (test.helper/datasource)
                                           :channel "jobs_channel"
                                           :polling-interval 500}) (q/start))]

     (q/subscribe queue (fn [job] (reset! spy job)))
     (q/push queue nil)
     @(future (Thread/sleep 2000)
        (is @spy)
        (is (= "success" (:status (test.helper/fetch-job (:id @spy)))))
        (q/stop queue))))


  (testing "should mark job with error status once exception appear on subscriber"
    (let [queue         (-> (pgqueue/new->PGQueue {:datasource (test.helper/datasource)
                                                   :channel "jobs_channel"
                                                   :polling-interval 500}) (q/start))
          job-id (atom nil)]
      (q/subscribe queue (fn [job]
                           (reset! job-id (:id job))
                           (throw (ex-info "boom!" {:error :test-failed}))))
      (q/push queue nil)
      @(future
         (Thread/sleep 2000)
         (let [job (test.helper/fetch-job @job-id)]
           (is (= "error" (:status job)))
           (q/stop queue))))))