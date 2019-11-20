(ns mping.clj-pgqueue
  (:require [next.jdbc :as jdbc])
  (:import [org.postgresql PGConnection]))

(defn transactional-claim! [{datasource :datasource} fn]
  (jdbc/with-transaction [tx datasource]
    (let [[job] (jdbc/execute! tx ["UPDATE jobs SET status='initializing' WHERE id = (SELECT id FROM jobs WHERE status='new' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *;"])]
      (when job
        (fn job)))))

(defn listen [{connection :connection :as queue} fn & {sleep-ms :sleep-ms :or {sleep-ms 1000}}]
  ;; fn argument is org.postgresql.core.Notification
  (let [pgconn (.unwrap connection PGConnection)]
    (future
      (loop []
        (let [notifications (.getNotifications pgconn)]
          (doseq [^org.postgresql.core.Notification notif notifications]
            ;; transactional claim will update status, which will emit a NOTIFY again
            (transactional-claim! queue fn)
            #_(fn {:name      (.getName notif)
                   :pid       (.getPID notif)
                   :parameter (.getParameter notif)})))
        (Thread/sleep sleep-ms)
        (recur))))
  queue)

;;;;
;; example 1

(defn new-queue [datasource channel]
  {:datasource datasource
   :channel    channel})
  
(defn start [{:keys [datasource channel] :as queue}]
  (let [conn   (.getConnection datasource)
        listen (jdbc/execute! conn [(str "LISTEN " channel)])]
    (assoc queue :connection conn)))
       

(defn close [{connection :connection}]
  (.close connection))

(defn enqueue! [{datasource :datasource} ^bytes payload]
  ;; assert that payload is byte array
  (jdbc/execute!
   datasource
   ["INSERT INTO jobs(payload, status,created_at, updated_at) VALUES (?, 'new', NOW(), NOW());", payload]))



