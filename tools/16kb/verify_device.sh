#!/usr/bin/env bash
# Usage: verify_device.sh SERIAL PACKAGE [inspect|launch|compat-off|compat-on|fatal]
# Run after installation; launch explicitly clears logcat and starts the launcher activity.
set -euo pipefail
serial="${1:?Device serial required}"
package="${2:?Package name required}"
action="${3:-inspect}"
adb_bin="${ADB:-adb}"
adb_device=("$adb_bin" -s "$serial")
page_size="$("${adb_device[@]}" shell getconf PAGE_SIZE | tr -d '\r')"
api="$("${adb_device[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
printf 'serial=%s package=%s api=%s pageSize=%s\n' "$serial" "$package" "$api" "$page_size"
case "$action" in
    inspect) ;;
    launch)
        "${adb_device[@]}" logcat -c
        "${adb_device[@]}" shell am force-stop "$package"
        "${adb_device[@]}" shell monkey -p "$package" -c android.intent.category.LAUNCHER 1
        ;;
    compat-off|compat-on|fatal)
        if [[ "$action" == fatal ]]; then
            (( api >= 37 )) || { echo 'fatal requires Android 17/API 37+' >&2; exit 1; }
            "${adb_device[@]}" shell setprop bionic.linker.16kb.app_compat.enabled fatal
            "${adb_device[@]}" shell setprop pm.16kb.app_compat.disabled true
        else
            enabled=false
            [[ "$action" == compat-on ]] && enabled=true
            "${adb_device[@]}" shell setprop bionic.linker.16kb.app_compat.enabled "$enabled"
            "${adb_device[@]}" shell setprop pm.16kb.app_compat.disabled "$([[ "$enabled" == true ]] && echo false || echo true)"
        fi
        ;;
    *) echo "Unknown action: $action" >&2; exit 2 ;;
esac
printf 'linkerCompat=%s packageManagerCompatDisabled=%s\n' \
    "$("${adb_device[@]}" shell getprop bionic.linker.16kb.app_compat.enabled | tr -d '\r')" \
    "$("${adb_device[@]}" shell getprop pm.16kb.app_compat.disabled | tr -d '\r')"
"${adb_device[@]}" shell dumpsys package "$package" | grep -Ei 'page.?size|16.?k|compat|primaryCpuAbi|versionName|versionCode' || true
"${adb_device[@]}" logcat -d -v threadtime | grep -Ei '16.?k[bB]|page.?size|NATIVE_LIBRARY_PAGE_ALIGNMENT_UNSUPPORTED|linker.*(error|fatal)' || true
[[ "$page_size" == 16384 ]] || { echo 'This receipt is from a 4 KB device, not a 16 KB acceptance run.' >&2; exit 1; }
