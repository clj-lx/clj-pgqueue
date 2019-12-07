![](https://github.com/clj-lx/clj-pgqueue/workflows/Clojure%20CI/badge.svg)
# clj-lx/clj-pgqueue

A Clojure library designed to use Postgres as a queue storage.
Inspired by https://layerci.com/blog/postgres-is-the-answer/

## How

- There's a `jobs` table (the table name is configurable)
 - a trigger fires on `insert` and `update`, calling a function
 - a function calls `pg_notify(channel, jobid)`
 
- On the sql client:
 - a thread is polling the channel through the connection's `getNotifications` method
 - when a notification arrives, subscribers are notified

## Usage

#### bootstrap

	(require '[clj-pgqueue.bootstrap :as b])
	(b/bootstrap "jobs" "jdbc:postgresql://localhost:5432/dbname")

#### single queue usage

	(require '[clj-pgqueue.queue :as q])
	(require '[clj-pgqueue.impl.pgqueue :as pgqueue])
	
	(def queue (pgqueue/new->PGQueue {:datasource datasource })
	(def worker {:callback (fn [job] (println "process your job" job)})
	(q/start queue worker)
	
	(q/push queue "payload")
	(q/push queue "another payload")
	
#### multiple queue usage	

You can specify **queue name** and how many threads will handle the queue.
a thread pool `newFixedThreadPool`  will be created with the number of threads you specify

```
(require '[clj-pgqueue.queue :as q])
(require '[clj-pgqueue.impl.pgqueue :as pgqueue])

(def mail-queue (pgqueue/new->PGQueue {:queue-name "mail-queue"
                                       :datasource datasource }))

(def invoicing-queue (pgqueue/new->PGQueue {:queue-name "invoicing-queue" 
                                            :datasource datasource 
                                            :table-name "jobs"}))

(def mail-worker {:callback (fn [job] (println "sending email" job) :concurrent 2})
(q/start mail-queue mail-worker)

(def invoicing-worker {:callback (fn [job] (println "creating invoice" job) :concurrent 3})
(q/start invoicing-queue invoicing-worker)

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
