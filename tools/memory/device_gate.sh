#!/system/bin/sh
# One scenario and mode of the runtime leak gate, run by the device itself so that a dropped adb link does not
# end the test (docs/memory/ROADMAP.md appendix C Q51 (b): the rented SM-A566B drops adb during long commands).
# Afterwards run_gate.py --attach <runId> --attach-dir <dir> writes the usual report from the pulled files
# (tools/memory/README.md).
#
#   adb -s SERIAL push tools/memory/device_gate.sh /data/local/tmp/device_gate.sh
#   adb -s SERIAL shell -T "nohup sh /data/local/tmp/device_gate.sh RUN_ID S4a s04aCaptureScreen normal on >/dev/null 2>&1 &"
#   adb -s SERIAL pull /data/local/tmp/memory-gate-attach LOCAL_DIR
#
# usage: sh device_gate.sh <runId> <scenario id> <test method> <normal|force> [capture on|off, default off]
# The test parameters are the defaults of run_gate.py (warm-ups 2, iterations 20, poll 10000 ms, force delay
# 1000 ms, meminfo phases, analysis on, no hprof). Files under /data/local/tmp/memory-gate-attach, per scenario
# and mode: <runId>-<S>-<mode>.log (this script's lines around the `am instrument -w -r` output), .json (the
# report the test wrote under the host's memory-gate directory), .logcat (AJ6MEM lines), .crash (the crash
# buffer) and .done (written last).
PKG=org.autojs.autojs6
RUN=$1
SC=$2
METHOD=$3
MODE=$4
CAPTURE=${5:-off}
if [ -z "$RUN" ] || [ -z "$SC" ] || [ -z "$METHOD" ] || [ -z "$MODE" ]; then
  echo "usage: sh device_gate.sh <runId> <scenario id> <test method> <normal|force> [capture on|off]"
  exit 2
fi
OUT=/data/local/tmp/memory-gate-attach
mkdir -p "$OUT"
BASE="$OUT/$RUN-$SC-$MODE"
echo "start $(date +%Y-%m-%dT%H:%M:%S) $RUN $SC $METHOD $MODE capture=$CAPTURE tz=$(getprop persist.sys.timezone)" > "$BASE.log"
input keyevent KEYCODE_WAKEUP
am force-stop "$PKG"
logcat -c
if [ "$CAPTURE" = "on" ]; then
  echo "projection_before=$(appops get "$PKG" PROJECT_MEDIA | head -1)" >> "$BASE.log"
  appops set "$PKG" PROJECT_MEDIA allow
fi
am instrument -w -r -e class "org.autojs.autojs.memory.RuntimeLeakGateDeviceTest#$METHOD" -e memoryGateRunId "$RUN" -e memoryGateModes "$MODE" -e memoryGateWarmups 2 -e memoryGateIterations 20 -e memoryGatePollMs 10000 -e memoryGateForceDelayMs 1000 -e memoryGateKeepHprof false -e memoryGateAnalyze true -e memoryGateCapture "$CAPTURE" -e memoryGateAccessibilityWaitMs 0 -e memoryGateMeminfo phases "$PKG.test/androidx.test.runner.AndroidJUnitRunner" >> "$BASE.log" 2>&1
echo "rc=$?" >> "$BASE.log"
if [ "$CAPTURE" = "on" ]; then
  appops set "$PKG" PROJECT_MEDIA default
  echo "projection_after=$(appops get "$PKG" PROJECT_MEDIA | head -1)" >> "$BASE.log"
fi
logcat -d -v brief -s AJ6MEM:I > "$BASE.logcat" 2>&1
logcat -d -b crash -v brief > "$BASE.crash" 2>&1
cp "/sdcard/Android/data/$PKG/files/memory-gate/$RUN-$SC-$MODE.json" "$BASE.json" 2>/dev/null
am force-stop "$PKG"
echo "end $(date +%Y-%m-%dT%H:%M:%S)" >> "$BASE.log"
touch "$BASE.done"
