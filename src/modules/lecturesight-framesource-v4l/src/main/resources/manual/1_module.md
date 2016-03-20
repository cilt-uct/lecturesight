# Module Overview

The framesource-v4l bundle provides a FrameSource implementation for accquiring frames
from Video4Linux and Video4Linux 2 devices. Arguments for creation of a
new FrameSource from this implementation can be provided in the
FrameSource MRL. If an argument is not present in the MRL, the default
value is take from the configuration properties.

## Usage

The `type` for this FrameSource implementation is **v4l**. The `path` is
the path to a linux video device (`/dev/videoX`). Available arguments
are `width, height, standard, channel, quality`; their meanings are the
same as those of the properties in the next section.

Example usage:

`v4l:///dev/video0[width=320;height=240]`

The example MRL tells the system to create a FrameSource using a
Video4Linux device `/dev/video0` as input with QVGA resolution.

Example usage:

`v4l2:///dev/video0[width=320;height=240]`

The example MRL tells the system to create a FrameSource using a
Video4Linux2 device `/dev/video0` as input with QVGA resolution.

