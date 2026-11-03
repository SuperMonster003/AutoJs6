"""images.requestScreenCapture / captureScreen on a device. Android 14+ allows one VirtualDisplay per consent token, a second
createVirtualDisplay throws "SecurityException: Don't re-use the resultData ..." and stops the projection.

    py -3.12 -I tools/screen-capture/device_capture_check.py -s SERIAL [--orientation auto|portrait|landscape]
        [--rotate] [--second-display] [--grant] [--max-static-ms 700]

static: consecutive captures of a still screen (the Settings app); fails when a capture throws or takes longer than
--max-static-ms (the first one, which waits for the consent dialog to fade, is not timed).
--rotate: keeps capturing while the display turns to landscape and back (auto-rotate off, user_rotation, both restored);
fails when the last frames before each turn do not have the display size or are letterboxed.
--second-display: creates a second VirtualDisplay on the projection of the capturer, as 6.7.0 did after a capture timed out;
on Android 14+ it must be rejected, the capturer must be released, and the next captureScreen must request consent again.
--grant: allows PROJECT_MEDIA, SYSTEM_ALERT_WINDOW and MANAGE_EXTERNAL_STORAGE with appops so the consent dialog is
skipped (emulators only, PROJECT_MEDIA is reset afterwards). On a phone the consent dialog is confirmed by hand.

The probe starts through RunIntentActivity; results are the "AJ6CAP" lines of the GlobalConsole logcat tag. The device
directory is removed afterwards.
"""

import argparse
import json
import pathlib
import re
import statistics
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
DEVICE_DIR = "/sdcard/Download/aj6-capture-check"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
SAMPLE_RE = re.compile(r"^(\S+) frame=(\d+)x(\d+) display=(\d+)x(\d+) ms=(\d+) letterbox=(\w+)$")


def adb(serial, *args, check=False):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check)


def shell(serial, command):
    return adb(serial, "shell", command).stdout.strip()


def probe_lines_since(serial, since):
    out = adb(serial, "shell", f"logcat -d -v raw -s GlobalConsole -T '{since}'").stdout
    return [l.split("AJ6CAP ", 1)[1].rstrip() for l in out.splitlines() if "AJ6CAP " in l]


