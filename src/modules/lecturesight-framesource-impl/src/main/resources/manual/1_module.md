# Module Overview

The FrameSource bundle provides the infrastructure responsible for
managing FrameSource implementations. It discovers video input plugins
and is responsible for setting up *FrameSources* with the propper input
plugin. It also registers the *FrameSourceProvider* service on startup,
which is the point in the system where other services get the (masked)
input image from the overview camera.

