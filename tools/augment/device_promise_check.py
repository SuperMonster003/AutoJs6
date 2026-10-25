"""ROADMAP P4.2 / Q95: the global Promise on a device (PromiseConstructor; promise.js with the switch off until P5.2).

    py -3.12 -I tools/augment/device_promise_check.py -s SERIAL [--sample] [--scenarios] [--bench]

--sample runs the app sample "协程/协程HelloWorld" (app/src/main/assets-app/sample) as a project, as the app runs it,
and checks its console lines in order ("Hello, Continuation!", "1 秒后...", "2 秒后...", "1 + 2 = 3", the file read);
--scenarios runs bench/promise_smoke/scenarios (a project with the continuation feature: order of reactions against the
timers, thenable adoption, await in its forms, callbacks settled on other threads, Flow await(), wait(), the unhandled
rejection warning) and prints its "AJ6P42" lines; --bench runs bench/promise_smoke/bench (a project with the continuation
feature: queued reactions are awaited, so the looper runs them; "queued+wait" runs them by one wait(), Q105) and prints
microseconds per operation. The rejected await of --scenarios shows an error toast (existing
behavior of popException); no tap is needed.

Scripts start through RunIntentActivity, results are read from the GlobalConsole lines of logcat (those logged since
each script started, as logcat -c does not always clear the buffer). The device directory /sdcard/Download/aj6-augment-bench/p42 is removed afterwards.
"""

import argparse
import pathlib
import re
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import device_bench  # noqa: E402
from device_bench import adb  # noqa: E402

HERE = pathlib.Path(__file__).resolve().parent
HOST = HERE.parents[1]
SMOKE = HERE / "bench" / "promise_smoke"
SAMPLE = HOST / "app" / "src" / "main" / "assets-app" / "sample" / "协程" / "协程HelloWorld"
DEVICE_DIR = "/sdcard/Download/aj6-augment-bench/p42"
LAUNCHER = HERE / "bench" / "bootstrap_smoke" / "launch_project.js"

SAMPLE_LINES = ["Hello, Continuation!", "1 秒后...", "2 秒后...", "1 + 2 = 3", "读取文件 hello.txt: Nothing"]


def push_dir(serial, local, remote):
    adb(serial, "shell", "mkdir", "-p", remote, check=True)
    for file in local.iterdir():
        adb(serial, "push", str(file), f"{remote}/{file.name}", check=True)


def launch_project(serial, project_dir, name):
    launcher = LAUNCHER.read_text(encoding="utf-8").replace("PROJECT_DIR", project_dir)
    local = HERE / "__pycache__" / f"p42_launch_{name}.js"
    local.parent.mkdir(exist_ok=True)
    local.write_text(launcher, encoding="utf-8")
    remote = f"{DEVICE_DIR}/launch_{name}.js"
    adb(serial, "push", str(local), remote, check=True)
    return remote


def console_lines_since(serial, since):
    """The GlobalConsole lines logged since the device time [since] (MM-DD hh:mm:ss.mmm, as logcat -t takes it)."""
    out = adb(serial, "shell", f"logcat -d -t '{since}'").stdout
    return [re.sub(r"^.*GlobalConsole: ", "", l).rstrip() for l in out.splitlines() if "GlobalConsole:" in l]


def watch(serial, remote, done, wait):
    """Starts [remote]; returns its GlobalConsole lines once [done](lines) holds or after [wait] s.

    logcat -c does not always clear the buffer (G8441, 2026-10-24: the lines of the previous run were still there, so
    the end line of that run ended the watch at once), so only the lines logged since the start are read: after a second
    without output of this tool, the device time rounded down to the second is later than any earlier line.
    """
    device_bench.clear_log(serial)
    time.sleep(1.0)
    since = adb(serial, "shell", "date '+%m-%d %H:%M:%S.000'").stdout.strip()
    adb(serial, "shell", "am", "start", "-n", f"{device_bench.PACKAGE}/{device_bench.RUN_ACTIVITY}", "-e", "path", remote, check=True)
    deadline = time.time() + wait
    lines = []
    while time.time() < deadline:
        time.sleep(0.5)
        lines = console_lines_since(serial, since)
        if done(lines):
            return lines, True
    return lines, False


def sample(args):
    remote_dir = f"{DEVICE_DIR}/helloworld"
    push_dir(args.serial, SAMPLE, remote_dir)
    remote = launch_project(args.serial, remote_dir, "helloworld")
    t0 = time.time()
    lines, done = watch(args.serial, remote, lambda ls: any(SAMPLE_LINES[-1] in l for l in ls), args.wait)
    elapsed = time.time() - t0
    found = [next((l for l in lines if want in l), None) for want in SAMPLE_LINES]
    order = [lines.index(l) for l in found if l is not None]
    ok = done and all(found) and order == sorted(order)
    errors = [l for l in lines if "Error" in l or "Exception" in l]
    print(f"sample helloworld: {'PASS' if ok else 'FAIL'} ({sum(1 for f in found if f)}/{len(SAMPLE_LINES)} lines in order, {elapsed:.1f} s)")
    for want, line in zip(SAMPLE_LINES, found):
        if line is None:
            print(f"  missing line: {want}")
    for line in errors[:3]:
        print(f"  error line: {line[:160]}")
    return ok


def scenarios(args):
    remote_dir = f"{DEVICE_DIR}/scenarios"
    push_dir(args.serial, SMOKE / "scenarios", remote_dir)
    remote = launch_project(args.serial, remote_dir, "scenarios")
    lines, done = watch(args.serial, remote, lambda ls: any("AJ6P42 done" in l for l in ls), args.wait)
    for line in lines:
        start = line.find("AJ6P42 ")
        if start >= 0 and "AJ6P42 done" not in line:
            print("scenario " + line[start + 7:])
    unhandled = any("Possible Unhandled Promise Rejection" in l and "aj6p42 unhandled" in l for l in lines)
    print(f"scenario B5-unhandled-warning {unhandled}")
    if not done:
        print(f"scenarios: no end line within {args.wait} s")
        for line in lines[-5:]:
            print(f"  last: {line[:160]}")
    return done


def bench(args):
    remote_dir = f"{DEVICE_DIR}/bench"
    push_dir(args.serial, SMOKE / "bench", remote_dir)
    remote = launch_project(args.serial, remote_dir, "bench")
    lines, done = watch(args.serial, remote, lambda ls: any("AJ6P42B done" in l for l in ls), args.wait)
    for line in lines:
        start = line.find("AJ6P42B ")
        if start >= 0 and "AJ6P42B done" not in line:
            print("bench " + line[start + 8:] + " us")
    if not done:
        print(f"bench: no end line within {args.wait} s")
    return done


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--sample", action="store_true")
    parser.add_argument("--scenarios", action="store_true")
    parser.add_argument("--bench", action="store_true")
    parser.add_argument("--wait", type=int, default=90, help="seconds to wait for a script")
    args = parser.parse_args()
    ok = True
    try:
        if args.sample:
            ok = sample(args) and ok
        if args.scenarios:
            ok = scenarios(args) and ok
        if args.bench:
            ok = bench(args) and ok
    finally:
        adb(args.serial, "shell", "rm", "-rf", DEVICE_DIR)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
