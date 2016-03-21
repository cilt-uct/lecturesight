# Module Overview

The VISCA bundle provides a driver for cameras speaking the VISCA protocol defined by Sony.

On activation, the service tries to initialize all VISCA cameras on a configured serial device.
Upon discovery of a VISCA camera, the driver determines camera vendor and model and tries to load a fitting device profile.
If no fitting profile exists, the driver loads a default profile that has the same configuration as the profile for the Sony EVI-D30.
Most VISCA cameras can be configured to run in D30 compatibility mode.

