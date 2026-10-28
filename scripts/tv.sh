#!/usr/bin/env bash
set -euo pipefail
umask 077
if [ "$#" -lt 2 ]; then echo "Usage: $0 SERIAL inspect|install APK|theme CONFIG.json" >&2; exit 2; fi
serial=$1; action=$2; shift 2
pkg=com.tvhomeviet.launcher
component="$pkg/com.gathin.tvhome.MainActivity"
adb_bin=${ADB:-adb}
repo=$(cd "$(dirname "$0")/.." && pwd)
"$adb_bin" -s "$serial" get-state >/dev/null
backup() {
  backup_dir="$repo/.local/backups/$(date -u +%Y%m%dT%H%M%SZ)"
  mkdir -p "$backup_dir"
  "$adb_bin" -s "$serial" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$backup_dir/home.txt" 2>/dev/null || true
  "$adb_bin" -s "$serial" shell pm path "$pkg" 2>/dev/null | tr -d '\r' > "$backup_dir/packages.txt" || true
  while IFS= read -r line; do
    case "$line" in package:*) "$adb_bin" -s "$serial" pull "${line#package:}" "$backup_dir/" ;; esac
  done < "$backup_dir/packages.txt"
  "$adb_bin" -s "$serial" exec-out content read --uri "content://$pkg.theme/config.json" > "$backup_dir/config.json" 2>/dev/null || true
  python3 -m json.tool "$backup_dir/config.json" >/dev/null 2>&1 || rm -f "$backup_dir/config.json"
  echo "Backup: $backup_dir (APK/config only; not firmware or all app data)"
}
case "$action" in
  inspect)
    "$adb_bin" -s "$serial" shell getprop ro.product.model
    "$adb_bin" -s "$serial" shell getprop ro.build.version.release
    "$adb_bin" -s "$serial" shell pm path "$pkg"
    "$adb_bin" -s "$serial" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
    ;;
  install)
    apk=${1:?APK path required}
    aapt_bin=${AAPT:-}
    if [ -z "$aapt_bin" ]; then aapt_bin=$(command -v aapt || true); fi
    if [ -z "$aapt_bin" ]; then
      for candidate in "${ANDROID_HOME:-}/build-tools/"*/aapt "${ANDROID_HOME:-}/build-tools/"*/aapt.exe; do [ ! -x "$candidate" ] || aapt_bin=$candidate; done
    fi
    [ -n "$aapt_bin" ] || { echo "Set AAPT to Android SDK build-tools/aapt" >&2; exit 2; }
    manifest=$("$aapt_bin" dump badging "$apk")
    case "$manifest" in "package: name='$pkg'"*) ;; *) echo "Wrong application ID; refusing install" >&2; exit 2;; esac
    backup
    "$adb_bin" -s "$serial" install -r "$apk"
    "$adb_bin" -s "$serial" shell am start -W -n "$component"
    echo "Check remote/HDMI, then choose the default Home in TV settings. Stock Home remains installed."
    ;;
  theme)
    config=${1:?JSON path required}
    python3 - "$config" <<'JSON'
import json,sys
from pathlib import Path
p=Path(sys.argv[1])
if p.stat().st_size>65536: raise SystemExit('Config exceeds 64 KiB')
if not isinstance(json.loads(p.read_text()),dict): raise SystemExit('Config must be a JSON object')
JSON
    backup
    "$adb_bin" -s "$serial" shell content write --uri "content://$pkg.theme/config.json" < "$config"
    "$adb_bin" -s "$serial" shell am force-stop "$pkg"
    "$adb_bin" -s "$serial" shell am start -W -n "$component"
    ;;
  *) echo "Unknown action: $action" >&2; exit 2 ;;
esac
