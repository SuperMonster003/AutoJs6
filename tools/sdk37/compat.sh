#!/usr/bin/env bash
# Usage: compat.sh SERIAL PACKAGE enable|disable|reset CHANGE [CHANGE...]
# Each invocation saves the original overrides before changing a debuggable package.
# reset restores the platform default, not a pre-existing explicit override.
# ADB selects the executable; SDK37_EVIDENCE_DIR optionally selects the snapshot directory.
set -euo pipefail
export MSYS_NO_PATHCONV=1

[[ $# -ge 4 ]] || { echo 'Usage: compat.sh SERIAL PACKAGE enable|disable|reset CHANGE [CHANGE...]' >&2; exit 2; }
serial="$1"
package="$2"
action="$3"
shift 3
[[ "$serial" =~ ^[[:alnum:]_.:-]+$ ]] || { echo 'Invalid device serial' >&2; exit 2; }
[[ "$package" =~ ^[[:alpha:]_][[:alnum:]_]*(\.[[:alpha:]_][[:alnum:]_]*)+$ ]] || { echo 'Invalid package name' >&2; exit 2; }
case "$action" in enable|disable|reset) ;; *) echo "Invalid action: $action" >&2; exit 2 ;; esac
for change in "$@"; do
    [[ "$change" =~ ^[[:alnum:]_]+$ ]] || { echo "Invalid change: $change" >&2; exit 2; }
done

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ -n "${SDK37_EVIDENCE_DIR:-}" ]]; then
    output="$(bash "$script_dir/dump_compat.sh" "$serial" "$SDK37_EVIDENCE_DIR")"
else
    output="$(bash "$script_dir/dump_compat.sh" "$serial")"
fi
adb_device=("${ADB:-adb}" -s "$serial")
"${adb_device[@]}" shell dumpsys package "$package" > "$output/package.txt"
grep -Fq "Package [$package]" "$output/package.txt" || { echo "Package not installed: $package" >&2; exit 1; }
grep -q 'DEBUGGABLE' "$output/package.txt" || { echo 'Use a debuggable APK for compatibility experiments' >&2; exit 1; }

# Validate the complete batch before applying its first change.
for change in "$@"; do
    if [[ "$change" =~ ^[0-9]+$ ]]; then
        grep -Fq "ChangeId($change;" "$output/platform-compat.txt" || { echo "Unknown change ID: $change" >&2; exit 1; }
    else
        grep -Eq "name=$change[;)]" "$output/platform-compat.txt" || { echo "Unknown change name: $change" >&2; exit 1; }
    fi
done

save_after() {
    "${adb_device[@]}" shell dumpsys platform_compat > "$output/platform-compat-after.txt" || true
}
trap save_after EXIT
printf 'Original state: %s\n' "$output"
for change in "$@"; do
    printf '%s %s %s\n' "$action" "$change" "$package" | tee -a "$output/actions.txt"
    if result="$("${adb_device[@]}" shell am compat "$action" "$change" "$package" 2>&1)"; then
        status=0
    else
        status=$?
    fi
    printf '%s\n' "$result" | tee -a "$output/actions.txt"
    if [[ $status -ne 0 ]] || grep -Eiq '(^Error|Exception|Unknown change|Cannot override)' <<< "$result"; then
        echo "Compatibility command failed; inspect $output before restoring earlier successful changes" >&2
        exit 1
    fi
done
