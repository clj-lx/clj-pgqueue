(ns clj-lx.benchmark
  (:require [next.jdbc :as jdbc]
            [clojure.string :as string]
            [clj-lx.bootstrap :as b]
            [clj-lx.queue :as q]))


(def report (atom {:count 0}))

(defn report-counter [key atom old-state new-state]
	(println key " received job " new-state))

(defn job-counter [{:keys [id] :as job}]
	(swap! report update-in [:count] inc)
	(println "got job " job))

(defn run [jdbc-url]
	(let [queue  (q/new->queue {:datasource       (jdbc/get-datasource jdbc-url)
								:table-name       "jobs" 
								:polling-interval 100})
	      queue  (q/start queue {:callback job-counter})]
	
	(add-watch report :watcher report-counter)

	(q/push queue nil)
	@(future (Thread/sleep 1000)
	    (q/stop queue)
	    (println  ">>" report))))

(defn -main [& args]
  (let [jdbc-url   (or (System/getenv "BENCHMARK_DATABASE_URL") 
  	                   (ex-info "No BENCHMARK_DATABASE_URL env var provided" {:jdbc-url nil}))]
    (println "tearing down benchmark db:")
    (b/teardown "jobs" jdbc-url)
    (println "bootstraping benchmark db:")
    (b/bootstrap "jobs" jdbc-url)

    (println "running benchmarks...")
    (run jdbc-url)))