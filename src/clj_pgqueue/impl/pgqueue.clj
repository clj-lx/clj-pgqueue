(ns clj-pgqueue.impl.pgqueue
  (:require [next.jdbc :as jdbc]
            [java-time :as time]
            [clj-pgqueue.protocol :as q]
            [clojure.tools.logging :as log]
            [next.jdbc.result-set :as rs])
  (:import (java.util.concurrent Executor Executors ThreadPoolExecutor)))

(defn- fetch-available-job [{:keys [datasource table-name queue-name n-workers]}]
  (jdbc/execute!
    datasource
   [(str "UPDATE " table-name
         " SET status='running' WHERE id in "
         " (SELECT id FROM " table-name
          " WHERE status='new'"
          " AND queue_name = ?"
          " AND now() >= run_at"
          " ORDER BY run_at asc, id asc"
          " FOR UPDATE SKIP LOCKED LIMIT " n-workers ")"
         "RETURNING *;") queue-name]
   {:return-keys true :builder-fn rs/as-unqualified-maps}))

(defn- update-job-status [{:keys [datasource table-name]} status job-id]
  (jdbc/execute!
   datasource
   [(str "UPDATE " table-name " SET status = ? WHERE id = ?") status job-id]))

(defn- push*
  ([queue ^bytes payload] (push* queue payload (time/local-date-time)))
  ([{:keys [datasource table-name queue-name]} ^bytes payload at]
   (jdbc/execute!
     datasource
     [(str "INSERT INTO " table-name
           " (queue_name, payload, run_at, status, created_at, updated_at) VALUES (?, ?, ?, 'new', NOW(), NOW());")
      queue-name
      payload
      at])))

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

(defn wait-time [executor sleep-time]
  (let [thread-pool-executor (cast ThreadPoolExecutor executor)
        n-tasks (.getTaskCount thread-pool-executor)
        n-threads (.getPoolSize thread-pool-executor)
        power (max 0 (- n-tasks n-threads))
        sleep (* (Math/pow 2 power) sleep-time)]
       (when (> sleep 15000)
         (log/info "[QUEUE OVER CAPACITY] n-tasks " n-tasks " n-running-threads " n-threads " sleep(ms) " sleep))
       (min 15000 sleep)))

(defn- run-queue [{:keys [executor polling-interval worker] :as queue}]
  (loop []
    (doseq [job (fetch-available-job queue)]
      (.submit executor #(try-run-job! queue job)))
    (Thread/sleep (wait-time executor polling-interval))
    (recur)))

(defn- start-queue* [{:keys [n-workers] :as queue}]
  (let [queue  (assoc queue :executor (Executors/newFixedThreadPool n-workers))]
    (assoc queue :runner (future (run-queue queue)))))

(defrecord PGQueue [datasource worker]
  q/QueueProtocol
  (-start [this] (start-queue* this))
  (-stop [this] (stop-queue* this))
  (-push [this payload] (push* this payload))
  (-push [this payload at] (push* this payload at)))

(defn new->PGQueue [datasource worker {:keys [polling-interval table-name queue-name n-workers] :as args}]
  (map->PGQueue {:datasource (or datasource
                                 (throw (ex-info "Datasource is required" {:error "missing_datasource" :args args})))
                 :worker (or worker
                             (throw (ex-info "Worker function must be supplied" {:error "missing_worker_fn" :args args})))
                 :table-name table-name
                 :n-workers n-workers
                 :queue-name queue-name
                 :polling-interval polling-interval}))
