"""Runs shell_stream_test.js on a device through RunIntentActivity and prints one PASS / FAIL line per check.

Covers the pipe-based Shell (new Shell(options)), shell.spawn / shizuku.spawn, the Auto.js Pro shell options and the
cleanup on script exit. Modes run where available: sh always, adb when Shizuku is operational, root when su is granted.
Results come back through logcat (tag AJ6ShellStream) filtered by a run id; logcat is never cleared.

Usage:
    python -I tools/shell-stream/device_shell_stream_test.py -s SERIAL [--package org.autojs.autojs6] [--touch SECONDS]

--touch also runs the exact getevent command of the original request; someone has to touch the screen when asked.
--touch-only runs just that check, counting from the start.
Exit code: 0 when every check passed.
"""
import argparse
import json
import pathlib
import subprocess
import sys
import time

TAG = "AJ6ShellStream"
HERE = pathlib.Path(__file__).resolve().parent
DEVICE_DIR = "/sdcard/Download/aj6-shell-stream"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"


def adb(serial, *args, check=False):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", check=check)


def run_phase(serial, package, phase, run_id, timeout, touch_seconds=0):
    source = (HERE / "shell_stream_test.js").read_text(encoding="utf-8")
    source = source.replace("__RUN_ID__", run_id).replace("__PHASE__", phase).replace("__TOUCH_SECONDS__", str(touch_seconds))
    local = HERE / f".{phase}.generated.js"
    local.write_bytes(source.encode("utf-8"))
    remote = f"{DEVICE_DIR}/{phase}.js"
    try:
        adb(serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
        adb(serial, "push", str(local), remote, check=True)
    finally:
        local.unlink(missing_ok=True)
    adb(serial, "shell", "am", "start", "-n", f"{package}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)

    deadline = time.monotonic() + timeout
    # Whole logcat lines (with their timestamps) identify what was read: the ring buffer drops old lines on busy
    # devices (Magisk logs every su call), so positions in successive dumps do not line up.
    seen = set()
    events = []
    touch_announced = False
    while time.monotonic() < deadline:
        for line in adb(serial, "logcat", "-d", "-s", f"{TAG}:I").stdout.splitlines():
            if f" {run_id} " not in line + " " or line in seen:
                continue
            seen.add(line)
            payload = line.split(f" {run_id} ", 1)[1].strip()
            if payload == "TOUCH_NOW" and not touch_announced:
                touch_announced = True
                print(f"  >>> 请在 {touch_seconds} 秒内在设备上多次触摸屏幕 (测试 getevent 实时输出)", flush=True)
                continue
            events.append(payload)
        if events and events[-1] == "DONE":
            return events[:-1], True
        time.sleep(1)
    return events, False


def summarize(phase, events, finished):
    passed = failed = 0
    reported = None
    for payload in events:
        try:
            item = json.loads(payload)
        except json.JSONDecodeError:
            print(f"  ?    {payload}")
            continue
        if item.get("info") == "reported":
            reported = item.get("value")
            continue
        if "info" in item:
            print(f"  info {item['info']}={item.get('value')}")
            continue
        ok = item.get("pass")
        passed += ok
        failed += not ok
        print(f"  {'PASS' if ok else 'FAIL'} {item['name']} {item.get('detail', '')}".rstrip())
    if not finished:
        failed += 1
        print(f"  FAIL {phase}: no DONE within the timeout")
    elif reported is not None and reported != passed + failed:
        failed += 1
        print(f"  FAIL {phase}: the device reported {reported} results, {passed + failed} were read from logcat")
    return passed, failed


def leftover_processes(serial, markers):
    listing = adb(serial, "shell", "ps -A -o ARGS").stdout
    return [marker for marker in markers if marker in listing]


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser()
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--package", default="org.autojs.autojs6")
    parser.add_argument("--touch", type=int, default=0, metavar="SECONDS")
    parser.add_argument("--touch-only", type=int, default=0, metavar="SECONDS", help="run only the getevent check")
    args = parser.parse_args()

    run_id = f"r{int(time.time())}"
    total_passed = total_failed = 0

    if args.touch_only:
        print(f"[touch] run {run_id}")
        events, finished = run_phase(args.serial, args.package, "touch", run_id, args.touch_only + 30, args.touch_only)
        p, f = summarize("touch", events, finished)
        print(f"passed={p} failed={f}")
        return 0 if f == 0 else 1

    print(f"[main] run {run_id}")
    # A slow root device (Magisk logs every su call) needs about 5 minutes.
    events, finished = run_phase(args.serial, args.package, "main", run_id, 480 + args.touch, args.touch)
    p, f = summarize("main", events, finished)
    total_passed += p
    total_failed += f

    print("[keepalive]")
    events, finished = run_phase(args.serial, args.package, "keepalive", run_id + "k", 30)
    p, f = summarize("keepalive", events, finished)
    total_passed += p
    total_failed += f

    print("[cleanup]")
    events, finished = run_phase(args.serial, args.package, "cleanup", run_id + "c", 30)
    p, f = summarize("cleanup", events, finished)
    total_passed += p
    total_failed += f
    markers = []
    for payload in events:
        if payload.startswith("{") and json.loads(payload).get("info") == "markers":
            markers = json.loads(payload)["value"].split(",")
    left = ["(no markers reported)"]
    if markers:
        # Root processes are ended by another root shell, which takes a moment to start.
        for _ in range(8):
            time.sleep(1)
            left = leftover_processes(args.serial, markers)
            if not left:
                break
    ok = finished and bool(markers) and not left
    print(f"  {'PASS' if ok else 'FAIL'} cleanup.onScriptExit markers={len(markers)} left={','.join(left) or '-'}")
    total_passed += ok
    total_failed += not ok

    print(f"passed={total_passed} failed={total_failed}")
    return 0 if total_failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
