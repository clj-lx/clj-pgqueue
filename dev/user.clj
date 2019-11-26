(ns user
  (:require
   [next.jdbc :as jdbc]
   [kaocha.repl :as kaocha.repl]
   [clj-lx.bootstrap :as bootstrap]
   [clj-lx.impl.pgqueue :as pgqueue]
   [clj-lx.queue :as q])
  (:import [io.zonky.test.db.postgres.embedded EmbeddedPostgres]))

(defn run-all-tests []
  (kaocha.repl/run :unit))

(comment
  #_(def epg (.start (EmbeddedPostgres/builder)))
  #_(def ds (.getPostgresDatabase epg))
  (def ds (jdbc/get-datasource {:dbtype "postgres" :dbname "mping"}))
  
  ;;setup tables and triggers
  (jdbc/execute! ds [(bootstrap/build-ddl "jobs")])

  (def queue
    (-> (pgqueue/new->PGQueue {:datasource ds})
        (q/start)))

  (q/subscribe queue #(println "GOT NOTIFICATION" (java.util.Date.) %))
  (q/push queue nil)
  (q/stop queue)

  "end-comment-here")

