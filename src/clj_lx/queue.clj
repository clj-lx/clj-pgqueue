(ns clj-lx.queue
  (:require [clj-lx.protocol :as p]))

(defn start [queueable worker]
 (p/-start queueable worker))

(defn stop [queueable]
  (p/-stop queueable))

(defn push [queueable payload]
  (p/-push queueable payload))

