#!/usr/bin/env bash
# Turns off the TV maker's ads, tracking and preloaded apps listed in a debloat file, over ADB.
#
# Nothing is uninstalled: every package is disabled with `pm disable-user --user 0`, and
# scripts/restore.sh turns back on exactly what this script turned off. Packages that are not on
# the TV are skipped, so one list works across models of the same brand.
#
#   scripts/debloat.sh debloat/sony-bravia.txt              apply the list
#   scripts/debloat.sh --dry-run debloat/sony-bravia.txt    show what would change, change nothing
#   scripts/restore.sh                                      undo everything this script did
#
# After a firmware update, run the same command again: it reports and re-disables whatever the
# update turned back on (--dry-run only reports it).
#
# The TV must be connected first (`adb connect <TV-IP>:5555`). With more than one device connected,
# pick one with ANDROID_SERIAL=<TV-IP>:5555.
#
# The safety checks follow CleanYourTV's tools/disable.sh (MIT, github.com/Gathin23/cleanyourtv):
#   - a list may contain bare package names only (they are pasted into a shell on the TV);
#   - core Android, the TV provider (Shelf's top shelf reads it) and the TV's input and tuner
#     services are refused outright, since without them the TV may not boot or show HDMI;
#   - the app the Home button opens is skipped unless Shelf is installed to take over;
#   - Google TV's setup wizard (setupwraith) is skipped: disabling it deletes the Wi-Fi network
#     saved during first setup, and the TV drops off the network. FORCE_SETUPWRAITH=1 overrides
#     it, e.g. on Ethernet.
set -euo pipefail

DRY_RUN=0
case "${1:-}" in
  --dry-run|--check) DRY_RUN=1; shift ;;
  -h|--help) sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
esac
LIST="${1:?usage: scripts/debloat.sh [--dry-run] <debloat list>, e.g. debloat/sony-bravia.txt}"
[ -r "$LIST" ] || { echo "cannot read the list: $LIST" >&2; exit 1; }

SHELF=com.shelf.launcher
# Where the TV keeps the record of what this script disabled, so restore.sh works from any computer.
RECORD=/data/local/tmp/shelf-debloat.txt

command -v adb > /dev/null || {
  echo "adb is not installed. Mac: brew install --cask android-platform-tools" >&2
  echo "Windows / Linux: https://developer.android.com/tools/releases/platform-tools" >&2
  exit 1
}

# Strip a BOM, CRLF line ends, comments and blank lines; keep the first word of each line.
PKGS=$(LC_ALL=C awk 'NR==1{sub(/^\357\273\277/,"")} {sub(/\r$/,""); sub(/#.*/,""); if ($1 != "") print $1}' "$LIST")
[ -n "$PKGS" ] || { echo "refusing $LIST: no package names in it (every line is a comment or blank)" >&2; exit 1; }

BAD=$(printf '%s\n' "$PKGS" | grep -vE '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' || true)
if [ -n "$BAD" ]; then
  { echo "refusing $LIST: not a package name:"; printf '  %s\n' $BAD; } >&2
  exit 1
fi

# Without these Android or the TV itself can stop working: never disabled, whatever the list says.
CORE_RE='^(android|android\.ext\.(services|shared)|com\.android\.(systemui|shell|phone|settings|tv\.settings|sdksandbox|location\.fused|providers\..*)|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata|gms|gsf)|com\.google\.android\.overlay\.modules.*|com\.android\.vending|com\.cltv\.mal|com\.mediatek\.(tis|tv\.service|tv\.agent)|com\.sony\.dtv\.(tvx|tvinput\..*|tvapi|sonysystemservice|braviasync.*|settings.*)|com\.shelf\.launcher)$'
CORE_OK='^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$'
CORE=$(printf '%s\n' "$PKGS" | grep -E "$CORE_RE" | grep -vE "$CORE_OK" || true)
if [ -n "$CORE" ]; then
  { echo "refusing $LIST: it lists packages the TV needs to boot, show HDMI or run Shelf:"
    printf '  %s\n' $CORE
    echo "Take these lines out of the list and run it again. Nothing was changed on the TV."; } >&2
  exit 1
fi

state=$(adb get-state 2>/dev/null || true)
if [ "$state" != "device" ]; then
  echo "No TV connected over ADB. Run: adb connect <TV-IP>:5555 (and allow it on the TV)." >&2
  echo "With several devices connected, choose one with ANDROID_SERIAL=<TV-IP>:5555." >&2
  exit 1
fi

tv() { adb shell "$@" | tr -d '\r'; }

model=$(tv getprop ro.product.model)
release=$(tv getprop ro.build.version.release)
echo "TV: $model, Android $release"

installed=$(tv pm list packages | sed 's/^package://')
disabled=$(tv pm list packages -d | sed 's/^package://')
home=$(tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
  | tail -n 1 | cut -d/ -f1)
shelf_ready=0
if printf '%s\n' "$installed" | grep -qx "$SHELF" && ! printf '%s\n' "$disabled" | grep -qx "$SHELF"; then
  shelf_ready=1
fi

has() { printf '%s\n' "$2" | grep -qxF "$1"; }

to_disable=()
missing=0
already=0
for pkg in $PKGS; do
  if ! has "$pkg" "$installed"; then missing=$((missing + 1)); continue; fi
  if has "$pkg" "$disabled"; then already=$((already + 1)); continue; fi
  if [ "$pkg" = "$home" ] && [ "$shelf_ready" -eq 0 ]; then
    echo "skip $pkg: it is the TV's home screen and Shelf is not installed to take over"
    continue
  fi
  if [ "$pkg" = com.google.android.tungsten.setupwraith ] && [ -z "${FORCE_SETUPWRAITH:-}" ]; then
    echo "skip $pkg: disabling it deletes the Wi-Fi network saved during first setup."
    echo "  Forget and re-join the network in Settings first, or use FORCE_SETUPWRAITH=1 on Ethernet."
    continue
  fi
  to_disable+=("$pkg")
done

echo "$already already off, $missing not on this TV, ${#to_disable[@]} to turn off."
[ "${#to_disable[@]}" -gt 0 ] || { echo "Nothing to do."; exit 0; }

if [ "$DRY_RUN" -eq 1 ]; then
  printf '  would disable %s\n' "${to_disable[@]}"
  echo "Dry run: nothing was changed. Run without --dry-run to apply."
  exit 0
fi

done_count=0
for pkg in "${to_disable[@]}"; do
  out=$(tv pm disable-user --user 0 "$pkg" 2>&1 || true)
  if printf '%s' "$out" | grep -q 'disabled-user'; then
    # Record it on the TV first, so an interrupted run can still be undone.
    tv "grep -qx '$pkg' $RECORD 2>/dev/null || echo '$pkg' >> $RECORD"
    echo "  off  $pkg"
    done_count=$((done_count + 1))
  else
    echo "  FAILED $pkg: $out"
  fi
done

# A copy of the record on this computer too, in case the TV's is lost.
serial=$(tv getprop ro.serialno)
backup_dir="$(cd "$(dirname "$0")/.." && pwd)/debloat/backups"
mkdir -p "$backup_dir"
tv cat "$RECORD" > "$backup_dir/${serial:-tv}.txt" 2>/dev/null || true

echo
echo "Done: $done_count turned off. Nothing was uninstalled."
echo "Undo any time with: scripts/restore.sh  (or one app: adb shell pm enable <package>)"
