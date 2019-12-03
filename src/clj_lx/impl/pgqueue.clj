(ns clj-lx.impl.pgqueue
  (:require [next.jdbc :as jdbc]
            [clj-lx.protocol :as q]
            [next.jdbc.result-set :as rs])
  (:import (java.util.concurrent Executor Executors)))

(defn- fetch-available-job [{:keys [datasource table-name queue-name]} jobs-limit]
  (jdbc/execute!
   datasource
   [(str "UPDATE " table-name
         " SET status='running' WHERE id ="
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

(defn- stop-queue* [{:keys [executor runner]}]
  (when executor
    (.shutdown executor))
  (when runner
    (future-cancel runner)))

(defn- run-queue [{:keys [executor polling-interval] :as queue} {:keys [callback concurrent]}]
  (loop []
    (doseq [job (fetch-available-job queue concurrent)]
      (.submit executor #(try-run-job! queue job callback)))
    (Thread/sleep polling-interval)
    (recur)))

(defn- start-queue* [queue worker]
  (let [worker (merge worker {:concurrent 1})
        queue (assoc queue :executor (Executors/newFixedThreadPool (:concurrent worker)))]
    (println "calling runner with:" (:concurrent worker) (:callback worker))
    (assoc queue :runner (future (run-queue queue worker)))))

(defrecord PGQueue [datasource channel]
  q/QueueProtocol
  (-start [this worker] (start-queue* this worker))
  (-stop [this] (stop-queue* this))
  (-push [this payload] (push* this payload)))

(defn new->PGQueue [{:keys [datasource polling-interval table-name queue-name concurrent]
                     :or { queue-name "default"
                           polling-interval 1000
                           concurrent 1
                           table-name "jobs"} :as args}]
  (map->PGQueue {:datasource (or datasource
                                 (throw (ex-info "Datasource is required" {:error "missing_datasource" :args args})))
                 :channel (str table-name "_channel")
                 :table-name table-name
                 :queue-name queue-name
                 :concurrent concurrent
                 :polling-interval polling-interval}))
