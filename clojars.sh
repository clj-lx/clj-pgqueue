#!/bin/bash
set -e

Echo "Checking environment variables CLOJARS_USERNAME and CLOJARS_PASSWORD."

if [ -z $CLOJARS_USERNAME ]
then
    echo "CLOJARS_USERNAME env variable does not exists ! Exiting."
    exit 1;
fi

if [ -z $CLOJARS_PASSWORD ]
then
    echo "CLOJARS_PASSWORD env variable does not exists ! Exiting."
    exit 1;
fi

echo "Cleaning previous jar"
rm clj_pgqueue.jar

echo "Generating pom"
clj -Spom

echo "Updating pom with git tag"
clj -A:bump -p

echo "Generating JAR clj_pgqueue.jar"
clj -A:pack  mach.pack.alpha.skinny --no-libs --project-path clj_pgqueue.jar

echo "Deploying into Clojars"

clojure -A:deploy

echo "Done"
