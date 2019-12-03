(ns clj-lx.protocol)

(defprotocol QueueProtocol
  (-start [this worker])
  (-stop [this])
  (-push [this payload]))
