(ns clj-lx.benchmark
  (:require [next.jdbc :as jdbc]
            [clojure.string :as string]
            [clj-lx.bootstrap :as b]
            [clj-lx.queue :as q]))

(def report (atom {:count 0}))
(def total-jobs 1000)

(defn stop-queue-watcher [queue]
  (fn report-counter [key atom old-state {count :count}]
    (when (= total-jobs count)
      (println "Finished all jobs")
      (q/stop queue))))

(defn job-counter [{:keys [id] :as job}]
  (swap! report update-in [:count] inc))

(defn run [jdbc-url]
  (let [queue (q/new->queue {:datasource       (jdbc/get-datasource jdbc-url)
                             :table-name       "jobs"
                             :polling-interval 100})
        queue (q/start queue {:callback job-counter :concurrent 10})]
    ;;stop the queue when we process all jobs
    (add-watch report :watcher (stop-queue-watcher queue))
    ;; insert all data
    (dotimes [_ total-jobs]
      (q/push queue nil))))
    

(defn -main [& args]
  (let [jdbc-url (or (System/getenv "BENCHMARK_DATABASE_URL") 
                     (first args)
                     (throw  (ex-info "No BENCHMARK_DATABASE_URL env var provided" {:jdbc-url nil})))]
    (println "Using jdbc url: " jdbc-url)
    (println "tearing down benchmark db")
    (b/teardown "jobs" jdbc-url)
    (println "bootstraping benchmark db")
    (b/bootstrap "jobs" jdbc-url)

    (println "running benchmarks...")
    (run jdbc-url)))


(comment
  ;; run this when benchmark is running
  ;; "select status, count(*) from jobs group by status;"
  (-main "jdbc:postgresql://localhost/pg_queue_bench"))
