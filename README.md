![](https://github.com/clj-lx/clj-pgqueue/workflows/Clojure%20CI/badge.svg)
# clj-lx/clj-pgqueue

A Clojure library designed to use Postgres as a queue storage.
Inspired by https://layerci.com/blog/postgres-is-the-answer/

## Usage

#### bootstrap

	(require '[clj-pgqueue.bootstrap :as b])
	(b/bootstrap "jobs" "jdbc:postgresql://localhost:5432/dbname")

#### single queue usage

	(require '[clj-pgqueue.queue :as q])
	
	(def worker (fn [job] (println "process your job" job))
	(def queue (pgqueue/new->queue {:datasource datasource :worker worker})
	(q/start queue)
	
	(q/push queue "payload")
	(q/push queue "another payload")
	
#### multiple queue usage	

You can specify **queue name** and how many threads will handle the queue. 
 
```
(require '[clj-pgqueue.queue :as q])

(def mail-worker (fn [job] (println "sending email" job))
(def mail-queue (q/new->queue {:queue-name "mail-queue"
                               :worker mail-worker
                               :n-workers 2
                               :datasource datasource }))

(def invoicing-worker (fn [job] (println "creating invoice" job))

(def invoicing-queue (q/new->queue {:queue-name "invoicing-queue" 
                                    :worker invoicing-worker
                                    :n-workers 3
                                    :datasource datasource 
                                    :table-name "jobs"}))

(q/start mail-queue)
(q/start invoicing-queue)

(q/push invoicing-queue (.getBytes "invoice n#1"))
(q/push mail-queue (.getBytes "confirmation email"))

```
## todo ( help us )

- [ ] retry/backoff strategy
- [ ] detect https://github.com/impossibl/pgjdbc-ng for a more efficient listening mechanism

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
