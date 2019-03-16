#! /bin/bash

UPTIME=`cat /proc/uptime | awk '{print int($1)}'`

if [ "$UPTIME" -lt "90" ]; then
	sleep 15
fi

wget -q --spider http://media.uct.ac.za
if [ "$?" != 0 ]; then
        echo Waiting for network to come up
        sleep 300
fi

UPTIME=`cat /proc/uptime | awk '{print int($1)}'`

# set correct base dir for production and daemon operation
BASE_DIR="/opt/ls"
cd $BASE_DIR

# OpenCL device type to be used. Default is GPU.
# Available options: CPU, GPU, ACCELERATOR, DEFAULT, ALL
OPENCL_DEVICE="GPU"

# Debug options. 
DEBUG_PORT="8001"
DEBUG_SUSPEND="y"

# assemble options
FELIX_CACHE="$BASE_DIR/felix-cache"
LOG_OPTS="-Dtinylog.configuration=$BASE_DIR/conf/log.properties"
OPENCL_OPTS="-Docl.device.type=$OPENCL_DEVICE -Docl.profiling=no"
CONFIG_OPTS="-Dfelix.config.properties=file:$BASE_DIR/conf/config.properties -Dfelix.fileinstall.dir=$BASE_DIR/conf/fileinstall/ -Dgosh.args=--noi"

if [ "$#" = "1" ] && [ "$1" = "debug" ] ; then
  DEBUG_OPTS="-Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,address=$DEBUG_PORT,server=y,suspend=$DEBUG_SUSPEND"
else
  DEBUG_OPTS=""
fi

# erase felix cache
rm -rf $FELIX_CACHE/*

# Check camera config and remove bundles for other camera types
VISCA=`grep -c ^com.wulff.lecturesight.visca.port.device $BASE_DIR/conf/lecturesight.properties`
VAPIX=`grep -c ^cv.lecturesight.vapix.camera.host $BASE_DIR/conf/lecturesight.properties`

if [ "$VISCA" == "1" ]; then
        echo VISCA camera
        rm -f $BASE_DIR/bundles/application/lecturesight-vapix-camera*jar
        rm -f $BASE_DIR/bundles/application/lecturesight-ptzcontrol-dummy-*.jar
fi

if [ "$VAPIX" == "1" ]; then
        echo VAPIX camera
        rm -f $BASE_DIR/bundles/application/visca*jar
        rm -f $BASE_DIR/bundles/application/lecturesight-ptzcontrol-dummy-*.jar
fi

if [ "$VAPIX$VISCA" == "00" ]; then
	echo No camera configured - using PTZ Dummy
        rm -f $BASE_DIR/bundles/application/visca*jar
        rm -f $BASE_DIR/bundles/application/lecturesight-vapix-camera*jar
fi

# gstreamer debug logging
# https://gstreamer.freedesktop.org/data/doc/gstreamer/head/gstreamer/html/gst-running.html
export GST_DEBUG=3
export GST_DEBUG_FILE=$BASE_DIR/log/gstreamer.log
export GST_DEBUG_NO_COLOR=1
export GST_DEBUG_DUMP_DOT_DIR=$BASE_DIR/log

# Clear logs and metrics
rm -f $BASE_DIR/log/ls.log
rm -f $BASE_DIR/log/gstreamer.log
rm -f $BASE_DIR/metrics/*csv
rm -f $BASE_DIR/metrics/*json

# Log OpenCL errors
export CL_LOG_ERRORS=stdout

# start LectureSight
ERROR_LOG=/opt/ls/log/ls-run.log
LSOUT_LOG=/opt/ls/log/ls-stdout.log

while true
do
        /usr/bin/logger "LectureSight start (uptime $UPTIME)"
        TIMESTAMP=`date +"%Y-%m-%d %H:%M:%S"`
        echo "$TIMESTAMP ###### LectureSight start (uptime $UPTIME)" >> $ERROR_LOG
        echo "$TIMESTAMP ###### LectureSight start (uptime $UPTIME)" >> $LSOUT_LOG
        echo "$TIMESTAMP ###### LectureSight start"

        java -Dlecturesight.basedir=$BASE_DIR $CONFIG_OPTS $LOG_OPTS $OPENCL_OPTS $DEBUG_OPTS -jar $BASE_DIR/bin/felix.jar -b $BASE_DIR/bundles/system $FELIX_CACHE >> $LSOUT_LOG 2>&1

        if [ $? != 0 ]; then
                echo "Abnormal exit: restarting LectureSight in 30s"
                /usr/bin/logger "LectureSight abnormal exit"
                TIMESTAMP=`date +"%Y-%m-%d %H:%M:%S"`
                echo "$TIMESTAMP ###### LectureSight abnormal exit" >> $ERROR_LOG
                sleep 30
        else
                echo "Normal exit"
                /usr/bin/logger "LectureSight normal exit"
                TIMESTAMP=`date +"%Y-%m-%d %H:%M:%S"`
                echo "$TIMESTAMP ###### LectureSight normal exit" >> $ERROR_LOG

		LATESTLOG=`ls -t $BASE_DIR/log/ls.*.log | head -1`
		ERRORS=`grep ERROR $LATESTLOG | grep -c Heartbeat`
		if [ $ERRORS == "0" ]; then
			break
		else
			echo "$TIMESTAMP Errors reported in log $LATESTLOG: restarting"  >> $ERROR_LOG
			sleep 30
		fi
        fi
done
