#!/usr/bin/env bash
# Usage: run-history-smoke.sh <serial> [wait-seconds]
set -u
export MSYS_NO_PATHCONV=1
SERIAL="$1"
WAIT="${2:-40}"
DIR="$(cd "$(dirname "$0")" && pwd -W)"
adb -s "$SERIAL" shell rm -f /sdcard/history-smoke-result.txt
adb -s "$SERIAL" push "$DIR/history-smoke.js" /sdcard/history-smoke.js >/dev/null
adb -s "$SERIAL" push "$DIR/history-smoke-runner.js" /sdcard/history-smoke-runner.js >/dev/null
adb -s "$SERIAL" shell am start -n org.autojs.autojs6/org.autojs.autojs.external.open.RunIntentActivity --es path /sdcard/history-smoke-runner.js >/dev/null
for i in $(seq 1 "$WAIT"); do
  sleep 1
  if adb -s "$SERIAL" shell cat /sdcard/history-smoke-result.txt 2>/dev/null | grep -q "^DONE"; then
    break
  fi
done
echo "==== result ($SERIAL) ===="
adb -s "$SERIAL" shell cat /sdcard/history-smoke-result.txt 2>/dev/null || echo "(no result file)"
