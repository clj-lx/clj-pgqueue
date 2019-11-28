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
	
	(def queue (q/start (pgqueue/new->PGQueue {:datasource datasource }))
	
	(q/subscribe queue (fn [job] (println "process your job" job)
	
	(q/push queue "payload")
	(q/push queue "another payload")
	
#### multiple queue usage	

You can specify **queue name** 

```
(require '[clj-pgqueue.queue :as q])
(require '[clj-pgqueue.impl.pgqueue :as pgqueue])

(def mail-queue (q/start (pgqueue/new->PGQueue {:queue-name "mail-queue"
                                                :datasource datasource }))

(def invoicing-queue (q/start (pgqueue/new->PGQueue {:queue-name "invoicing-queue" 
                                                     :datasource datasource 
                                                     :table-name "jobs"}))

(q/subscribe mail-queue (fn [job] (println "sending email" job)
(q/subscribe invoicing-queue (fn [job] (println "creating invoice" job)

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
