(ns clj-lx.protocol)

(defprotocol QueueProtocol
  (-start [this])
  (-stop [this])
  (-push [this payload])
  (-subscribe [this callback]))
