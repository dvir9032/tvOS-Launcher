#!/usr/bin/env bash
# Installs the launcher on an Android TV emulator (Android 10, 1080p),
# makes it the home screen, presses some remote buttons and takes screenshots.
# Fails if the app crashes or isn't on screen at the end.
set -uo pipefail

PKG=com.shelf.launcher
OUT=screenshots
mkdir -p "$OUT"
LOG="$OUT/steps.txt"

resumed() {
  adb shell dumpsys activity activities | grep -m1 -E "mResumedActivity|ResumedActivity" | tr -s ' '
}

shot() {
  sleep 2
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "$1 :: $(resumed)" | tee -a "$LOG"
}

press() {
  adb shell input keyevent "$1"
}

adb logcat -c || true
adb install -r ShelfLauncher.apk | tee -a "$LOG"

# Like a Sony TV: the built-in launcher stays enabled and stays the default home.
echo "--- start directly" | tee -a "$LOG"
adb shell am start -W -n "$PKG/.MainActivity" 2>&1 | tee -a "$LOG"
sleep 8
shot 0-setup-pill   # should show the orange "set up Home button" pill

# What the user does once with the remote: switch on Shelf in Accessibility.
echo "--- enable the Home button accessibility service" | tee -a "$LOG"
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.home.HomeButtonService"
adb shell settings put secure accessibility_enabled 1
sleep 4

# Home now goes to the built-in launcher first; the service should bring Shelf back.
press KEYCODE_HOME; sleep 5
shot 1-start

press KEYCODE_DPAD_RIGHT; shot 2-right
press KEYCODE_DPAD_RIGHT; shot 3-right-again
press KEYCODE_DPAD_DOWN;  shot 4-down
press KEYCODE_BACK;       shot 5-back
press KEYCODE_HOME;       sleep 3; shot 6-home-button

# Open the focused app and come back with Home.
press KEYCODE_DPAD_CENTER; sleep 4; shot 7-opened-app
press KEYCODE_HOME;        sleep 4; shot 8-back-home

# --- 2.0 features ---
# Long-press OK on the first app: the menu (Open / Move / Hide) should appear.
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; shot 09-menu
# Pick "Move", move the app one step right, confirm.
press KEYCODE_DPAD_DOWN; sleep 1
press KEYCODE_DPAD_CENTER; shot 10-move-mode
press KEYCODE_DPAD_RIGHT;  shot 11-moved-right
press KEYCODE_DPAD_CENTER; shot 12-move-done
# Up to the settings button in the top bar, open settings.
press KEYCODE_DPAD_UP; sleep 1
press KEYCODE_DPAD_CENTER; shot 13-settings
press KEYCODE_BACK; shot 14-settings-closed

adb logcat -d > "$OUT/full-log.txt" || true
grep -A40 "FATAL EXCEPTION" "$OUT/full-log.txt" > "$OUT/crash.txt" || true
grep -E "$PKG|ShelfLauncher" "$OUT/full-log.txt" | tail -80 > "$OUT/app-log.txt" || true

if grep -q "$PKG" "$OUT/crash.txt"; then
  echo "::error title=Launcher crashed::$(head -40 "$OUT/crash.txt" | sed ':a;N;$!ba;s/%/%25/g;s/\r//g;s/\n/%0A/g')"
  exit 1
fi

if ! resumed | grep -q "$PKG"; then
  echo "::error title=Launcher not on screen::$(sed ':a;N;$!ba;s/%/%25/g;s/\r//g;s/\n/%0A/g' "$LOG")"
  exit 1
fi

echo "Launcher ran on the TV emulator without crashing." | tee -a "$LOG"
