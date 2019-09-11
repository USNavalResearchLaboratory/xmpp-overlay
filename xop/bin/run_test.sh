#!/usr/bin/env bash

if [ "$#" -ne "4" ]; then
    echo "usage: $0 <jid> <nick> <room> <iface>"
    exit 1
fi

jid=$1
nick=$2
room=$3
iface=$4
echo "> jid   $jid"
echo "> nick  $nick"
echo "> room  $room"
echo "> iface $iface"

ROOT=/home/duc/workspace/test_env/logs
dt=`date +%Y%m%d.%H%M`
outdir=${ROOT}/${nick}/${dt}
pcapfile=${outdir}/tcpdump.pcap

# setup
echo "Setting up the output files"
mkdir -p ${outdir}

#
## Start TCPdump
#echo "Start TCPdump"
#tcpdump -i ${iface} -w ${pcapfile} &
#DUMP_PID=$!

# start XO
echo "start XO"
./start_xop.sh &> ${outdir}/xopout.${dt}.log &
XOPSH_PID=$!
XOP_PID=$(pidof java)

echo "java pid: $XOP_PID, XOPSH_PID $XOPSH_PID"

sleep_interval=3
echo "sleep for ${sleep_interval}s"
sleep ${sleep_interval}

# Start traffic generator
echo "Start traffic generator"
num_msg=15
msg_interval=3
echo "Sending ${num_msg} at ${msg_interval} second intervals"
cd ../../xmpp_tester
./.venv3/bin/python xmpp_gen.py -s localhost -j ${jid} -n ${nick} -r ${room} \
                                 -m ${num_msg}= -i ${msg_interval} -d ${outdir}
cd -

# wait for traffic generator to end
echo "sleep for another $sleep_interval s"
sleep ${sleep_interval}


# stop XO
echo "stopping XO with pid $XOP_PID"
kill -9 $(pidof java)

echo "sleep for another $sleep_interval s"
sleep ${sleep_interval}
#
## stop TCP dump
#echo "stopping tcpdump with pid $DUMP_PID"
#kill -INT ${DUMP_PID}

