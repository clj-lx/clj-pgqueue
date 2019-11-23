![](https://github.com/clj-lx/clj-pgqueue/workflows/Clojure%20CI/badge.svg)
# clj-lx/clj-pgqueue

A Clojure library designed to use Postgres as a queue storage.
Inspired by https://layerci.com/blog/postgres-is-the-answer/

## Usage

	(def ds (jdbc/get-datasource {:dbtype "postgresql" :dbname "cljlx"}))

	;; create a queue, but don't start yet
	(def queue (-> (q/new->PGQueue ds "jobs_status_channel") (protocol/start))
	
	(def subscriber-one (protocol/subsribe queue #(println "Subscriber #1" (java.util.Date.) %)))))
	(def subscriber-two (protocol/subsribe queue #(println "Subscriber #2" (java.util.Date.) %)))))
    
    ;; start
	(protocol/push queue "payload-here"))

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
