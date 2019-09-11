#!/bin/bash

PID_PATH=/tmp/te-pid
LOG_PATH=/tmp

ARGS=`getopt -o "i:,c:,help" -l "id:,config:,help" -- "$@"`
ID=

function te_get_pid {
    local PID=0
    if [ -f $PID_PATH ]
    then
        # Get PID of the specified ID ($1)
        PID=`cat $PID_PATH | grep "^$1 " | awk '{ print $2 }'`
        if [ ! -z $PID ]
        then
            # Check if that PID is running
            kill -0 $PID > /dev/null 2>&1

            if [ $? -ne 0 ]
            then
                PID=0
            fi
        else
            PID=0
        fi
    fi
    echo $PID
}

function te_put_pid {
    local ID_TO_ADD=$1
    local PID_OF_ID=$2
    touch $PID_PATH

    local NEW_PIDS=`sed -e "/^${ID_TO_ADD} /d" $PID_PATH`
    echo $NEW_PIDS > $PID_PATH
    if [ ! -z $PID_OF_ID ]
    then
        echo $ID_TO_ADD $PID_OF_ID >> $PID_PATH
    fi
}

function te_start {
    if [ -z $ID ]
    then
        ID=$RANDOM
        while [ `te_get_pid $ID` -ne 0 ]
        do
            ID=$RANDOM
        done
    fi

    if [ `te_get_pid $ID` -eq 0 ]
    then
        echo "*** Starting Transport Engine with ID $ID"
        java -Djava.library.path=./lib -cp lib/*:dist/TransportEngine.jar edu.drexel.transportengine.core.Run --id $ID $CONFIG &> $LOG_PATH/te_${ID}.log &

        PID=$!
        te_put_pid $ID $PID
    else 
        echo "*** Transport Engine already running (pid=`te_get_pid $ID`)"
    fi
}

function te_stop {
    if [ `te_get_pid $1` -eq 0 ]
    then
        echo "*** Transport Engine with ID $1 does not appear to be running"
    else
        local PID=`te_get_pid $1`
        kill $PID
        te_put_pid $1
        echo "*** Transport Engine stopped and cleaned up"
    fi
}

function te_status {
    echo "*** List of Transport Engine Instances ***"
    if [ -f $PID_PATH ]
    then
        for TE_ID in `cat /tmp/te-pid | awk '{ print $1 }'`
        do
            TE_PID=`te_get_pid $TE_ID`
            if [ $TE_PID -ne 0 ]
            then
                echo "*** ID $TE_ID is running with PID $TE_PID"
            fi
        done
    fi
}

function usage {
    echo "usage: $0 <command>"
    echo "    <command> values:"
    echo "        start [--id id] [--config config]"
    echo "        stop <id>"
    echo "        status"
    echo "        generate-config <path>"
    exit 1
}

if [ ! -d dist ]
then
    echo "No 'dist' directory found.  Run 'ant' to build the Transport Engine."
    exit 1
fi

eval set -- "$ARGS"

while true;
do
    case "$1" in
        -c|--config)
            CONFIG="--config $2"
            shift 2;;
        -i|--id)
            ID=$2
            shift 2;;
        -h|--help)
            java -cp lib/*:dist/TransportEngine.jar edu.drexel.transportengine.core.Run --help
            exit 0
            break;;
        --)
            shift
            break;;
    esac
done

case $1 in
    start)
        te_start
        ;;
    stop)
        if [ -z $2 ]
        then
            echo "usage: $0 stop <id>"
        else
            te_stop $2
        fi
        ;;
    status)
        te_status
        ;;
    generate-config)
        if [ $# -lt 2 ]
        then
            echo "usage: $0 generate-config <path>"
        else
            java -cp lib/*:dist/TransportEngine.jar edu.drexel.transportengine.core.Run --generate-config-at $2
            echo "Configuration generated"
            echo "To use this config, run"
            echo "    $0 start --config $2"
        fi
        ;;
    *)
        usage
        ;;
esac
