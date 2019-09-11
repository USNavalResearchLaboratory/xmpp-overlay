#!/bin/bash
XOP_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java -cp ${XOP_PATH}/xop-all.jar mil.navy.nrl.xop.util.ListNetsKt
