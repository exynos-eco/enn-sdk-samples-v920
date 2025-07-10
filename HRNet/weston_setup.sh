#!/bin/bash

echo "[INFO] Starting weston..."
weston --backend=drm-backend.so --idle-time=0 --tty=1 > /tmp/weston.log 2>&1 &
WESTON_PID=$!

sleep 2

if ps -p $WESTON_PID > /dev/null; then
    echo "[INFO] weston is running (PID=$WESTON_PID)"
else
    echo "[ERROR] weston failed to start."
    exit 1
fi
