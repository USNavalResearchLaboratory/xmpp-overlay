#!/usr/bin/env bash

echo "Starting tcpdump"
ts=`date +%Y%m%d.%H%M%S`
node=`hostname`
logdir=/tmp
LOGFILE="${logdir}/${node}.${ts}.pcap"
PARAMS=
tcpdump ${PARAMS} -w ${LOGFILE} &
DUMP_PID=$!
function ctrl_c (){
    pid=$DUMP_PID
    echo "exiting XOP, stopping TCPDUMP with PID $pid"
    kill ${pid}
    echo "killed tcpdump with pid $pid"
}
trap ctrl_c SIGHUP SIGINT SIGTERM

echo "tcpdump writing to $LOGFILE. wait 1s."
sleep 1

export ${DUMP_PID}