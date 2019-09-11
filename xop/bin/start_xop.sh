#!/bin/bash

# Usage: run.sh [XOP Options]
#
# Start XOP. Assumes Transport Engine is running

XOP_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

#echo "*** STARTING TRANSPORT ENGINE ***"
#./start_te.sh xog
#echo "*** TRANSPORT ENGINE STARTED. RUN ./kill_te.sh TO SHUTDOWN ***"

#./start_tcpdump.sh

echo "*** Starting XOP"
SYSTEM_PROPERTIES="-Dxop.path=$XOP_PATH"

## Add all the files in the libs* directories to our classpath
#LIB_DIRS=( "" "-protosd" "-desktop" "-transportengine"  )
#for LIB_DIR in ${LIB_DIRS}; do
#    for jar in `ls ${XOP_PATH}/libs${LIB_DIR}/*.jar`; do
#        CLASSPATH=${CLASSPATH}:${jar}
#    done
#done

OTHER_PROPS=$@
ARCH=`uname -m`
if [[ "$ARCH" == "i686" ]]; then
    echo "Running on $ARCH (32bit) linux system"
    ARCH="x86"
fi

JNIDIR="${XOP_PATH}/jniLibs/$ARCH"
SYS_PROPS="-Djava.library.path=$JNIDIR"
export LD_LIBRARY_PATH=.:${JNIDIR}

cd ${XOP_PATH}
echo "Using JNIDIR $JNIDIR"
java -cp xop-all.jar ${SYS_PROPS} ${SYSTEM_PROPERTIES} ${OTHER_PROPS} edu.drexel.xop.Run
#java -cp xop-all.jar $SYSTEM_PROPERTIES $OTHER_PROPS edu.drexel.xop.Run
echo "*** XOP stopped."

