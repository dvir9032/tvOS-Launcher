#!/usr/bin/env bash
# Captures the frames for the full tour video: every main feature of Shelf,
# one sharp screenshot per step, each with a caption (captions.txt).
# make-tour-video.sh turns them into screenshots/tour.mp4. Never fails the build.
set -uo pipefail

PKG=com.shelf.launcher
OUT=screenshots/tour
mkdir -p "$OUT"
: > "$OUT/captions.txt"

# --- Fresh Shelf with the colorful demo apps (record-demo.sh installed them) ---
for apk in demo-apks/*.apk; do adb install -r "$apk" > /dev/null; done
for p in com.google.android.tv.remote.service com.google.android.play.games com.google.android.music; do
  adb shell pm disable-user --user 0 "$p" > /dev/null 2>&1 || true
done
adb uninstall "$PKG" > /dev/null 2>&1 || true
adb install ShelfLauncher.apk > /dev/null && echo "reinstalled Shelf"
adb shell pm grant "$PKG" android.permission.READ_TV_LISTINGS || true
adb shell settings delete secure enabled_accessibility_services > /dev/null 2>&1 || true
sleep 2
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.home.HomeButtonService"
adb shell settings put secure accessibility_enabled 1
sleep 6
adb shell input keyevent KEYCODE_HOME
sleep 10
in_front() { adb shell dumpsys activity activities | grep -m1 -E "mResumedActivity|ResumedActivity" | grep -q "$PKG"; }
if ! in_front; then adb shell am start -n "$PKG/.MainActivity" > /dev/null; sleep 10; fi
in_front || { echo "Shelf not in front; skipping the tour"; exit 0; }

n=0
# snap "caption" [wait]: wait (default 1.5 s), take a frame, remember its caption.
snap() {
  sleep "${2:-1.5}"
  n=$((n + 1))
  local f; f=$(printf '%02d' "$n")
  adb exec-out screencap -p > "$OUT/$f.png"
  echo "$f|$1" >> "$OUT/captions.txt"
  echo "frame $f: $1"
}
key() { adb shell input keyevent "KEYCODE_$1"; }
hold_ok() { adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; }

# Dock: Arcade, Cinema, Play Store, Kids, Music, News — row 2: Sports, YouTube, Settings
snap "Shelf: a tvOS-inspired home screen for Android TV" 1
key DPAD_RIGHT; snap "The backdrop follows the app in focus"
key DPAD_RIGHT; snap "The backdrop follows the app in focus"
key DPAD_RIGHT; snap "Apps sit in a frosted glass dock"
key DPAD_RIGHT; snap "Apps sit in a frosted glass dock"
snap "Leave the remote alone and everything else dims" 7
for i in 1 2 3 4; do key DPAD_LEFT; sleep 0.4; done
key DPAD_DOWN;  snap "More apps fade in below the dock"
key DPAD_RIGHT; snap "More apps fade in below the dock"

# Move YouTube up into the dock (shows the menu and Move at the same time)
hold_ok;        snap "Hold OK for options"
key DPAD_DOWN; sleep 1
key DPAD_CENTER; snap "Move apps anywhere"
key DPAD_UP;     snap "Move apps anywhere"
key DPAD_CENTER; snap "Top shelf: what's new inside the app" 5
key DPAD_UP;     snap "Open it straight from the home screen" 2
key DPAD_RIGHT;  snap "Open it straight from the home screen" 2
key DPAD_RIGHT;  snap "Open it straight from the home screen" 2

# Control Center (from an app without content, Up goes to the top bar)
key DPAD_DOWN; sleep 1
for i in 1 2 3 4 5; do key DPAD_LEFT; sleep 0.4; done
key DPAD_UP; snap "Control Center" 1
key DPAD_CENTER; snap "Control Center: sound, theme, sleep timer, picture"
key DPAD_RIGHT;  key DPAD_CENTER; snap "Light theme, or automatic at night"
key BACK;        snap "Light theme, or automatic at night"
key DPAD_UP; sleep 1; key DPAD_CENTER; sleep 1; key DPAD_RIGHT; sleep 1
key DPAD_CENTER; sleep 1; key DPAD_CENTER; sleep 1   # Light → Auto → Dark
key DPAD_DOWN; sleep 1; key DPAD_DOWN; sleep 1
for i in 1 2 3 4 5; do key DPAD_LEFT; done; sleep 1
key DPAD_CENTER; snap "Settings in the tvOS style"
key DPAD_CENTER; snap "Make it yours: theme, sounds, clock, glass"
key BACK; sleep 1; key BACK; sleep 1; key BACK; sleep 2

# Folders: first app → Add to folder → Games
hold_ok; sleep 2
for i in 1 2 3; do key DPAD_DOWN; sleep 0.8; done
key DPAD_CENTER; snap "Group apps into folders"
key DPAD_CENTER; snap "Group apps into folders"
key DPAD_CENTER; snap "Group apps into folders"
key BACK; snap "Free and open source: github.com/dvir9032/tvOS-Launcher" 2
