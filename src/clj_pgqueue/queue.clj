(ns clj-pgqueue.queue
  (:require [clj-pgqueue.protocol :as p]
            [clj-pgqueue.impl.pgqueue :as pgqueue]))

(defn start [queueable]
 (p/-start queueable))

(defn stop [queueable]
  (p/-stop queueable))

(defn push [queueable payload]
  (p/-push queueable payload))

(defn new->queue
  ([datasource worker] (new->queue datasource worker {}))
  ([datasource worker
    {:keys [polling-interval table-name queue-name n-workers]
     :or {polling-interval 5000 table-name "jobs" n-workers 1 queue-name "default"}}]
   (pgqueue/new->PGQueue datasource worker {:table-name table-name
                                            :queue-name queue-name
                                            :n-workers n-workers
                                            :polling-interval polling-interval})))
