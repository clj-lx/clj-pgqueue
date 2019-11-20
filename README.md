![](https://github.com/clj-lx/clj-pgqueue/workflows/Clojure%20CI/badge.svg)
# clj-pgqueue

A Clojure library designed to use Postgres as a queue storage.
Inspired by https://layerci.com/blog/postgres-is-the-answer/

## Usage

FIXME

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
