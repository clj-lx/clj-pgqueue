(ns user
  (:require
   [next.jdbc :as jdbc]
   [mping.clj-pgqueue :as q]
   [kaocha.repl :as kaocha.repl]))


(defn run-all-tests []
  (kaocha.repl/run :unit))

(comment
  (def db {:dbtype "postgresql" :dbname "mping"})
  (def ds (jdbc/get-datasource db))

  (def queue
    (delay
     (-> (q/new-queue ds "jobs_status_channel")
         (q/start)
         (q/listen #(println "GOT NOTIFICATION" (java.util.Date.) %)))))

  (q/enqueue! @queue nil))

