#!/usr/bin/env bash
# Records a short demo video of Shelf on the Android TV emulator, using colorful
# placeholder apps so the changing backdrop shows. Never fails the build.
set -uo pipefail

PKG=com.shelf.launcher
OUT=screenshots
mkdir -p "$OUT"

for apk in demo-apks/*.apk; do
  adb install -r "$apk" > /dev/null && echo "installed $apk"
done

# A cleaner first row: hide the emulator's own sample apps (emulator only).
for p in com.google.android.tv.remote.service com.google.android.play.games com.google.android.music; do
  adb shell pm disable-user --user 0 "$p" > /dev/null 2>&1 || true
done

# Fresh start: default order and settings, Home button service on.
adb shell pm clear "$PKG" > /dev/null
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.home.HomeButtonService"
adb shell settings put secure accessibility_enabled 1
sleep 3
adb shell am start -n "$PKG/.MainActivity" > /dev/null
sleep 8

adb shell screenrecord --bit-rate 8000000 --time-limit 24 /sdcard/demo.mp4 &
REC=$!
sleep 2
for key in RIGHT RIGHT RIGHT RIGHT RIGHT DOWN RIGHT UP LEFT LEFT; do
  adb shell input keyevent "KEYCODE_DPAD_$key"
  sleep 1.8
done
wait "$REC"
sleep 2
adb pull /sdcard/demo.mp4 "$OUT/demo.mp4" && echo "demo recorded"
adb exec-out screencap -p > "$OUT/demo-last-frame.png"
