#! /bin/sh

echo -n "Stopping Lecturesight: "

java -cp /opt/ls/bin ShutdownOSGI localhost 2501 10

LS_PID=`ps aux | grep java | awk '/lecturesight/ && !/awk/ {print $2}'`
if [ -z "$LS_PID" ]; then
  echo "Lecturesight already stopped - nothing to do"
  exit 0
fi

sleep 10

LS_PID=`ps aux | grep java | awk '/lecturesight/ && !/awk/ {print $2}'`
if [ ! -z $LS_PID ]; then
  echo "Hard killing since felix ($LS_PID) seems unresponsive to regular kill"
  kill -9 $LS_PID
fi

echo "done."

