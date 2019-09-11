#!/bin/sh
#
# Usage: run.sh [configuration-shell-script]
#

SYSTEM_PROPERTIES=""

usage() {
   echo "Usage: $1 "
}


# DNGUYEN 20110420 - HACK: ADDING xop/dist/lib dir to system library path to get norm to work (TODO: DO THIS THE "XOP WAY"
SYSTEM_PROPERTIES="-Djava.library.path=/coreapps/xop/lib"
echo $COMMANDLINE_PROPERTIES
cd `dirname $0` # change to the directory of this script so we have xop.jar and deps in the path
java $SYSTEM_PROPERTIES $COMMANDLINE_PROPERTIES -cp xop.jar edu.drexel.xop.net.test.XOPAdvertiser
