#!/bin/sh

destfilename=$1
dt=`date +%Y%m%d-%H%M%S`

mkdir -p ${destfilename}.${dt}
cp -a dist ${destfilename}.${dt}
cp gcsd-config.json ${destfilename}.${dt}
cp run.sh run_gcsd.sh ${destfilename}.${dt}
tar zcf ${destfilename}.${dt}.tgz ${destfilename}.${dt}
echo "created ${destfilename}.${dt}.tgz ${destfilename}.${dt}"
echo "removing  ${destfilename}.${dt}"
rm -rf ${destfilename}.${dt}
