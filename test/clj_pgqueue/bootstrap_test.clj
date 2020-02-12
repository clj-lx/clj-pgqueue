(ns clj-pgqueue.bootstrap-test
  (:require [clojure.test :refer :all]
            [clj-pgqueue.bootstrap :as bootstrap]
            [clj-pgqueue.helper :as test.helper]))

(defn with-db [f]
  (test.helper/start-database)
  (f)
  (test.helper/stop-database))

(use-fixtures :once with-db)

(deftest test-boostrap
  (testing "should create table with given name"
    (let [jdbc-url   (.getJdbcUrl @test.helper/db "postgres" "postgres")
          table-name "lisbon_table"]
      (bootstrap/bootstrap table-name jdbc-url)
      (is (test.helper/list-tables table-name)))))
