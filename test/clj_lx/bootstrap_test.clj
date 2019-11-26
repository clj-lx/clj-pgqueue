(ns clj-lx.bootstrap-test
  (:require [clojure.test :refer :all]
            [clj-lx.bootstrap :as bootstrap]
            [clj-lx.helper :as test.helper]))

(defn with-db [f]
  (test.helper/start-database)
  (f)
  (test.helper/stop-database))

(use-fixtures :once with-db)

(deftest test-boostrap
  (testing "should create table with given name"
    (let [jdbc-url (.getJdbcUrl @test.helper/db "postgres" "postgres")
          table-name "lisbon_table"]
      (bootstrap/bootstrap table-name jdbc-url)
      (is (test.helper/list-tables table-name))))

  (testing "should create function name"
    (let [jdbc-url (.getJdbcUrl @test.helper/db "postgres" "postgres")
          table-name "another_table"]
      (bootstrap/bootstrap table-name jdbc-url)
      (is (test.helper/list-functions (str table-name "_channel_notify")))))

  (testing "should create trigger name"
    (let [jdbc-url (.getJdbcUrl @test.helper/db "postgres" "postgres")
          table-name "again_another_table"]
      (bootstrap/bootstrap table-name jdbc-url)
      (is (test.helper/list-triggers (str table-name "_notify_trigger"))))))