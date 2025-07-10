#/bin/bash

adb push opencv/lib* /usr/lib/
adb push weston_setup.sh /data/vendor/hrnet/

adb push res/models/* /data/vendor/hrnet/
adb shell mkdir -p /data/vendor/hrnet/media/
adb push res/media/* /data/vendor/hrnet/media/
