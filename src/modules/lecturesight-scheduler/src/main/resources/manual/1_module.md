# Module Overview

The scheduler bundle provides a service that loads a schedule from an iCalendar
(RFC-2445) file and starts/stops object tracking and camera control
accordingly. Changes to the file are detected and the internal schedule
is updated automatically. When the file is deleted, all events are
removed.

Depending on the video analysis implementation, the tracking components
may need a certain time to adapt to the scene before producing correct
tracking results. To prevent false camera movement caused by false
positives, the service can be configured to start camera control some
time after the object tracking has been activated.

Usually automatic lecture recordings are started with some lead time and
then cut afterwards. It is suggested to set the lead time for the tracker
scheduler to some value smaller than this lead time.

