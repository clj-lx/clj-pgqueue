(ns clj-lx.bootstrap
  (:require [next.jdbc :as jdbc]
            [clojure.string :as string]))

(defn build-ddl [tbl-name]
  (as-> (string/lower-case tbl-name) %
        (string/replace % "-" "_")
        (string/replace (slurp "resources/schema.sql.template") ":table-name" %)))

(defn bootstrap [table-name jdbc-url]
  (jdbc/execute!
    (jdbc/get-datasource {:jdbcUrl jdbc-url})
    [(build-ddl table-name)]))

(defn -main [& args]
  (let [table-name (or (first args) "jobs")
        jdbc-url (or (System/getenv "DATABASE_URL") (throw "missing DATABASE_URL"))]
    (bootstrap table-name jdbc-url)))

(comment
  (def jdbc-url "jdbc:postgresql://localhost:5432/db_name")
  (bootstrap "jobx" jdbc-url)
  "end comment")


