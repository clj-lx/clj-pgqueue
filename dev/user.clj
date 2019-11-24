(ns user
  (:require
   [next.jdbc :as jdbc]
   [kaocha.repl :as kaocha.repl]
   [clj-lx.impl.pgqueue :as pgqueue]
   [clj-lx.queue :as q])
  (:import [io.zonky.test.db.postgres.embedded EmbeddedPostgres]))

(def n "user")
(defn run-all-tests []
  (kaocha.repl/run :unit))

(comment
  (def epg (.start (EmbeddedPostgres/builder)))
  (def  ds  (.getPostgresDatabase epg))

  ;;setup tables and triggers
  (jdbc/execute! ds [(slurp "resources/schema.sql")])

  (def queue
     (-> (pgqueue/new->PGQueue {:datasource ds :channel "jobs_status_channel"})
         (q/start)))

  (q/subscribe queue #(println "GOT NOTIFICATION" (java.util.Date.) %))
  (q/push queue nil)

  "end-comment-here")

