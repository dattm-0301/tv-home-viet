#!/usr/bin/env bash
set -euo pipefail
umask 077
if [ "$#" -lt 2 ]; then echo "Usage: $0 SERIAL inspect|install APK|theme CONFIG.json|theme-replace CONFIG.json|personal PERSONAL.json" >&2; exit 2; fi
serial=$1; action=$2; shift 2
pkg=com.tvhomeviet.launcher
component="$pkg/com.gathin.tvhome.MainActivity"
adb_bin=${ADB:-adb}
repo=$(cd "$(dirname "$0")/.." && pwd)
"$adb_bin" -s "$serial" get-state >/dev/null
backup() {
  mkdir -p "$repo/.local/backups"
  backup_dir=$(mktemp -d "$repo/.local/backups/$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX")
  "$adb_bin" -s "$serial" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$backup_dir/home.txt" 2>/dev/null || true
  "$adb_bin" -s "$serial" shell pm path "$pkg" 2>/dev/null | tr -d '\r' > "$backup_dir/packages.txt" || true
  while IFS= read -r line; do
    case "$line" in package:*) "$adb_bin" -s "$serial" pull "${line#package:}" "$backup_dir/" ;; esac
  done < "$backup_dir/packages.txt"
  "$adb_bin" -s "$serial" exec-out content read --uri "content://$pkg.theme/config.json" > "$backup_dir/config.json" 2>/dev/null || true
  python3 -m json.tool "$backup_dir/config.json" >/dev/null 2>&1 || rm -f "$backup_dir/config.json"
  "$adb_bin" -s "$serial" exec-out content read --uri "content://$pkg.theme/personal.json" > "$backup_dir/personal.json" 2>/dev/null || true
  python3 -m json.tool "$backup_dir/personal.json" >/dev/null 2>&1 || rm -f "$backup_dir/personal.json"
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
    "$adb_bin" -s "$serial" shell am start -n "$component"
    echo "Check remote/HDMI, then choose the default Home in TV settings. Stock Home remains installed."
    ;;
  theme|theme-replace|personal)
    config=${1:?JSON path required}
    # Validate before touching the TV. A personal import replaces only personal overrides.
    kind=theme; endpoint=config.json
    if [ "$action" = personal ]; then kind=personal; endpoint=personal.json; fi
    python3 "$repo/scripts/config.py" "$kind" "$config" >/dev/null
    backup
    prepared="$backup_dir/import.json"
    if [ "$action" = theme ] && [ -f "$backup_dir/config.json" ]; then
      python3 "$repo/scripts/config.py" theme "$config" "$backup_dir/config.json" > "$prepared"
    else
      python3 "$repo/scripts/config.py" "$kind" "$config" > "$prepared"
    fi
    "$adb_bin" -s "$serial" shell content write --uri "content://$pkg.theme/$endpoint" < "$prepared"
    "$adb_bin" -s "$serial" shell am force-stop "$pkg"
    "$adb_bin" -s "$serial" shell am start -n "$component"
    echo "Applied $endpoint. Personal options take precedence over theme defaults."
    ;;
  *) echo "Unknown action: $action" >&2; exit 2 ;;
esac
