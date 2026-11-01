#!/usr/bin/env bash
# Turns back on what scripts/debloat.sh turned off.
#
#   scripts/restore.sh                         everything debloat.sh disabled on this TV
#   scripts/restore.sh debloat/sony-bravia.txt only the packages in that list
#   scripts/restore.sh --stock-home            also turn the TV's own launcher back on
#
# The record of what was disabled lives on the TV (/data/local/tmp/shelf-debloat.txt), with a copy
# on the computer that ran debloat.sh (debloat/backups/). Packages the TV maker disabled at the
# factory are never in it, so they stay as they were.
set -euo pipefail

STOCK_HOME=0
if [ "${1:-}" = "--stock-home" ]; then STOCK_HOME=1; shift; fi
LIST="${1:-}"
RECORD=/data/local/tmp/shelf-debloat.txt
# The stock launchers README.md tells people to disable by hand.
STOCK_LAUNCHERS="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher"

command -v adb > /dev/null || { echo "adb is not installed" >&2; exit 1; }
[ "$(adb get-state 2>/dev/null || true)" = "device" ] || {
  echo "No TV connected over ADB. Run: adb connect <TV-IP>:5555" >&2
  exit 1
}

tv() { adb shell "$@" | tr -d '\r'; }

serial=$(tv getprop ro.serialno)
backup="$(cd "$(dirname "$0")/.." && pwd)/debloat/backups/${serial:-tv}.txt"

recorded=$( { tv cat "$RECORD" 2>/dev/null || true; [ -r "$backup" ] && cat "$backup"; } \
  | grep -E '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' | sort -u || true)

if [ -n "$LIST" ]; then
  [ -r "$LIST" ] || { echo "cannot read the list: $LIST" >&2; exit 1; }
  wanted=$(LC_ALL=C awk 'NR==1{sub(/^\357\273\277/,"")} {sub(/\r$/,""); sub(/#.*/,""); if ($1 != "") print $1}' "$LIST" | sort -u)
  if [ -n "$recorded" ]; then
    targets=$(comm -12 <(printf '%s\n' "$recorded") <(printf '%s\n' "$wanted"))
  else
    echo "No record of what debloat.sh disabled on this TV: turning on every disabled package in $LIST."
    disabled=$(tv pm list packages -d | sed 's/^package://' | sort -u)
    targets=$(comm -12 <(printf '%s\n' "$disabled") <(printf '%s\n' "$wanted"))
  fi
else
  targets=$recorded
fi

if [ "$STOCK_HOME" -eq 1 ]; then
  targets=$(printf '%s\n' $targets $STOCK_LAUNCHERS | sort -u)
fi

[ -n "$(printf '%s' "$targets" | tr -d '[:space:]')" ] || { echo "Nothing to turn back on."; exit 0; }

installed=$(tv pm list packages -u | sed 's/^package://')
restored=()
for pkg in $targets; do
  printf '%s\n' "$installed" | grep -qxF "$pkg" || continue
  out=$(tv pm enable "$pkg" 2>&1 || true)
  if printf '%s' "$out" | grep -q 'new state: enabled'; then
    echo "  on   $pkg"
    restored+=("$pkg")
  else
    echo "  FAILED $pkg: $out"
  fi
done

# Forget what was turned back on; keep the rest for a later restore.
if [ "${#restored[@]}" -gt 0 ]; then
  left=$(printf '%s\n' $recorded | grep -vxF -f <(printf '%s\n' "${restored[@]}") || true)
  if [ -n "$left" ]; then
    # Package names only (checked above), so they are safe to pass to the TV's shell.
    tv "printf '%s\n' $(printf '%s ' $left) > $RECORD"
  else
    tv rm -f "$RECORD"
  fi
  if [ -r "$backup" ]; then
    if [ -n "$left" ]; then printf '%s\n' "$left" > "$backup"; else rm -f "$backup"; fi
  fi
fi

echo "Done: ${#restored[@]} turned back on."
