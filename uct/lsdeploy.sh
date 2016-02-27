#! /bin/sh

## Copy across latest build to camonitor for CA updates
echo Copying build to staging server
scp build/lsuct-latest.tgz ca@camonitor.uct.ac.za:/home/ca/files/

