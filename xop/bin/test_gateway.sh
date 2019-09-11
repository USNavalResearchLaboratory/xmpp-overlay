#!/usr/bin/env bash

LOGGING='-Djava.util.logging.config.file=config/gateway-logging.properties'
LIBRARY_PATH='-Djava.library.path=./jniLibs/x86_64'
CLASS=edu.drexel.xop.gateway.ServerDialbackSession

# eg. ./test_gateway.sh eth1 5269 openfire xo.nrl.navy.mil 3

java -cp xop-all.jar ${LIBRARY_PATH} ${LOGGING} ${CLASS} $@
