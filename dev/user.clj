(ns user
  (:require
   [next.jdbc :as jdbc]
   [kaocha.repl :as kaocha.repl]
   [clj-lx.clj-pgqueue :as q]))


(defn run-all-tests []
  (kaocha.repl/run :unit))

(comment
  (def db {:dbtype "postgresql" :dbname "cljlx"})
  (def ds (jdbc/get-datasource db))

  (def queue
    (delay
     (-> (q/new-queue ds "jobs_status_channel")
         (q/start)
         (q/listen #(println "GOT NOTIFICATION" (java.util.Date.) %)))))

  (q/enqueue! @queue nil))

