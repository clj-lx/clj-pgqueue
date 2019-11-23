(ns clj-lx.helper
  (:require [next.jdbc :as jdbc])
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
  ([] (.getPostgresDatabase (database)))
  ([db] (.getPostgresDatabase db)))

(defn run-schema [db]
  (jdbc/execute! (datasource db) [(slurp "resources/schema.sql")]))

(defn setup-database []
  (let [db (start-database)]
    (run-schema db)))

