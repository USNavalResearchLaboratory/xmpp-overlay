#!/bin/bash

# Rob Lass <urlass@cs.drexel.edu>
# Date:  02/07/2013
#
# Usage: run.sh [parameters to pass to XO]
#
# Start both the transport engine and XOP.  Override the default settings with values from
# command line, if any.

XOP_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"/dist

# Add all the files to our classpath
export CLASSPATH=$XOP_PATH/xop.jar:.
for jar in `ls $XOP_PATH/lib/*.jar`; do
    export CLASSPATH=$CLASSPATH:$jar
done

export CLASSPATH=$CLASSPATH:components/muc/dist/muc.jar

#JMX_PROPS="-Dcom.sun.management.jmxremote.port=5300 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
java $SYSTEM_PROPERTIES $JMX_PROPS $@ 

echo "*** XOP stopped."
