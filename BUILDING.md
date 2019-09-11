XOP Building
Duc Nguyen (dn53@drexel.edu)
====

# Background

Building XOP has migrated from using Apache Ant to using Gradle to better
support building distributions for multiple platforms, Linux and Android.

For more information on Gradle, go to http://www.gradle.org

# Building and Running XOP for Linux

To build and run:

    $ ./gradlew deployXOP
    $ cd dist
    $ ./run.sh

# Building and Running XOP for Android

To build and install on a device:

    $ ./gradlew installDebug


