(ns clj-lx.queue
  (:require [clj-lx.protocol :as p]
            [clj-lx.impl.pgqueue :as pgqueue]))


(defn start [queueable {:keys [_workersk _callback] :as worker}]
 (p/-start queueable worker))

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
