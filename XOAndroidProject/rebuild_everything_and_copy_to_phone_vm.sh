#!/bin/bash
# i got tired of doing this manually
cd ..
ant clean
ant deploy-to-android
cd -
ant clean
ant debug
adb install -r bin/XOAndroid-debug.apk
