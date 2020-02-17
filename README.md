![](https://github.com/clj-pgqueue/clj-pgqueue/workflows/Clojure%20CI/badge.svg)

# clj-pgqueue/clj-pgqueue

A Clojure library designed to use Postgres as a queue storage.
Inspired by https://layerci.com/blog/postgres-is-the-answer/

## Install

[![Clojars Project](https://img.shields.io/clojars/v/clj-pgqueue.svg)](https://clojars.org/clj-pgqueue)


## Usage

#### bootstrap

	(require '[clj-pgqueue.bootstrap :as b])
	(b/bootstrap "jobs" "jdbc:postgresql://localhost:5432/dbname")

#### single queue usage

	(require '[clj-pgqueue.queue :as q])

    (defn worker [job] 
      (println "processing " job))
      
	(def queue (-> (q/new->queue datasource worker) (q/start)))

	(q/push queue (.getBytes "payload"))
	(q/push queue (.getBytes "another payload"))

#### multiple queue usage

You can specify **queue name**

```
(require '[clj-pgqueue.queue :as q])

(defn email-worker [job] 
  (println "sending email worker"))
  
(defn invoice-worker [job]
  (pritnln "invoice worker"))  

(def mail-queue (-> (q/new->queue datasource email-worker {:queue-name "mail-queue"}) 
                    (q/start)))

(def invoicing-queue (-> (q/new->queue datasource invoice-worker {:queue-name "invoicing-queue"}) 
                         (q/start)))

(q/push invoicing-queue (.getBytes "invoice n#1"))
(q/push mail-queue (.getBytes "confirmation email"))

```


## TODO

- [x] use protocol based implementation (in branch `protocol-based-queue`)
- [ ] detect https://github.com/impossibl/pgjdbc-ng for a more efficient listening mechanism
- [ ] api to handle notifications in parallel (q/subscribe queue fn {:parallel 3})
- [ ] retry/backoff strategy

## License

Copyright © 2019 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.


## Testing

	clojure  -R:test -A:test-runner -m kaocha.runner
    ;; or
    ./bin/kaocha



## Cider

	clj -A:cider-clj:dev:test

## nRepl

   	clj-A:nrepl:dev



## Run tests from repl

    (user/run-all-tests)
