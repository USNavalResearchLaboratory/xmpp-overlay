#!/bin/bash

# Run this script from /etc/rc.local e.g.
# e.g. /home/duc32/xop_0.9.4_20180613/xop.rc.sh -Dxop.enable.gateway=true -Dxop.transport.nodeid=5 &
# note the nodeid must be unique per machine and need the '&' at the end of the line

export LD_LIBRARY_PATH=/usr/local/lib
# update the below directory to the path to this dist dir
XOP_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $XOP_PATH
dt=`date +%Y%m%d.%H%M%S`
./start_xop.sh $@ > xop_${dt}.log 2>&1

