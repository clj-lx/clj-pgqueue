(ns user
  (:require [next.jdbc :as jdbc])
  (:import [org.postgresql PGConnection]))

(def db {:dbtype "postgresql" :dbname "mping"})
(def ds (jdbc/get-datasource db))


;; (jdbc/execute! ds ["INSERT INTO jobs(payload, status,created_at, updated_at) VALUES ('\\xDEADBEEF', 'new', NOW(), NOW());"])


(defn listen [ds channel fn & {sleep-ms :sleep-ms :or {sleep-ms 1000}}]
  (future
    (let [conn   (.getConnection ds)
          listen (jdbc/execute! conn [(str "LISTEN " channel)])
          pgconn (.unwrap conn PGConnection)]
      (loop []
        (let [notifications (.getNotifications pgconn)]
          (doseq [n notifications]
            (fn n))
          (Thread/sleep sleep-ms)
          (recur))))))


(listen ds "jobs_status_channel" println)

(future-cancel *1)


