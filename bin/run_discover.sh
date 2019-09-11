#!/bin/sh
#
# Usage: run-discovery.sh [configuration-shell-script]
#
# Start XOP.  Override the default settings with values from
# ./xop-config.sh, if it exists, then from the executable
# configuration-shell-script.
#
# You can put other things, such as static routes, in xop-config.sh
# and the configuration-shell-script.
#
# It is not an error if xop-config.sh exists but is not executable.
#
# It is an error if configuration-shell-script does not exist or is
# not executable.
#

# These are the variables that define how XOP behaves.  You change
# them by defining them in ./xop-config.sh and in another shell script
# that you might name on the command line.  ./xop-config.sh will be
# ignored if it is not executable.  It is an error if the other shell
# script named on the command line does not exist or is not
# executable.
#
# The value of SYSTEM_PROPERTIES is presented java before the
# properties that are defined on the command line.  If a property is
# defined more than once, xop ignores all but the last definition of
# that property.
#
SYSTEM_PROPERTIES=""

usage() {
	echo "Usage: run.sh [-D property=value [-D property=value [...]]] [configuration-shell-script]" >&2
	echo >&2
	echo "The configuration-shell-script, if given on the command line, ">&2
	echo "must exist and must be executable.">&2
	echo >&2
	echo "Options:" >&2
	echo "-D property=value Define a property to add to Java's system properties" >&2
	echo "                  This option may appear more than once." >&2
	echo "                  Remember to prefix any XOP property with" >&2
	echo "                  \"XOP.\".  For example, to define the property" >&2
	echo "                  muc.interfaces=eth0,eth1, use these arguments">&2
	echo "                  on the command line:">&2
	echo "                  -D XOP.muc.interfaces=eth0,eth1">&2
	echo >&2
}

# This invocation of getopt works with both GNU getopt and OS X getopt.
TEMP=`getopt D: $*`

if [ $? != 0 ] ; then 
        usage
        exit 1
fi

#
# Note the quotes around `$TEMP': they are essential!
#
eval set -- "$TEMP"

while true ; do 
        case "$1" in 
                -D) COMMANDLINE_PROPERTIES="$COMMANDLINE_PROPERTIES -D$2" ; shift 2 ;;
                --) shift ; break ;;
                *) echo "Internal error!" ; exit 1 ;;
        esac
done

#
# Override the defaults by setting them in ./xop-config.sh.
#
if [ -x ./xop-config.sh ] ; then
  . ./xop-config.sh
elif [ -e ./xop-config.sh ] ; then
	echo "WARNING: ./xop-config.sh exists but is not executable."
fi

#
# Override the defaults and the values from ./xop-config.sh
# with values from a shell script named on the command line.
#
if [ ! -z "$1" ] ; then
	if [ -x $1 ] ; then
		. $1
    else
		echo "$0: Configuration script $1 either does not exist or is not executable"
		exit 1
	fi
fi
# DNGUYEN 20110420 - HACK: ADDING xop/dist/lib dir to system library path to get norm to work (TODO: DO THIS THE "XOP WAY"
SYSTEM_PROPERTIES="-Djava.library.path=/coreapps/xop/lib"
echo $COMMANDLINE_PROPERTIES
cd `dirname $0` # to change to the directory of this script so we have xop.jar etc in the path
java $SYSTEM_PROPERTIES $COMMANDLINE_PROPERTIES -cp xop.jar edu.drexel.xop.net.test.XOPDiscover
