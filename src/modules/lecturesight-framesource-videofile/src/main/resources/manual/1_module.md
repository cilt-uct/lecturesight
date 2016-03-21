# Module Overview

The framesource-videofile bundle provides a FrameSource implementation that reads frames from
a video file. It is based on GStreamer. It depends on the set of codecs
installed in the host operating systems what formats are supported.
Standard MPEG file formats should always be supported since they are
included in most standard installations.

## Usage

The `type` for this FrameSource implementation is **file**. The `path`
is the path to a video file. There are no arguments for this FrameSource
implementation.

Example usage:

`file://testvideos/sample.mpg`

The example MRL tells the system to create a FrameSource from the file
`testvideos/sample.mpg`.

## Requirements

The implementation is based on GStreamer4Java. The bundle does not
provide the native libraries; they have to be installed in the host
operating system.

To install the required libraries under Ubuntu Version 10.04+ the
following apt command can be used:

`sudo apt-get install libgstreamer0.10-0 gstreamer0.10-ffmpeg gstreamer0.10-alsa gstreamer0.10-plugins-bad gstreamer0.10-plugins-bad-multiverse gstreamer0.10-plugins-base gstreamer0.10-plugins-base-apps gstreamer0.10-plugins-good gstreamer0.10-plugins-ugly`
