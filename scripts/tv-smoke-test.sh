#!/usr/bin/env bash
# Installs the launcher on an Android TV emulator (Android 10, 1080p),
# makes it the home screen, presses some remote buttons and takes screenshots.
# Fails if the app crashes.
set -euo pipefail

PKG=com.shelf.launcher
OUT=screenshots
mkdir -p "$OUT"

shot() {
  sleep 2
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "screenshot: $1"
}

press() {
  adb shell input keyevent "$1"
}

adb logcat -c || true
adb install -r ShelfLauncher.apk

# Make it the default home screen, then press Home.
adb shell cmd package set-home-activity "$PKG/.MainActivity"
press KEYCODE_HOME
sleep 8
shot 1-home

press KEYCODE_DPAD_RIGHT; shot 2-right
press KEYCODE_DPAD_RIGHT; shot 3-right-again
press KEYCODE_DPAD_DOWN;  shot 4-down
press KEYCODE_BACK;       shot 5-back
press KEYCODE_HOME;       shot 6-home-again

# Open the focused app and come back with Home.
press KEYCODE_DPAD_CENTER; sleep 4; shot 7-opened-app
press KEYCODE_HOME;        sleep 3; shot 8-back-home

adb logcat -d -b crash > "$OUT/crash-log.txt" || true
adb logcat -d > "$OUT/full-log.txt" || true

if grep -q "$PKG" "$OUT/crash-log.txt"; then
  echo "::error::The launcher crashed. See crash-log.txt in the screenshots artifact."
  cat "$OUT/crash-log.txt"
  exit 1
fi

if ! adb shell pidof "$PKG" > /dev/null; then
  echo "::error::The launcher is not running after the test."
  exit 1
fi

echo "Launcher ran on the TV emulator without crashing."
