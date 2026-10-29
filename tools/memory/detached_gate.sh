#!/bin/bash
# One scenario and mode of the runtime leak gate on a device whose adb proxy closes long or script-running shell
# commands (docs/memory/ROADMAP.md appendix C Q51 (b): the rented SM-A566B through Samsung RDB; tools/memory/device_gate.sh
# cannot even be started there). Every adb call is one short command, `am instrument` runs without -w, so the test
# keeps running on the device when the link drops. Afterwards run_gate.py --attach <runId> --attach-dir <dir> writes the
# usual report from the collected files; the test outcome comes from the TestRunner logcat lines (tools/memory/README.md).
#
#   tools/memory/detached_gate.sh launch  SERIAL DIR RUN_ID S4a s04aCaptureScreen normal on
#   tools/memory/detached_gate.sh collect SERIAL DIR RUN_ID S4a normal [wait seconds, default 1800; S4f needs about 15 min per mode]
#
# Files in DIR per scenario and mode, named like device_gate.sh's: <runId>-<S>-<mode>.log (this script's lines, host
# clock, `launch=detached` on the start line, `end` written by collect), .json (the report the test wrote, pulled from
# the host's memory-gate directory), .logcat (AJ6MEM and TestRunner lines), .crash (the crash buffer) and .done.
export MSYS_NO_PATHCONV=1
CMD=$1; S=$2; DIR=$3; RUN=$4; SC=$5
PKG=org.autojs.autojs6
RUNNER="$PKG.test/androidx.test.runner.AndroidJUnitRunner"
cr=$(printf '\r')
dsh() { adb -s "$S" shell "$@" | tr -d "$cr"; }
now() { date +%Y-%m-%dT%H:%M:%S; }
case "$CMD" in
  launch)
    METHOD=$6; MODE=$7; CAPTURE=${8:-off}
    if [ -z "$RUN" ] || [ -z "$SC" ] || [ -z "$METHOD" ] || [ -z "$MODE" ]; then
      echo "usage: detached_gate.sh launch SERIAL DIR RUN_ID S4a s04aCaptureScreen normal [capture on|off]" >&2
      exit 2
    fi
    mkdir -p "$DIR"
    LOG="$DIR/$RUN-$SC-$MODE.log"
    {
      echo "start $(now) $RUN $SC $METHOD $MODE capture=$CAPTURE host-clock launch=detached"
      echo "device_time=$(dsh date +%Y-%m-%dT%H:%M:%S) tz=$(dsh getprop persist.sys.timezone)"
    } > "$LOG"
    dsh am force-stop $PKG
    dsh input keyevent KEYCODE_WAKEUP
    dsh logcat -c
    if [ "$CAPTURE" = on ]; then
      echo "projection_before=$(dsh appops get $PKG PROJECT_MEDIA | head -1)" >> "$LOG"
      dsh appops set $PKG PROJECT_MEDIA allow
      echo "projection_set=$(dsh appops get $PKG PROJECT_MEDIA | head -1)" >> "$LOG"
    fi
    adb -s "$S" shell am instrument -e class "org.autojs.autojs.memory.RuntimeLeakGateDeviceTest#$METHOD" \
      -e memoryGateRunId "$RUN" -e memoryGateModes "$MODE" -e memoryGateWarmups 2 -e memoryGateIterations 20 \
      -e memoryGatePollMs 10000 -e memoryGateForceDelayMs 1000 -e memoryGateKeepHprof false -e memoryGateAnalyze true \
      -e memoryGateCapture "$CAPTURE" -e memoryGateAccessibilityWaitMs 0 -e memoryGateMeminfo phases "$RUNNER" >> "$LOG" 2>&1
    echo "am_rc=$?" >> "$LOG"
    echo "launched $(now)" >> "$LOG"
    cat "$LOG"
    ;;
  collect)
    MODE=$6; WAIT=${7:-1800}
    LOG="$DIR/$RUN-$SC-$MODE.log"
    BASE="$DIR/$RUN-$SC-$MODE"
    if [ ! -f "$LOG" ]; then echo "no $LOG: launch first" >&2; exit 2; fi
    JSON="/sdcard/Android/data/$PKG/files/memory-gate/$RUN-$SC-$MODE.json"
    deadline=$(( $(date +%s) + WAIT ))
    while :; do
      state=$(dsh logcat -d -v brief -s TestRunner:I 2>/dev/null | grep -c "run finished\|Process crashed")
      if [ "${state:-0}" -gt 0 ]; then break; fi
      if [ "$(date +%s)" -ge "$deadline" ]; then
        # Leave the device alone: the test may still be running, collect again later with a longer wait.
        echo "collect: no TestRunner run finished line within $WAIT s; nothing collected, the test was not stopped" >&2
        exit 3
      fi
      sleep 20
    done
    sleep 2
    dsh logcat -d -v brief -s AJ6MEM:I TestRunner:I > "$BASE.logcat"
    dsh logcat -d -b crash -v brief > "$BASE.crash"
    adb -s "$S" pull "$JSON" "$BASE.json" > /dev/null 2>&1 && echo "json_pulled=$(stat -c %s "$BASE.json")" >> "$LOG" || echo "json_pulled=none" >> "$LOG"
    CAPTURE=$(grep -o "capture=[a-z]*" "$LOG" | head -1 | cut -d= -f2)
    if [ "$CAPTURE" = on ]; then
      dsh appops set $PKG PROJECT_MEDIA default
      echo "projection_after=$(dsh appops get $PKG PROJECT_MEDIA | head -1)" >> "$LOG"
    fi
    dsh am force-stop $PKG
    echo "end $(now)" >> "$LOG"
    touch "$BASE.done"
    grep -c "" "$BASE.logcat" | sed 's/^/logcat_lines=/'
    grep "TestRunner" "$BASE.logcat" | grep -v "chunk" | tail -4
    tail -3 "$LOG"
    ;;
  *)
    echo "usage: detached_gate.sh launch|collect ..." >&2
    exit 2
    ;;
esac
