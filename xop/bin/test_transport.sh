#!/usr/bin/env bash

addr=$2

# echo "Starting tcpdump"
# ts=`date +%Y%m%d.%H%M%S`
# node=`hostname`
# logdir=/tmp
# LOGFILE="${logdir}/${node}.${ts}.pcap"
# PARAMS=
# tcpdump ${PARAMS} -w ${LOGFILE} dst host ${addr} &
# DUMP_PID=$!
# function ctrl_c (){
#     pid=${DUMP_PID}
#     echo "exiting XOP, stopping TCPDUMP with PID $pid"
#     kill ${pid}
#     echo "killed tcpdump with pid $pid"
# }
# trap ctrl_c SIGHUP SIGINT SIGTERM
# 
# echo "tcpdump writing to $LOGFILE. wait 1s."
# sleep 1

LOGGING='-Djava.util.logging.config.file=config/test-logging.properties'
LIBRARY_PATH='-Djava.library.path=./jniLibs/x86_64' 
CLASS=mil.navy.nrl.xop.transport.reliable.XopNormServiceRunnerKt 

java -cp xop-all.jar ${LIBRARY_PATH} ${LOGGING} ${CLASS} $@

# kill ${DUMP_PID}
