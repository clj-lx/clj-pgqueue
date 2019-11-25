![](https://github.com/clj-lx/clj-pgqueue/workflows/Clojure%20CI/badge.svg)
# clj-lx/clj-pgqueue

A Clojure library designed to use Postgres as a queue storage.
Inspired by https://layerci.com/blog/postgres-is-the-answer/

## Usage


	(def db {:dbtype "postgresql" :dbname "cljlx"})
	(def ds (jdbc/get-datasource db))

	;; create a queue, but don't start yet
	(def queue
		(delay
		 (-> (q/new-queue ds "jobs_status_channel")
		     (q/start)
		     (q/listen #(println "GOT NOTIFICATION" (java.util.Date.) %)))))
    ;; start
	@queue
    
	;; put something there	
	(q/enqueue! @queue nil))

## TODO

- [x] use protocol based implementation (in branch `protocol-based-queue`)
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
    
    
## Run tests from repl

    (user/run-all-tests)
