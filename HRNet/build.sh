#!/bin/bash

SDK_PATH="/opt/ea-sdk/4.0.14/environment-setup-cortexa76-poky-linux"
source ${SDK_PATH}

rm -rf build/
mkdir build
cd build
cmake .. 
make
cd ..

echo "------------------------------------------"
echo "push the executable to /data/vendor/hrnet/"
adb shell mkdir -p /data/vendor/hrnet/
adb push build/bin/* /data/vendor/hrnet/

echo "DONE!"
