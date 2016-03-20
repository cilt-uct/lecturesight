# Configuration Parameters

**cv.lecturesight.scheduler.schedule.file**
*Default:* schedule.ics

The name of the iCal file holding the schedule. The full path of the iCal file must also be specified in `conf/fileinstall/org.apache.felix.fileinstall-scheduler.cfg` so that LectureSight will update the schedule when the iCal file changes.

**cv.lecturesight.scheduler.agent.name**

A capture agent name the service will look for in case the iCal holds schedules for more than one capture agent. If this property value is empty, the service will take every event from the iCal into account.

**cv.lecturesight.scheduler.timezone.offset**
*Default*: 1

The time zone offset to add to the event times from the schedule.

**cv.lecturesight.scheduler.tracking.leadtime**
*Default:* 10

The time (in seconds) the service will wait after the object tracking has been activated before the camera control is activated.

Tracking algorithms may need a certain amount of time to adapt to the scene.
In this period false positives could lead to unwanted camera movements, so starting the camera control 
some time after the object tracking might be useful.

