#!/bin/sh

SCRIPTDIR=`pwd`
APK_OUTPUT_DIR=${SCRIPTDIR}/xop-android/build/outputs/apk
APK_NAME=xop-android-debug.apk

BASE_DEST_DIR=/media/sf_workspace/ToFromVirtualBox
DEST_DIR=$1

dt=`date +%Y%m%d-%H%M%S`
NAME=xop.${dt}.apk

echo "Copying ${APK_OUTPUT_DIR}/${APK_NAME} to ${BASE_DEST_DIR}/${DEST_DIR}/${NAME}"
cp ${APK_OUTPUT_DIR}/${APK_NAME} ${BASE_DEST_DIR}/${DEST_DIR}/${NAME}
echo "Done!"
