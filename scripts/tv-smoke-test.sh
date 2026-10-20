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
# What the user does once: allow Shelf to show content from apps.
adb shell pm grant "$PKG" android.permission.READ_TV_LISTINGS 2>&1 | tee -a "$LOG"

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
shot 1-start   # the orange pill should be gone now

press KEYCODE_DPAD_RIGHT; shot 2-right
press KEYCODE_DPAD_RIGHT; shot 3-right-again
press KEYCODE_DPAD_DOWN;  shot 4-down
press KEYCODE_BACK;       shot 5-back
press KEYCODE_HOME;       sleep 3; shot 6-home-button

# Open the focused app and come back with Home.
press KEYCODE_DPAD_CENTER; sleep 4; shot 7-opened-app
# The app the user opened must stay in front (the Home button service must not cover it).
sleep 3
if resumed | grep -q "$PKG"; then
  echo "::error title=Opened app was covered::Shelf came back over the app it just opened. $(resumed)"
  exit 1
fi
echo "7b-app-still-open :: $(resumed)" | tee -a "$LOG"
press KEYCODE_HOME;        sleep 4; shot 8-back-home

# --- Diagnostic: open an app right after Home (Android holds app starts ~5 s after Home) ---
echo "--- quick open after Home" | tee -a "$LOG"
press KEYCODE_HOME; sleep 4                      # leave Shelf settled first
adb logcat -c || true
t0=$(date +%s%N)
press KEYCODE_HOME
for i in $(seq 1 40); do resumed | grep -q "$PKG" && break; sleep 0.25; done
t1=$(date +%s%N)
echo "home -> Shelf in front: $(( (t1 - t0) / 1000000 )) ms" | tee -a "$LOG"
press KEYCODE_DPAD_CENTER
opened=no
for i in $(seq 1 40); do
  if ! resumed | grep -q "$PKG"; then opened=yes; break; fi
  sleep 0.25
done
t2=$(date +%s%N)
echo "OK -> app in front: $opened after $(( (t2 - t1) / 1000000 )) ms :: $(resumed)" | tee -a "$LOG"
adb logcat -d | grep -iE "stopped|app switch|Background activity start|Abort background" | tail -10 | tee -a "$LOG"
press KEYCODE_HOME; sleep 6

# --- 2.0 features ---
# Long-press OK on the first app: the menu (Open / Move / Hide) should appear.
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; shot 09-menu
# Pick "Move", move the app one step right, confirm.
press KEYCODE_DPAD_DOWN; sleep 1
press KEYCODE_DPAD_CENTER; shot 10-move-mode
press KEYCODE_DPAD_RIGHT;  shot 11-moved-right
press KEYCODE_DPAD_CENTER; shot 12-move-done
# Up to the Control Center button in the top bar.
press KEYCODE_DPAD_UP; sleep 1
press KEYCODE_DPAD_CENTER; shot 12b-control-center
# Settings is the first round button: down twice, then all the way left.
press KEYCODE_DPAD_DOWN; sleep 1
press KEYCODE_DPAD_DOWN; sleep 1
for i in 1 2 3 4 5; do press KEYCODE_DPAD_LEFT; done; sleep 1
press KEYCODE_DPAD_CENTER; shot 13-settings
press KEYCODE_DPAD_CENTER; shot 13b-appearance      # first category
press KEYCODE_DPAD_CENTER; shot 13c-light-theme     # Theme: Dark → Light
press KEYCODE_DPAD_CENTER; sleep 1                  # Light → Auto
press KEYCODE_DPAD_CENTER; sleep 1                  # Auto → Dark, back to normal
press KEYCODE_BACK;        shot 13d-settings-main
press KEYCODE_DPAD_DOWN;   sleep 1
press KEYCODE_DPAD_DOWN;   sleep 1
press KEYCODE_DPAD_CENTER; shot 13e-home-button-page
press KEYCODE_BACK;        sleep 1
press KEYCODE_BACK;        shot 14-settings-closed

# Light theme from Control Center, then back to dark.
press KEYCODE_DPAD_UP;     sleep 1
press KEYCODE_DPAD_CENTER; sleep 1
press KEYCODE_DPAD_RIGHT;  sleep 1                  # Theme tile
press KEYCODE_DPAD_CENTER; shot 14b-cc-light
press KEYCODE_BACK;        shot 14c-home-light
press KEYCODE_DPAD_UP;     sleep 1
press KEYCODE_DPAD_CENTER; sleep 1
press KEYCODE_DPAD_RIGHT;  sleep 1
press KEYCODE_DPAD_CENTER; sleep 1                  # Light → Auto
press KEYCODE_DPAD_CENTER; sleep 1                  # Auto → Dark
press KEYCODE_BACK;        sleep 2

# --- Top shelf: content from the focused app (YouTube is the 5th app in the dock) ---
press KEYCODE_BACK; sleep 2                      # back to the first app
for i in 1 2 3; do press KEYCODE_DPAD_RIGHT; sleep 1; done
press KEYCODE_DPAD_RIGHT; adb exec-out screencap -p > "$OUT/15a-focus-motion.png"   # mid-swing, light sweeping
sleep 2; shot 15b-youtube-shelf
sleep 6; shot 15-youtube-shelf-idle   # remote left alone: the other tiles dim
press KEYCODE_DPAD_UP;    sleep 3; shot 16-content-row
press KEYCODE_DPAD_RIGHT; sleep 3; shot 17-content-next
press KEYCODE_DPAD_DOWN;  sleep 1
press KEYCODE_BACK;       sleep 2

# --- Folders: first app → "Add to folder" → Games ---
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 2
for i in 1 2 3; do press KEYCODE_DPAD_DOWN; sleep 1; done
press KEYCODE_DPAD_CENTER; shot 18-folder-picker
press KEYCODE_DPAD_CENTER; shot 19-folder-tile
press KEYCODE_DPAD_CENTER; shot 20-folder-open
press KEYCODE_BACK;        sleep 2
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; shot 21-folder-menu
press KEYCODE_BACK;        sleep 2

# --- Recent row (an app was opened earlier) ---
press KEYCODE_DPAD_DOWN;   sleep 2; shot 22-recent
press KEYCODE_BACK;        sleep 2

adb logcat -d > "$OUT/full-log.txt" || true
grep "ShelfContent" "$OUT/full-log.txt" | tail -5 | tee -a "$LOG"
grep -A40 "FATAL EXCEPTION" "$OUT/full-log.txt" > "$OUT/crash.txt" || true
grep -E "$PKG|ShelfLauncher|ShelfContent" "$OUT/full-log.txt" | tail -80 > "$OUT/app-log.txt" || true

if grep -q "$PKG" "$OUT/crash.txt"; then
  echo "::error title=Launcher crashed::$(head -40 "$OUT/crash.txt" | sed ':a;N;$!ba;s/%/%25/g;s/\r//g;s/\n/%0A/g')"
  exit 1
fi

if ! resumed | grep -q "$PKG"; then
  echo "::error title=Launcher not on screen::$(sed ':a;N;$!ba;s/%/%25/g;s/\r//g;s/\n/%0A/g' "$LOG")"
  exit 1
fi

echo "Launcher ran on the TV emulator without crashing." | tee -a "$LOG"
