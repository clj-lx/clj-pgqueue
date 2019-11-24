(ns clj-lx.queue
  (:require [clj-lx.protocol :as p]))

(defn start [queueable]
 (p/-start queueable))

(defn stop [queueable]
  (p/-stop queueable))

(defn subscribe [queueable listen-fn]
  (p/-subscribe queueable listen-fn))

(defn push [queueable payload]
  (p/-push queueable payload))