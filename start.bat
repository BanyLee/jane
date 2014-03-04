@echo off
setlocal
pushd %~dp0

set JVM=-Xms512m -Xmx512m -server -XX:+UseConcMarkSweepGC -Xloggc:log/gc.log -XX:+PrintGCTimeStamps
set LIB=lib/slf4j-api-1.7.6.jar;lib/log4j-core-2.0-rc1.jar;lib/log4j-api-2.0-rc1.jar;lib/log4j-slf4j-impl-2.0-rc1.jar;lib/mina-core-2.0.7.jar;lib/luaj-jse-2.0.3.jar

set MAIN=%1
if "%MAIN%" equ ""  set MAIN=jane.test.TestMain
if "%MAIN%" equ "b" set MAIN=jane.test.TestDBBenchmark

md log 2>nul
md db  2>nul

java %JVM% -cp %LIB%;jane-core.jar;jane-test.jar;. %MAIN% %2 %3 %4 %5 %6 %7 %8 %9
