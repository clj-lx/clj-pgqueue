(ns clj-pgqueue.protocol)

(defprotocol QueueProtocol
  (-start [this])
  (-stop [this])
  (-push [this payload] [this payload at]))
