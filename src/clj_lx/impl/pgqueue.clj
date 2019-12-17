(ns clj-lx.impl.pgqueue
  (:require [next.jdbc :as jdbc]
            [clj-lx.protocol :as q]
            [next.jdbc.result-set :as rs])
  (:import (java.util.concurrent Executor Executors)))

(defn- fetch-available-jobs [{:keys [datasource table-name queue-name]} jobs-limit]
  (jdbc/execute!
   datasource
   [(str "UPDATE " table-name
         " SET status='running' WHERE id in"
         " (SELECT id FROM " table-name
          " WHERE status='new'"
          " AND queue_name = ?"
          " ORDER BY created_at asc, id asc"
          " FOR UPDATE SKIP LOCKED LIMIT " jobs-limit ")"
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

(defn- try-run-job! [queue job subscriber-fn]
  (try
    (subscriber-fn job)
    (update-job-status queue "success" (:id job))
    (catch Exception e
      (update-job-status queue "error" (:id job)))))

(defn- stop-queue* [{:keys [datasource executor runner]}]
  (when executor
    (.shutdown executor))
  (when runner
    (future-cancel runner)))

(defn- run-queue [{:keys [executor polling-interval] :as queue} {:keys [callback concurrent]}]
  (loop []
    (let [jobs (fetch-available-jobs queue concurrent)]
      (doseq [job jobs]
        (.submit executor #(try-run-job! queue job callback))))
    (Thread/sleep polling-interval)
    (recur)))

(defn- start-queue* [queue opts]
  (let [queue-opts (merge {:concurrent 1} opts)
        queue      (assoc queue :executor (Executors/newFixedThreadPool (:concurrent queue-opts)))]
    (assoc queue :runner (future (run-queue queue queue-opts)))))

(defrecord PGQueue [datasource]
  q/QueueProtocol
  (-start [this opts] (start-queue* this opts))
  (-stop [this] (stop-queue* this))
  (-push [this payload] (push* this payload)))

(defn new->PGQueue [{:keys [datasource polling-interval table-name queue-name]
                     :or { queue-name "default"
                           polling-interval 1000
                           table-name "jobs"} :as args}]
  (map->PGQueue {:datasource (or datasource
                                 (throw (ex-info "Datasource is required" {:error "missing_datasource" :args args})))
                 :table-name table-name
                 :queue-name queue-name
                 :polling-interval polling-interval}))
