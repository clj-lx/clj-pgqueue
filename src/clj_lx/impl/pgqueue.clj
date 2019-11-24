(ns clj-lx.impl.pgqueue
  (:require [next.jdbc :as jdbc]
            [clj-lx.protocol :as q]
            [next.jdbc.result-set :as rs])
  (:import [org.postgresql PGConnection]))

(defn- fetch-available-job [datasource]
  (jdbc/execute-one! datasource
                     ["UPDATE jobs SET status='running' WHERE id = (SELECT id FROM jobs WHERE status='new' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *;"]
                     {:return-keys true :builder-fn rs/as-unqualified-maps}))

(defn- update-job-status [datasource status job-id]
  (jdbc/execute! datasource ["UPDATE jobs SET status = ?::job_status WHERE id = ?" status job-id]))

(defn- try-run-job! [datasource job subscriber-fn]
  (try
    (subscriber-fn job)
    (update-job-status datasource "success" (:id job))
    (catch Exception e
      (update-job-status datasource "error" (:id job)))))

(defn- claim-and-run-job! [datasource fn]
  (let [job (fetch-available-job datasource)]
    (when job
      (try-run-job! datasource job fn))))

(defn- start-queue* [{:keys [datasource channel] :as queue}]
  (let [conn   (.getConnection datasource)
        _rs (jdbc/execute! conn [(str "LISTEN " channel)])]
    (assoc queue :connection conn)))

(defn- stop-queue* [{:keys [connection]}]
  (when connection
    (.close connection)))

(defn- push* [{:keys [datasource] } ^bytes payload]
  (jdbc/execute!
    datasource
    ["INSERT INTO jobs(payload, status, created_at, updated_at) VALUES (?, 'new', NOW(), NOW());", payload]))

(defn- subscribe* [{poll :polling-interval ds :datasource conn :connection}  callback]
  (future
    (let [pgconn (.unwrap conn PGConnection)]
      (loop []
        (let [notifications (.getNotifications pgconn)]
          (doseq [^org.postgresql.core.Notification _wakeup notifications]
            (claim-and-run-job! ds callback)))
        (Thread/sleep poll)
        (recur)))))

(defrecord PGQueue [datasource channel]
  q/QueueProtocol
  (-start [this] (start-queue* this))
  (-stop [this] (stop-queue* this))
  (-push [this payload] (push* this payload))
  (-subscribe [this callback] (subscribe* this callback)))

(defn new->PGQueue [{:keys [datasource channel polling-interval]}]
  (map->PGQueue {:datasource (or datasource (throw (ex-info "Datasource is required" {:error "datasource is required"})))
                 :channel    (or channel (throw (ex-info "Channel is required" {:error "channel is required"})))
                 :polling-interval  (or polling-interval 1000)}))
