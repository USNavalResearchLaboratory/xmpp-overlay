#!/bin/bash

ROOT_DIR=`pwd`/`dirname $0`

java -cp $ROOT_DIR/../dist/xop.jar edu.drexel.xop.properties.XopDefaultProperties
