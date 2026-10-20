#!/usr/bin/env bash
# Switches the emulator to Hebrew and takes screenshots of the menus.
# Best effort: needs a debuggable emulator image, never fails the build.
set -uo pipefail

PKG=com.shelf.launcher
OUT=screenshots/hebrew
mkdir -p "$OUT"

shot() {
  sleep 2
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "hebrew $1"
}
press() { adb shell input keyevent "$1"; }

adb root > /dev/null 2>&1 || true
sleep 4
adb wait-for-device

# Change the system language and restart the Android framework so it applies.
adb shell setprop persist.sys.locale iw-IL || { echo "can't change the language here"; exit 0; }
adb shell setprop ctl.restart zygote || { echo "can't restart the framework here"; exit 0; }
sleep 15
adb wait-for-device
for i in $(seq 1 60); do
  adb shell pm path android > /dev/null 2>&1 && break
  sleep 3
done
sleep 30
echo "locale now: $(adb shell getprop persist.sys.locale)"

adb shell am start -n "$PKG/.MainActivity" > /dev/null
sleep 10
shot 01-home

press KEYCODE_DPAD_RIGHT; sleep 1
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; shot 02-menu
press KEYCODE_BACK; sleep 1

press KEYCODE_DPAD_UP; sleep 1
press KEYCODE_DPAD_CENTER; shot 03-settings
press KEYCODE_DPAD_CENTER; shot 04-appearance
press KEYCODE_BACK; sleep 1
press KEYCODE_DPAD_DOWN; sleep 1
press KEYCODE_DPAD_CENTER; shot 05-apps
press KEYCODE_BACK; sleep 1
press KEYCODE_DPAD_DOWN; sleep 1
press KEYCODE_DPAD_CENTER; shot 06-home-button
press KEYCODE_BACK; sleep 1
press KEYCODE_BACK; shot 07-closed
