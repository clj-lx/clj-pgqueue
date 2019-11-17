(ns user
  (:require [next.jdbc :as jdbc])
  (:import [org.postgresql PGConnection]))

(def db {:dbtype "postgresql" :dbname "mping"})
(def ds (jdbc/get-datasource db))


;; (jdbc/execute! ds ["INSERT INTO jobs(payload, status,created_at, updated_at) VALUES ('\\xDEADBEEF', 'new', NOW(), NOW());"])


(defn transactional-claim! [queue fn]
  (jdbc/with-transaction [tx (:datasource queue)]
    (let [job (jdbc/execute! tx ["UPDATE jobs SET status='initializing' WHERE id = (SELECT id FROM jobs WHERE status='new' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *;"])]
      (fn job))))

(defn listen [queue fn & {sleep-ms :sleep-ms :or {sleep-ms 1000}}]
  ;; fn argument is org.postgresql.core.Notification
  (let [pgconn (.unwrap (:connection queue) PGConnection)]
    (future
      (loop []
        (let [notifications (.getNotifications pgconn)]
          (doseq [^org.postgresql.core.Notification notif notifications]
            ;; transactional claim will update status, which will emit a NOTIFY again
            (println "notif: " (.getParameter notif))
            (transactional-claim! queue fn)
            #_(fn {:name      (.getName notif)
                   :pid       (.getPID notif)
                   :parameter (.getParameter notif)})))
        (Thread/sleep sleep-ms)
        (recur))))
  queue)

;;;;
;; example 1

(defn new-queue [ds channel]
  {:datasource ds
   :channel    channel})
  
(defn start [queue]
  (let [conn   (.getConnection (:datasource queue))
        listen (jdbc/execute! conn [(str "LISTEN " (:channel queue))])]
    (assoc queue :connection conn)))
       

(defn close [queue]
  (.close (:connection queue)))

(defn enqueue! [queue ^bytes payload]
  ;; assert that payload is byte array
  (jdbc/execute!
   (:datasource queue)
   ["INSERT INTO jobs(payload, status,created_at, updated_at) VALUES (?, 'new', NOW(), NOW());", payload]))


(comment
  (def queue
    (delay
     (let [queue (new-queue ds "jobs_status_channel")]
       (-> queue
           start
           (listen #(println (java.util.Date.) %)))))))
 

(comment
  (enqueue! @queue nil))
