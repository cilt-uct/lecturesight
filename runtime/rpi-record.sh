#! /bin/bash

## This records the RPi overview camera (plays back at 1fps)

# set correct base dir for production and daemon operation
BASE_DIR="/opt/ls"
cd $BASE_DIR

# Check serial link type and set up socat if required
RTP=`grep "cv.lecturesight.framesource.input.mrl=rtph264://" $BASE_DIR/conf/lecturesight.properties`

# Match RPi hostname without the port
if [[ "$RTP" =~ rtph264://([a-z0-9.-]+) ]]; then
        RPI=${BASH_REMATCH[1]}
        echo RPi overview camera: $RPI
	gst-launch-1.0 -e tcpclientsrc host=$RPI port=8554 ! gdpdepay ! rtph264depay ! avdec_h264 ! videoconvert ! avimux ! filesink location=$BASE_DIR/overview.avi
fi

