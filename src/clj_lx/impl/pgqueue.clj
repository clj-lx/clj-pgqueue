(ns clj-lx.impl.pgqueue
  (:require [next.jdbc :as jdbc]
            [clj-lx.protocol :as q]
            [next.jdbc.result-set :as rs])
  (:import [org.postgresql PGConnection]))

(defn- fetch-available-job [{:keys [datasource table-name queue-name]}]
  (jdbc/execute-one!
   datasource
   [(str "UPDATE " table-name
         " SET status='running' WHERE id ="
         " (SELECT id FROM " table-name
          " WHERE status='new'"
          " AND queue_name = ?"
          " ORDER BY created_at asc, id asc FOR UPDATE SKIP LOCKED LIMIT 1)"
         "RETURNING *;") queue-name]
   {:return-keys true :builder-fn rs/as-unqualified-maps}))

(defn- update-job-status [{:keys [datasource table-name]} status job-id]
  (jdbc/execute!
   datasource
   [(str "UPDATE " table-name " SET status = ?::jobs_status WHERE id = ?") status job-id]))

(defn- push* [{:keys [datasource table-name queue-name]}  ^bytes payload]
  (jdbc/execute!
    datasource
    [(str "INSERT INTO " table-name
          " (queue_name, payload, status, created_at, updated_at) VALUES (?, ?, 'new', NOW(), NOW());")
     queue-name
     payload]))

(defn- try-run-job! [queue job subscriber-fn]
  (try
    (subscriber-fn job)
    (update-job-status queue "success" (:id job))
    (catch Exception e
      (update-job-status queue "error" (:id job)))))

(defn- claim-and-run-job! [queue fn]
  (when-let [job (fetch-available-job queue)]
    (try-run-job! queue job fn)))

(defn- start-queue* [{:keys [datasource channel] :as queue}]
  (let [conn (.getConnection datasource)
        _rs  (jdbc/execute! conn [(str "LISTEN " channel)])]
    (assoc queue :connection conn)))

(defn- stop-queue* [{:keys [connection]}]
  (when connection (.close connection)))

(defn- subscribe* [{poll :polling-interval conn :connection :as queue} callback]
  (future
    (let [pgconn (.unwrap conn PGConnection)]
      (loop []
        (doseq [^org.postgresql.core.Notification _notif (.getNotifications pgconn)]
          (claim-and-run-job! queue callback))
        (Thread/sleep poll)
        (recur)))))

(defrecord PGQueue [datasource channel]
  q/QueueProtocol
  (-start [this] (start-queue* this))
  (-stop [this] (stop-queue* this))
  (-push [this payload] (push* this payload))
  (-subscribe [this callback] (subscribe* this callback)))

(defn new->PGQueue [{:keys [datasource polling-interval table-name queue-name]
                     :or { queue-name "default"
                           polling-interval 1000
                           table-name "jobs"} :as args}]
  (map->PGQueue {:datasource       (or datasource
                                       (throw (ex-info "Datasource is required" {:error "missing_datasource" :args args})))
                 :channel          (str table-name "_channel")
                 :table-name       table-name
                 :queue-name       queue-name
                 :polling-interval polling-interval}))
