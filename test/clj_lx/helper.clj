(ns clj-lx.helper
  (:require [next.jdbc :as jdbc]
            [clj-lx.bootstrap :as bootstrap]
            [next.jdbc.result-set :as rs])
  (:import (io.zonky.test.db.postgres.embedded EmbeddedPostgres)))

(def db (atom nil))

(defn database []
  (when-not @db (throw (ex-info "Database not started" {:error :db})))
  @db)

(defn start-database []
  (reset! db (.start (EmbeddedPostgres/builder))))

(defn stop-database []
  (.close (database)))

(defn datasource
  ([] (datasource (database)))
  ([db] (.getPostgresDatabase db)))

(defn run-schema [db table-name]
  (jdbc/execute! (datasource db) [(bootstrap/build-ddl table-name)]))

(defn fetch-job [id]
  (jdbc/execute-one! (datasource)
                     ["select * from jobs where id = ?" id]
                     {:return-keys true :builder-fn rs/as-unqualified-lower-maps}))
(defn setup-database []
  (start-database)
  (run-schema (database) "jobs"))

