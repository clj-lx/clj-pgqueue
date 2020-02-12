(ns clj-pgqueue.impl.pgqueue
  (:require [next.jdbc :as jdbc]
            [clj-pgqueue.protocol :as q]
            [next.jdbc.result-set :as rs])
  (:import (java.util.concurrent Executor Executors)))

(defn- fetch-available-job [{:keys [datasource table-name queue-name n-workers]}]
  (jdbc/execute!
    datasource
   [(str "UPDATE " table-name
         " SET status='running' WHERE id in "
         " (SELECT id FROM " table-name
          " WHERE status='new'"
          " AND queue_name = ?"
          " ORDER BY created_at asc, id asc"
          " FOR UPDATE SKIP LOCKED LIMIT " n-workers ")"
         "RETURNING *;") queue-name]
   {:return-keys true :builder-fn rs/as-unqualified-maps}))

(defn- update-job-status [{:keys [datasource table-name]} status job-id]
  (jdbc/execute!
   datasource
   [(str "UPDATE " table-name " SET status = ?::jobs_status WHERE id = ?") status job-id]))

(defn- push* [{:keys [datasource table-name queue-name]} ^bytes payload]
  (jdbc/execute!
    datasource
    [(str "INSERT INTO " table-name
          " (queue_name, payload, status, created_at, updated_at) VALUES (?, ?, 'new', NOW(), NOW());")
     queue-name
     payload]))

(defn- try-run-job! [{:keys [worker] :as queue} job]
  (try
    (worker job)
    (update-job-status queue "success" (:id job))
    (catch Exception e
      (update-job-status queue "error" (:id job)))))

(defn- stop-queue* [{:keys [executor runner]}]
  (when executor
    (.shutdown executor))
  (when runner
    (future-cancel runner)))

(defn- run-queue [{:keys [executor polling-interval worker] :as queue}]
  (loop []
    (doseq [job (fetch-available-job queue)]
      (.submit executor #(try-run-job! queue job)))
    (Thread/sleep polling-interval)
    (recur)))

(defn- start-queue* [{:keys [n-workers] :as queue}]
  (let [queue  (assoc queue :executor (Executors/newFixedThreadPool n-workers))]
    (assoc queue :runner (future (run-queue queue)))))

(defrecord PGQueue [datasource]
  q/QueueProtocol
  (-start [this] (start-queue* this))
  (-stop [this] (stop-queue* this))
  (-push [this payload] (push* this payload)))

(defn new->PGQueue [{:keys [datasource polling-interval table-name queue-name n-workers worker]
                     :or { queue-name "default"
                           n-workers 1
                           polling-interval 1000
                           table-name "jobs"} :as args}]

  (map->PGQueue {:datasource (or datasource
                                 (throw (ex-info "Datasource is required" {:error "missing_datasource" :args args})))
                 :worker (or worker
                             (throw (ex-info "Worker function must be supplied" {:error "missing_worker_fn" :args args})))
                 :table-name table-name
                 :n-workers n-workers
                 :queue-name queue-name
                 :polling-interval polling-interval}))
