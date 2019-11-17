(ns mping.clj-pgqueue-test
  (:require [clojure.test :refer :all]
            [next.jdbc :as jdbc]
            [mping.clj-pgqueue :refer :all])
  (:import [io.zonky.test.db.postgres.embedded EmbeddedPostgres]))

;; the test datasource
(def ^:dynamic *test-ds*)

(defn with-pg [f]
  (let [epg (.start (EmbeddedPostgres/builder))
        ds  (.getPostgresDatabase epg)]
    (jdbc/execute! ds [(slurp "resources/schema.sql")])
    (binding [*test-ds* ds]
      (f))
    (.close epg)))

(use-fixtures :once with-pg)

(deftest a-test
  (testing "FIXME, I fail."
    (println *test-ds*)
    (is (= 0 1))))
