#!/usr/bin/env bash

LOGGING='-Djava.util.logging.config.file=config/test-logging.properties'
LIBRARY_PATH='-Djava.library.path=./jniLibs/x86_64' 
CLASS=mil.navy.nrl.xop.client.ClientConnectionKt

java -cp xop-all.jar ${LIBRARY_PATH} ${LOGGING} ${CLASS} $@

