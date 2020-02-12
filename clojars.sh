#!/bin/bash
set -e

JAR=clj_pgqueue.jar

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
if [ -f "$JAR" ]
then
    rm $JAR
fi

echo "Generating pom"
clj -Spom

echo "Updating pom with git tag"
clj -A:bump -p

echo "Generating JAR $JAR"
clj -A:pack  mach.pack.alpha.skinny --no-libs --project-path $JAR

echo "Deploying into Clojars"

clojure -A:deploy

echo "Done"
