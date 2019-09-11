#!/bin/bash

for pid in `ps -aux | grep java | grep TransportEngine | awk '{print $2}'`; do
    kill -9 $pid; 
done
