#! /bin/sh
export http_proxy=http://media.uct.ac.za:3128
export https_proxy=http://media.uct.ac.za:3128

add-apt-repository ppa:gstreamer-developers/ppa 
apt-get update 
apt-get install gstreamer1.0*

