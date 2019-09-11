#!/bin/bash

# Rob Lass <urlass@cs.drexel.edu>
# Date:  02/07/2013
#
# Usage: run.sh [parameters to pass to XO]
#
# Start both the transport engine and XOP.  Override the default settings with values from
# command line, if any.

XOP_PATH=`pwd`
TRANSPORT_ENGINE_PATH=./transport-engine

if [ -z $TRANSPORT_ENGINE_PATH ]
then
    echo "ERROR: TRANSPORT_ENGINE_PATH environment variable not specified set.  Please
check out the repository from:
    
    https://pf.itd.nrl.navy.mil/svnroot/groupwise/transport-engine

and set TRANSPORT_ENGINE_PATH to its trunk in this run.sh script"
    exit 1
elif [ ! -d $TRANSPORT_ENGINE_PATH ]
then
    echo "ERROR: Specified path to Transport Engine $TRANSPORT_ENGINE_PATH does not exist."
    exit 1
elif [ ! -d $TRANSPORT_ENGINE_PATH/dist ]
then
    echo "ERROR: Transport Engine not built in $TRANSPORT_ENGINE_PATH/dist."
    exit 1
fi

cd $TRANSPORT_ENGINE_PATH
./te-daemon.sh start --config $XOP_PATH/config/transport.conf 
cd -

sleep 3

echo "*** Starting XOP"
# Need these for NORM
SYSTEM_PROPERTIES="-Djava.library.path=$XOP_PATH/lib/ -Dxop.path=$XOP_PATH"
export LD_LIBRARY_PATH="$XOP_PATH/lib/"

# Add all the files to our classpath
export CLASSPATH=$XOP_PATH/xop.jar:.
for jar in `ls $XOP_PATH/lib/*.jar`; do
    export CLASSPATH=$CLASSPATH:$jar
done

#JMX_PROPS="-Dcom.sun.management.jmxremote.port=5300 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
java $SYSTEM_PROPERTIES $JMX_PROPS $@ -jar $XOP_PATH/xop.jar 

echo "*** XOP stopped."
