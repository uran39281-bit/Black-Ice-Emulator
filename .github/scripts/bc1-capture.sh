#!/usr/bin/env bash
set -euo pipefail
APP=com.blackice.bc1.preview
mkdir -p screenshots
adb install -r qa-apk/app-debug.apk
adb shell input keyevent 82
capture() {
  adb shell am force-stop "$APP"
  adb shell wm size "$2"
  adb shell wm density "$3"
  adb shell am start -W -n "$APP/com.blackice.bc1.WorkbenchActivity"
  sleep 3
  adb exec-out screencap -p > "screenshots/$1.png"
  test -s "screenshots/$1.png"
}
capture phone-9x16 1080x1920 320
capture wide-16x9 1920x1080 160
adb shell am force-stop "$APP"
adb shell run-as "$APP" mkdir -p shared_prefs
printf '%s' '<?xml version="1.0" encoding="utf-8"?><map><string name="workspace-mode">WIDE</string></map>' | adb shell run-as "$APP" tee shared_prefs/bc1-workspace.xml > /dev/null
capture compact-wide 780x420 160
adb logcat -d -s AndroidRuntime:E > screenshots/runtime-errors.txt
