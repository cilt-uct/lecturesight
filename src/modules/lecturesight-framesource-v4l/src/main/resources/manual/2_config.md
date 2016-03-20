# Configuration Properties

**cv.lecturesight.framesource.v4l.resolution.width**
*Default:* 320

Default width for input frames.

**cv.lecturesight.framesource.v4l.resolution.height**
*Default:* 240

Default height for input frames.

**cv.lecturesight.framesource.v4l.standard**
*Default:* 0

Default video standard. Usually not used with USB webcams but rather with capture cards. Which value indicates a certain standard (eg. PAL-X/NTSC) depends on the driver of the video device.

**cv.lecturesight.framesource.v4l.channel**
*Default:* 0

Default video input. Usually not used with USB webcams but rather with capture cards. This can be usefull with capture cards, since they are by default set to tuner input and need to be set to composite (usually 1).

**cv.lecturesight.framesource.v4l.quality**
*Default:* 0

Default encoding quality. Only used for devices that provide encoded video streams (such as MPEG2 or MJPEG). Value range depends on device driver.

Note: For real-time operation, only devices that provide raw video streams should be used since encoding and decoding of frames can lead to several hundred milliseconds of delay.

