(ns clj-lx.protocol)

(defprotocol QueueProtocol
  ;; to avoid Component name collision (start, stop)
  (start-queue [this])
  (stop-queue [this])
  (push [this payload])
  (subscribe [this callback]))
