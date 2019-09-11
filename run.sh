#!/bin/bash

PARAMS=""

function ctrl_c {
    echo killing GCSD with id $1
    kill $1
}

USE_GCS=0
while getopts "g:" opt; do
    case $opt in
        g)
            GCSID=$OPTARG
            echo "Running with GCS GCSID=$GCSID"
            USE_GCS=1
            gcsd -i ${GCSID} -f gcsd-config.json > gcs_${GCSID}.log 2>&1 &
            gcsd_pid=$!
            echo "Running GCS has PID: $gcsd_pid"
            trap "ctrl_c ${gcsd_pid}" INT
            shift $((OPTIND -1))
            PARAMS=$*
            break
            ;;
        \? )
            echo "Not starting GCSD.  ${opt}"
            PARAMS=$*
            break
    esac
done


XOP_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
#XOP_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"/dist
XOP_DIST_PATH=$XOP_PATH/dist

sleep 1
echo "*** Starting XOP"
# Need these for NORM
SYSTEM_PROPERTIES="-Djava.library.path=$XOP_DIST_PATH/libs/ -Dxop.path=$XOP_DIST_PATH"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$XOP_DIST_PATH/libs/

# Add all the files to our classpath
export CLASSPATH=$CLASSPATH:$XOP_DIST_PATH/xop.jar:.
for jar in `ls $XOP_DIST_PATH/libs/*.jar`; do
    export CLASSPATH=$CLASSPATH:$jar
done

cd $XOP_DIST_PATH
#java $SYSTEM_PROPERTIES -Dxop.enable.gateway=$1 -Dxop.gateway.bindinterfaces=$2 -Dxop.bind.interface=$3  -Dxop.gateway.servers=c4ad038.ciav.cmil.mil -jar xop-all.jar
echo "java $SYSTEM_PROPERTIES $PARAMS $JMX_PROPS -jar xop-all.jar "
java $SYSTEM_PROPERTIES $PARAMS $JMX_PROPS -jar xop-all.jar
#java $SYSTEM_PROPERTIES $PARAMS $JMX_PROPS -javaagent:xop.jar $OTHER_PROPS -jar xop.jar
echo "*** XOP stopped."
#echo "*** RUN ./kill_te.sh TO SHUTDOWN TRANSPORT ENGINE ***"
