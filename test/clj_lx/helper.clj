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

(defn list-tables [table-name]
  (jdbc/execute-one! (datasource)
                     ["SELECT * FROM pg_catalog.pg_tables where tablename = ?" table-name]
                     {:return-keys true :builder-fn rs/as-unqualified-lower-maps}))

(defn list-functions [fn-name]
  (jdbc/execute-one! (datasource)
                     ["SELECT * FROM information_schema.routines where routine_name = ?" fn-name]
                     {:return-keys true :builder-fn rs/as-unqualified-lower-maps}))

(defn list-triggers [trigger-name]
  (jdbc/execute-one! (datasource)
                     ["SELECT * FROM information_schema.triggers where trigger_name = ?" trigger-name]
                     {:return-keys true :builder-fn rs/as-unqualified-lower-maps}))

(defn fetch-job [id]
  (jdbc/execute-one! (datasource)
                     ["select * from jobs where id = ?" id]
                     {:return-keys true :builder-fn rs/as-unqualified-lower-maps}))

(defn insert-job [payload]
  (jdbc/execute-one!
    (datasource)
    ["insert into jobs (payload, status, created_at, updated_at) values (?,?::jobs_status,NOW(),NOW())" payload "new"]))

(defn fetch-new-jobs []
  (jdbc/execute! (datasource)
                 ["select * from jobs where status = 'new'"]
                 {:return-keys true :builder-fn rs/as-unqualified-lower-maps}))

(defn setup-database []
  (start-database)
  (run-schema (database) "jobs"))

