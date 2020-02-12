(ns clj-pgqueue.helper
  (:require [next.jdbc :as jdbc]
            [clj-pgqueue.bootstrap :as bootstrap]
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

(defn fetch-job [id]
  (jdbc/execute-one! (datasource)
                     ["select * from jobs where id = ?" id]
                     {:return-keys true :builder-fn rs/as-unqualified-lower-maps}))

(defn insert-job
  ([payload] (insert-job payload 0))
  ([payload diff-created-at] (insert-job payload diff-created-at "default"))
  ([payload diff-created-at queue-name]
   (let [created-at (str "now() + interval '" diff-created-at " day'")]
     (jdbc/execute-one!
       (datasource)
       [(str "insert into jobs (queue_name, payload, status, created_at, updated_at) values (?, ?,'new', " created-at " ,now())") queue-name payload]))))

(defn fetch-new-jobs []
  (jdbc/execute! (datasource)
                 ["select * from jobs where status = 'new'"]
                 {:return-keys true :builder-fn rs/as-unqualified-lower-maps}))

(defn setup-database [table-name]
  (start-database)
  (run-schema (database) table-name))