def run_probe(serial, args):
    check = {
        "orientation": args.orientation,
        "statics": args.statics,
        "rotate": args.rotate,
        "rotateMs": args.rotate_ms,
        "secondDisplay": args.second_display,
    }
    local = HERE / "__pycache__" / "capture_probe_run.js"
    local.parent.mkdir(exist_ok=True)
    local.write_text(f"var CHECK = {json.dumps(check)};\n" + (HERE / "capture_probe.js").read_text(encoding="utf-8"),
                     encoding="utf-8")
    remote = f"{DEVICE_DIR}/capture_probe.js"
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    adb(serial, "push", str(local), remote, check=True)
    local.unlink()

    # A still page that follows user_rotation.
    adb(serial, "shell", "am", "start", "-W", "-a", "android.settings.SETTINGS")
    time.sleep(2)
    since = shell(serial, "date +'%m-%d %H:%M:%S.000'")
    adb(serial, "shell", "am", "start", "-n", f"{args.package}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)
    if not args.grant:
        print("waiting for the screen capture consent on the device (choose the entire screen)")

    lines = []
    rotations = []
    deadline = time.time() + args.timeout
    while time.time() < deadline:
        time.sleep(0.5)
        lines = probe_lines_since(serial, since)
        if "rotate-ready" in lines and not rotations:
            rotations = rotate(serial, args.rotate_ms)
        if "end" in lines:
            break
    return lines


def rotate(serial, rotate_ms):
    """Turns to landscape and back in [rotate_ms] while the probe keeps capturing; returns the rotations applied."""
    saved = (shell(serial, "settings get system accelerometer_rotation"), shell(serial, "settings get system user_rotation"))
    try:
        shell(serial, "settings put system accelerometer_rotation 0")
        steps = ["1", "0"]
        for step in steps:
            time.sleep(rotate_ms / 1000 / (len(steps) + 1))
            shell(serial, f"settings put system user_rotation {step}")
        return steps
    finally:
        for key, value in zip(("accelerometer_rotation", "user_rotation"), saved):
            if value and value != "null":
                shell(serial, f"settings put system {key} {value}")


def samples(lines, prefix):
    found = []
    for line in lines:
        m = SAMPLE_RE.match(line)
        if m and m.group(1).startswith(prefix):
            found.append((m.group(1), (int(m.group(2)), int(m.group(3))), (int(m.group(4)), int(m.group(5))),
                          int(m.group(6)), m.group(7)))
    return found


def evaluate(lines, args, sdk):
    results = []
    errors = [l for l in lines if l.startswith("error ")]
    results.append(("no exception", not errors, errors[0] if errors else "none"))
    results.append(("consent granted", "request true" in lines, next((l for l in lines if l.startswith("request")), "no line")))

    static = samples(lines, "static-")
    if args.orientation == "auto":
        bad = [s for s in static if s[1] != s[2] or s[4] == "yes"]
        results.append(("still screen frame size", bool(static) and not bad,
                        f"{static[0][1][0]}x{static[0][1][1]} on {static[0][2][0]}x{static[0][2][1]}"
                        + (f", letterbox {static[0][4]}" if static else "") if static else "no capture"))
    timed = [s[3] for s in static[1:]]
    if timed:
        ok = max(timed) <= args.max_static_ms and len(static) == args.statics
        results.append(("still screen latency", ok,
                        f"{len(static)} captures, median {statistics.median(timed):.0f} ms, max {max(timed)} ms"))
    else:
        results.append(("still screen latency", False, f"{len(static)} captures"))

    if args.rotate:
        groups = []
        for sample in samples(lines, "rotating-"):
            if not groups or groups[-1][0] != sample[2]:
                groups.append((sample[2], []))
            groups[-1][1].append(sample)
        settled = [g for g in groups if len(g[1]) >= 3]
        bad = [f"{g[0][0]}x{g[0][1]}: {s[1][0]}x{s[1][1]} letterbox={s[4]}"
               for g in settled for s in g[1][-2:] if s[1] != g[0] or s[4] == "yes"]
        ok = len(settled) >= 3 and not bad
        slowest = max((s[3] for g in groups for s in g[1]), default=0)
        detail = " -> ".join(f"{g[0][0]}x{g[0][1]}" for g in settled) + f", slowest {slowest} ms"
        results.append(("rotation", ok, detail + (f", mismatched {bad[0]}" if bad else "")))

    if args.second_display:
        line = next((l for l in lines if l.startswith("second-display ")), "no line")
        after = next((l for l in lines if l.startswith("capturer-after ")), "no line")
        recovered = samples(lines, "recovered")
        if sdk >= 34:
            ok = "SecurityException" in line and after != "capturer-after active" and bool(recovered)
        else:
            ok = line == "second-display created" and bool(recovered)
        results.append(("second display then recovery", ok,
                        f"{line.split(':')[0]}; {after}; recovered {'yes' if recovered else 'no'}"))
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--package", default="org.autojs.autojs6")
    parser.add_argument("--orientation", default="auto", choices=["auto", "portrait", "landscape"])
    parser.add_argument("--statics", type=int, default=6)
    parser.add_argument("--max-static-ms", type=int, default=700)
    parser.add_argument("--rotate", action="store_true")
    parser.add_argument("--rotate-ms", type=int, default=12000)
    parser.add_argument("--second-display", action="store_true")
    parser.add_argument("--grant", action="store_true")
    parser.add_argument("--timeout", type=int, default=150)
    parser.add_argument("--verbose", action="store_true", help="also print the probe lines")
    args = parser.parse_args()

    serial = args.serial
    sdk = int(shell(serial, "getprop ro.build.version.sdk") or 0)
    emulator = shell(serial, "getprop ro.kernel.qemu") == "1" or serial.startswith("emulator-")
    if args.grant and not emulator:
        sys.exit("--grant is for emulators; confirm the consent dialog on a phone instead")
    if args.grant:
        for op in ("PROJECT_MEDIA", "SYSTEM_ALERT_WINDOW", "MANAGE_EXTERNAL_STORAGE"):
            shell(serial, f"appops set {args.package} {op} allow")
    try:
        lines = run_probe(serial, args)
    finally:
        adb(serial, "shell", "rm", "-rf", DEVICE_DIR)
        if args.grant:
            shell(serial, f"appops set {args.package} PROJECT_MEDIA default")

    print(f"{serial} (API {sdk}), orientation {args.orientation}")
    if args.verbose:
        for line in lines:
            print(f"    {line}")
    results = evaluate(lines, args, sdk)
    for name, ok, detail in results:
        print(f"  {'PASS' if ok else 'FAIL'}  {name}: {detail}")
    if "end" not in lines:
        print("  FAIL  probe did not finish in time")
    sys.exit(0 if all(ok for _, ok, _ in results) and "end" in lines else 1)


if __name__ == "__main__":
    main()
