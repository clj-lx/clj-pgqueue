(ns clj-pgqueue.queue
  (:require [clj-pgqueue.protocol :as p]
            [clj-pgqueue.impl.pgqueue :as pgqueue]))


(defn start [queueable]
 (p/-start queueable))

(defn stop [queueable]
  (p/-stop queueable))

(defn push [queueable payload]
  (p/-push queueable payload))

;; conf
;;  {
;;   :datasource (REQUIRED)
;;   :polling-interval ;; default value 1000 ms
;;   :table-name       ;; default value 'jobs'
;;   :queue-name       ;; default value 'default'
;;  }
(defn new->queue [conf]
  (pgqueue/new->PGQueue conf))