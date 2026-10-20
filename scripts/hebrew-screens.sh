#!/usr/bin/env bash
# Screenshots of Shelf's menus in Hebrew. Runs on an Android TV 13 emulator,
# where one app's language can be set without root. Never fails the build.
set -uo pipefail

PKG=com.shelf.launcher
OUT=screenshots-hebrew
mkdir -p "$OUT"

shot() {
  sleep 2
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "hebrew $1"
}
press() { adb shell input keyevent "$1"; }

adb install -r ShelfLauncher.apk && echo "installed Shelf"
adb shell pm grant "$PKG" android.permission.READ_TV_LISTINGS || true
for apk in demo-apks/*.apk; do adb install -r "$apk" > /dev/null && echo "installed $apk"; done

# Shelf in Hebrew (the rest of the TV stays in English).
adb shell cmd locale set-app-locales "$PKG" --locales he-IL
echo "Shelf locale: $(adb shell cmd locale get-app-locales "$PKG" 2>&1)"

# Home button service on, like a user would after setup.
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.home.HomeButtonService"
adb shell settings put secure accessibility_enabled 1
sleep 4

adb shell am start -n "$PKG/.MainActivity" > /dev/null
sleep 12
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
press KEYCODE_DPAD_DOWN; sleep 1
press KEYCODE_DPAD_CENTER; shot 07-updates
press KEYCODE_BACK; sleep 1
press KEYCODE_BACK; shot 08-closed
