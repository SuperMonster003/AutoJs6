#!/usr/bin/env bash
# Usage: dump_compat.sh SERIAL [OUTPUT_DIRECTORY]
# Read-only snapshot. ADB can point to an adb executable outside PATH.
set -euo pipefail
export MSYS_NO_PATHCONV=1

serial="${1:?Usage: dump_compat.sh SERIAL [OUTPUT_DIRECTORY]}"
[[ "$serial" =~ ^[[:alnum:]_.:-]+$ ]] || { echo 'Invalid device serial' >&2; exit 2; }
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
safe_serial="${serial//[^[:alnum:]_.-]/_}"
output="${2:-$root/build/sdk37-compat/$safe_serial-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
mkdir -p -- "$(dirname -- "$output")"
mkdir -- "$output" # Refuse to overwrite an earlier snapshot.
adb_device=("${ADB:-adb}" -s "$serial")

"${adb_device[@]}" get-state > "$output/device-state.txt"
"${adb_device[@]}" shell getprop ro.build.fingerprint > "$output/fingerprint.txt"
"${adb_device[@]}" shell getprop ro.build.version.sdk > "$output/api.txt"
"${adb_device[@]}" shell getconf PAGE_SIZE > "$output/page-size.txt"
"${adb_device[@]}" shell dumpsys platform_compat > "$output/platform-compat.txt"
"${adb_device[@]}" shell dumpsys audio > "$output/audio.txt"
"${adb_device[@]}" shell am memory-limiter status > "$output/memory-limiter.txt" 2>&1 || true
printf '%s\n' "$output"
