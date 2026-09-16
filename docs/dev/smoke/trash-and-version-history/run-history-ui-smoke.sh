#!/usr/bin/env bash
# Usage: run-history-ui-smoke.sh <serial>
set -u
export MSYS_NO_PATHCONV=1
SERIAL="$1"
DIR="$(cd "$(dirname "$0")" && pwd -W)"
SHOTS="$DIR/shots"
mkdir -p "$SHOTS"
adb -s "$SERIAL" shell rm -f /sdcard/history-ui-smoke-result.txt
adb -s "$SERIAL" push "$DIR/history-ui-smoke.js" /sdcard/history-ui-smoke.js >/dev/null
adb -s "$SERIAL" shell am start -n org.autojs.autojs6/org.autojs.autojs.external.open.RunIntentActivity --es path /sdcard/history-ui-smoke.js >/dev/null

wait_marker() {
  local marker="$1"
  for i in $(seq 1 60); do
    if adb -s "$SERIAL" shell cat /sdcard/history-ui-smoke-result.txt 2>/dev/null | grep -q "^$marker"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

if wait_marker TRASH_SHOWN; then
  sleep 1
  adb -s "$SERIAL" exec-out screencap -p > "$SHOTS/trash-$SERIAL.png"
  echo "trash shot: $SHOTS/trash-$SERIAL.png"
fi
if wait_marker VH_SHOWN; then
  sleep 1
  adb -s "$SERIAL" exec-out screencap -p > "$SHOTS/version-history-$SERIAL.png"
  echo "version history shot: $SHOTS/version-history-$SERIAL.png"
fi
wait_marker DONE
echo "==== result ($SERIAL) ===="
adb -s "$SERIAL" shell cat /sdcard/history-ui-smoke-result.txt 2>/dev/null || echo "(no result file)"
# Leave the pages: back out twice.
adb -s "$SERIAL" shell input keyevent BACK
sleep 1
adb -s "$SERIAL" shell input keyevent BACK
