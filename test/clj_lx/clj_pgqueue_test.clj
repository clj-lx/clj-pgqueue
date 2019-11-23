(ns clj-lx.clj-pgqueue-test
  (:require [clojure.test :refer :all]
            [next.jdbc :as jdbc]
            [clj-lx.clj-pgqueue :as clj-queue]
            [clj-lx.protocol :as q])
  (:import [io.zonky.test.db.postgres.embedded EmbeddedPostgres]))

;; the test datasource
(def ^:dynamic *test-ds*)

(defn with-pg [f]
  (let [epg (.start (EmbeddedPostgres/builder))
        ds  (.getPostgresDatabase epg)]
    (jdbc/execute! ds [(slurp "resources/schema.sql")])
    (binding [*test-ds* ds]
      (f))
    (.close epg)))

(use-fixtures :once with-pg)

(deftest test-listen-emits-notification
  (testing "Listen emits a notification"
    (let [notif-called? (atom false)
          queue         (-> (clj-queue/new->PGQueue *test-ds* "jobs_status_channel" 1000)
                            (q/start-queue))
          _ (println ">>>>>>>>>>>>" queue)
          subscrtiber   (q/subscribe queue #(do
                                             (println "[TEST] GOT NOTIFICATION" (java.util.Date.) %)
                                             (reset! notif-called? true)))]
      (q/push queue nil)
      @(future
        (Thread/sleep 2000)
        (println "Thread woke, is notif called?")
        (is @notif-called?)))))


(comment
  (def db {:dbtype "postgresql" :dbname "cljlx"})
  (def ds (jdbc/get-datasource db))

  (def queue
    (delay
     (-> (clj-queue/new-queue ds "jobs_status_channel")
         clj-queue/start
         (clj-queue/listen #(println "GOT NOTIFICATION" (java.util.Date.) %)))))

  (clj-queue/enqueue! @queue nil))
