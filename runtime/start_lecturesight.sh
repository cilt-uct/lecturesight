#! /bin/bash

# set correct base dir for production and daemon operation
BASE_DIR="/opt/ls"
cd $BASE_DIR

# OpenCL device type to be used. Default is GPU.
# Available options: CPU, GPU, ACCELERATOR, DEFAULT, ALL
OPENCL_DEVICE="GPU"

# assemble options
FELIX_CACHE="$BASE_DIR/felix-cache"
LOG_OPTS="-Dtinylog.configuration=$BASE_DIR/conf/log.properties"
OPENCL_OPTS="-Docl.device.type=$OPENCL_DEVICE -Docl.profiling=no"
CONFIG_OPTS="-Dfelix.config.properties=file:$BASE_DIR/conf/config.properties -Dfelix.fileinstall.dir=$BASE_DIR/conf/fileinstall/ -Dgosh.args=--noi"

# erase felix cache
rm -rf $FELIX_CACHE/*

# Check camera config and remove bundles for other camera types
VISCA=`grep -c ^com.wulff.lecturesight.visca.port.device $BASE_DIR/conf/lecturesight.properties`
VAPIX=`grep -c ^cv.lecturesight.vapix.camera.host $BASE_DIR/conf/lecturesight.properties`

if [ "$VISCA" == "1" ]; then
        echo VISCA camera
        rm -f $BASE_DIR/bundles/application/lecturesight-vapix-camera*jar
        rm -f $BASE_DIR/bundles/application/ptzcontrol-dummy-*.jar
fi

if [ "$VAPIX" == "1" ]; then
        echo VAPIX camera
        rm -f $BASE_DIR/bundles/application/visca*jar
        rm -f $BASE_DIR/bundles/application/ptzcontrol-dummy-*.jar
fi

if [ "$VAPIX$VISCA" == "00" ]; then
	echo No camera configured - using PTZ Dummy
        rm -f $BASE_DIR/bundles/application/visca*jar
        rm -f $BASE_DIR/bundles/application/lecturesight-vapix-camera*jar
fi


# Check serial link type and set up socat if required
RTP=`grep "^cv.lecturesight.framesource.input.mrl=rtph264://" $BASE_DIR/conf/lecturesight.properties`

# Match RPi hostname without the port
if [[ "$RTP" =~ rtph264://([a-z0-9.-]+) ]]; then
        RPI=${BASH_REMATCH[1]}
        echo RPi overview camera: $RPI
        # Serial link
        killall --quiet socat
        /usr/bin/socat pty,link=/dev/ttyUSB0,waitslave tcp:$RPI:2000 &
fi

# gstreamer debug logging
# https://gstreamer.freedesktop.org/data/doc/gstreamer/head/gstreamer/html/gst-running.html
export GST_DEBUG=3
export GST_DEBUG_FILE=/opt/ls/log/gstreamer.log
export GST_DEBUG_NO_COLOR=1

# start LectureSight
java -Dlecturesight.basedir=$BASE_DIR $CONFIG_OPTS $LOG_OPTS $OPENCL_OPTS -jar $BASE_DIR/bin/felix.jar -b $BASE_DIR/bundles/system $FELIX_CACHE

