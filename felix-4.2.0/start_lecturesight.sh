#! /bin/bash

# Start Lecturesight

LSDIR=/opt/ls

cd $LSDIR

DEBUG_PORT="8001"
DEBUG_SUSPEND="n"
DEBUG_OPTS="-Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,address=$DEBUG_PORT,server=y,suspend=$DEBUG_SUSPEND"

FELIX_CACHE="./felix-cache"
LOG_OPTS="-Dtinylog.configuration=conf/log.properties"
export CL_LOG_ERRORS="stderr"

# Serial link (Raspberry Pi cameras)
#killall socat
#socat pty,link=/dev/ttyUSB0,waitslave tcp:137.158.32.126:2000 &

rm -rf $FELIX_CACHE/*
rm -rf record/*

java $DEBUG_OPTS $LOG_OPTS -Dgosh.args=--nointeractive -Dlecturesight.basedir=$LSDIR -jar bin/felix.jar $FELIX_CACHE
