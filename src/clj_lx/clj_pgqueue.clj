(ns clj-lx.clj-pgqueue
  (:require [next.jdbc :as jdbc]
            [clj-lx.protocol :as q])
  (:import [org.postgresql PGConnection]))

(defn transactional-claim! [datasource fn]
  (jdbc/with-transaction [tx datasource]
    (let [[job] (jdbc/execute! tx ["UPDATE jobs SET status='initializing' WHERE id = (SELECT id FROM jobs WHERE status='new' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *;"])]
      (when job
        (do (fn job))))))

(defn start-queue* [{:keys [datasource channel] :as queue}]
  (let [conn   (.getConnection datasource)
        _rs (jdbc/execute! conn [(str "LISTEN " channel)])]
    (assoc queue :connection conn)))

(defn stop-queue* [{:keys [connection]}]
  (when connection
    (.close connection)))

(defn push* [{:keys [datasource] } ^bytes payload]
  (jdbc/execute!
    datasource
    ["INSERT INTO jobs(payload, status, created_at, updated_at) VALUES (?, 'new', NOW(), NOW());", payload]))

(defn subscribe* [{poll :polling-interval ds :datasource conn :connection ex-handler :exception-handler}  callback]
 (future
   (let [pgconn (.unwrap conn PGConnection)]
     (loop []
       (let [notifications (.getNotifications pgconn)]
         (doseq [^org.postgresql.core.Notification _wakeup notifications]
             (try
               (transactional-claim! ds callback)
               (catch Exception e (ex-handler e))))) ;; todo capture the job id ( move try-catch to other function )
       (Thread/sleep poll)
       (recur)))))

(defrecord PGQueue [datasource channel]
  q/QueueProtocol
  (start-queue [this] (start-queue* this))
  (stop-queue [this] (stop-queue* this))
  (push [this payload] (push* this payload))
  (subscribe [this callback] (subscribe* this callback)))

(defn new->PGQueue [{:keys [datasource channel exception-handler polling-interval]}]
  (map->PGQueue {:datasource (or datasource (throw (ex-info "Datasource is required" {:error "datasource is required"})))
                 :channel    (or channel (throw (ex-info "Channel is required" {:error "channel is required"})))
                 :exception-handler (or exception-handler println)
                 :polling-interval  (or polling-interval 1000)}))
