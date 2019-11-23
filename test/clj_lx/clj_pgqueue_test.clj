(ns clj-lx.clj-pgqueue-test
  (:require [clojure.test :refer :all]
            [clj-lx.clj-pgqueue :as clj-queue]
            [clj-lx.protocol :as q]
            [clj-lx.helper :as test.helper]))
(defn setup-db [f]
  (test.helper/setup-database)
  (f)
  (test.helper/stop-database))

(use-fixtures :once setup-db)

(deftest test-listen-emits-notification
  (testing "should notify subscriber once new message arrives"
    (let [spy (atom {})
          queue (-> (clj-queue/new->PGQueue {:datasource (test.helper/datasource)
                                             :channel "jobs_status_channel"
                                             :exception-handler (fn [e] (reset! spy (ex-data e)))
                                             :polling-interval 500}) (q/start-queue))]

     (q/subscribe queue (fn [job] (reset! spy job)))
     (q/push queue nil)
     @(future (Thread/sleep 2000)
        (is @spy)
        (q/stop-queue queue))))

  (testing "should calls exception handler when subscriber blows up"
    (let [spy (atom {})
          queue         (-> (clj-queue/new->PGQueue {:datasource (test.helper/datasource)
                                                     :channel "jobs_status_channel"
                                                     :exception-handler (fn [e] (reset! spy (ex-data e)))
                                                     :polling-interval 500})
                            (q/start-queue))]

      (q/subscribe queue (fn [_job] (throw (ex-info "boom!" {:error :test-failed}))))
      (q/push queue nil)
      @(future (Thread/sleep 2000)
         (is (= (:error @spy) :test-failed))
         (q/stop-queue queue)))))