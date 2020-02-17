(ns user
  (:require
   [next.jdbc :as jdbc]
   [kaocha.repl :as kaocha.repl]
   [clj-pgqueue.bootstrap :as bootstrap]
   [clj-pgqueue.impl.pgqueue :as pgqueue]
   [clj-pgqueue.queue :as q]))

(defn run-all-tests []
  (kaocha.repl/run :unit))

(defn named-args [& {:keys [a b]}]
  (println a b))

(apply named-args {:a 1})

(comment
  (run-all-tests)
  (def ds (jdbc/get-datasource {:dbtype "postgres" :dbname "mping"}))

  (q/new->queue  ds println)
  (def queue
    (->))


  "end-comment-here")

