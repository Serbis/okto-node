#!/bin/bash

java_home='/usr/lib/jvm/jdk1.8.0_181'

g++ -shared -O3 \
        -fPIC \
        -I/usr/include \
        -I${java_home}/include \
        -I${java_home}/include/linux \
        $1 -o $2
