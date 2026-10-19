#!/usr/bin/env bash
# Captures the frames for the demo video of Shelf on the Android TV emulator,
# using colorful placeholder apps so the changing backdrop shows.
# One sharp screenshot per step; make-demo-video.sh joins them with crossfades.
# Never fails the build.
set -uo pipefail

PKG=com.shelf.launcher
OUT=screenshots/demo
mkdir -p "$OUT"

for apk in demo-apks/*.apk; do
  adb install -r "$apk" > /dev/null && echo "installed $apk"
done

# A cleaner dock: hide the emulator's own sample apps (emulator only).
for p in com.google.android.tv.remote.service com.google.android.play.games com.google.android.music; do
  adb shell pm disable-user --user 0 "$p" > /dev/null 2>&1 || true
done

# Fresh start (default order and settings): reinstall Shelf, then switch on the
# Home button service like a user would, so there's no setup pill in the shot.
adb uninstall "$PKG" > /dev/null 2>&1 || true
adb install ShelfLauncher.apk > /dev/null && echo "reinstalled Shelf"
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.home.HomeButtonService"
adb shell settings put secure accessibility_enabled 1
sleep 4
adb shell input keyevent KEYCODE_HOME   # built-in launcher → service → Shelf
sleep 10

n=0
snap() {
  sleep 1.5
  n=$((n + 1))
  adb exec-out screencap -p > "$OUT/$(printf '%02d' "$n").png"
  echo "frame $n"
}

# Dock: Arcade, Cinema, Google Play Store, Kids, Music, News
# Row 2: Sports, YouTube, Settings
snap                                  # Arcade
for key in RIGHT RIGHT RIGHT RIGHT RIGHT LEFT LEFT LEFT LEFT DOWN RIGHT UP; do
  adb shell input keyevent "KEYCODE_DPAD_$key"
  snap
done
