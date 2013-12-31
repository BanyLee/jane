#!/bin/sh

cd `dirname $0`

JVM="-Xms512m -Xmx512m -server -XX:+UseConcMarkSweepGC -Xloggc:log/gc.log -XX:+PrintGCTimeStamps"
LIB="lib/slf4j-api-1.7.5.jar:lib/logback-core-1.0.13.jar:lib/logback-classic-1.0.13.jar:lib/mina-core-2.0.7.jar:lib/luaj-jse-2.0.3.jar"

if [ "$1" == "" ] ; then set $1 jane.test.TestMain; fi
if [ "$1" == "b" ] ; then set $1 jane.test.TestDBBenchmark; fi

mkdir -p log 2> /dev/null
mkdir -p db 2> /dev/null

java $JVM -cp $LIB:jane-core.jar:jane-test.jar:. $@
