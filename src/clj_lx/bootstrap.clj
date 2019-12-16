(ns clj-lx.bootstrap
  (:require [next.jdbc :as jdbc]
            [clojure.string :as string]))

(defn build-ddl [table-name]
  (as-> (string/lower-case table-name) %
        (string/replace % "-" "_")
        (string/replace (slurp "resources/schema.sql.template") ":table-name" %)))

(defn bootstrap [table-name jdbc-url]
  (jdbc/execute!
    (jdbc/get-datasource {:jdbcUrl jdbc-url})
    [(build-ddl table-name)]))

(defn teardown [table-name jdbc-url]
  (let [drop-ddl (as-> (string/lower-case table-name) %
                        (string/replace % "-" "_")
                        (string/replace (slurp "resources/schema-teardown.sql.template") ":table-name" %))]
  (jdbc/execute!
    (jdbc/get-datasource {:jdbcUrl jdbc-url})
    [drop-ddl])))


(defn -main [& args]
  (let [table-name (or (first args) "jobs")
        jdbc-url   (or (System/getenv "DATABASE_URL")
                       (throw "missing DATABASE_URL"))]
    (println "bootstraping, table-name:" table-name)
    (bootstrap table-name jdbc-url)
    (println"done !")))

(comment
  (def jdbc-url "jdbc:postgresql://localhost:5432/db_name")
  (bootstrap "jobx" jdbc-url)
  "end comment")


