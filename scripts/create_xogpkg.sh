#!/bin/sh

# 2019-06-14: Creates a zipfile of xop distribution

destfilename=$1
dt=`date +%Y%m%d-%H%M%S`

#mkdir -p ${destfilename}.${dt}
destdir=zips
cp -ar dist/ ${destdir}/${destfilename}.${dt}
cd zips
tar zcf ${destfilename}.${dt}.tgz ${destfilename}.${dt}
echo "created ${destfilename}.${dt}.tgz ${destfilename}.${dt}"
zip -rq ${destfilename}.${dt}.zip ${destfilename}.${dt}
echo "removing  ${destfilename}.${dt}"
rm -rf ${destfilename}.${dt}
cd ..

