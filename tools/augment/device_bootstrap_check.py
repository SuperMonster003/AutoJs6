"""ROADMAP P2.5: probe and smoke tests of the startup objects on a device.

    py -3.12 -I tools/augment/device_bootstrap_check.py -s SERIAL --probe [--save FILE] [--compare FILE]
    py -3.12 -I tools/augment/device_bootstrap_check.py -s SERIAL --smoke coroutine images automator ui

--probe runs bench/bootstrap_probe.js (surface and behavior of __exitIfError__, __asGlobal__, continuation.await /
delay, ResultAdapter and the lazily installed polyfills) and prints one line per item; --save writes the items to a JSON
file, --compare prints the items that differ from a saved run (for example the build before P2.5).

--smoke runs bench/bootstrap_smoke: "coroutine" and "ui" are projects with the continuation feature, started through
ProjectLauncher as the app starts a project; "ui" shows dialogs.confirm and waits for a tap on the phone (up to
--ui-wait seconds); "images" runs images.loadAsync (ResultAdapter.promise); "automator" runs automator.captureScreen
(ResultAdapter.wait), skipped when the AutoJs6 accessibility service is off. Only the marked console lines are printed.

Scripts start through RunIntentActivity; results are read from the GlobalConsole lines of logcat written since the
start (logcat is never cleared). The device directory /sdcard/Download/aj6-augment-bench/p25 is removed afterwards.
"""

import argparse
import json
import pathlib
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
BENCH = HERE / "bench"
PACKAGE = "org.autojs.autojs6"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
DEVICE_DIR = "/sdcard/Download/aj6-augment-bench/p25"


def adb(serial, *args, check=False):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check)


def device_epoch(serial):
    return adb(serial, "shell", "date", "+%s", check=True).stdout.strip()


def console_since(serial, epoch):
    out = adb(serial, "logcat", "-d", "-v", "epoch", "-T", f"{epoch}.000", "-s", "GlobalConsole").stdout
    return [line.split("GlobalConsole: ", 1)[1].rstrip() for line in out.splitlines() if "GlobalConsole: " in line]


def run(serial, remote, mark, wait, notice=None):
    """Starts [remote] and returns the "<mark> ..." console lines (without the mark) until "<mark> done" or [wait]."""
    epoch = device_epoch(serial)
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)
    if notice:
        print(notice, flush=True)
    deadline = time.time() + wait
    lines = []
    while time.time() < deadline:
        time.sleep(0.5)
        lines = []
        for line in console_since(serial, epoch):
            start = line.find(mark + " ")
            if start >= 0:
                lines.append(line[start + len(mark) + 1:])
        if "done" in lines:
            return [l for l in lines if l != "done"], True
    return lines, False


def push(serial, local, remote):
    adb(serial, "shell", "mkdir", "-p", remote.rsplit("/", 1)[0], check=True)
    adb(serial, "push", str(local), remote, check=True)


def probe(args):
    remote = f"{DEVICE_DIR}/bootstrap_probe.js"
    push(args.serial, BENCH / "bootstrap_probe.js", remote)
    lines, done = run(args.serial, remote, "AJ6P25", args.wait)
    items = {}
    for line in lines:
        name, _, value = line.partition(" ")
        items[name] = value
    for name, value in items.items():
        print(f"{name}: {value}")
    if not done:
        print(f"probe: no end line within {args.wait} s ({len(items)} items)")
    if args.save:
        pathlib.Path(args.save).write_text(json.dumps(items, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    if args.compare:
        before = json.loads(pathlib.Path(args.compare).read_text(encoding="utf-8"))
        names = list(dict.fromkeys(list(before) + list(items)))
        changed = [n for n in names if before.get(n) != items.get(n)]
        print(f"compare: {len(names) - len(changed)} same, {len(changed)} different")
        for n in changed:
            print(f"  {n}\n    before {before.get(n)}\n    now    {items.get(n)}")
    return done


def accessibility_on(serial):
    enabled = adb(serial, "shell", "settings", "get", "secure", "enabled_accessibility_services").stdout
    return f"{PACKAGE}/" in enabled


def smoke(args, name):
    root = BENCH / "bootstrap_smoke"
    if name in ("coroutine", "ui"):
        for file in (root / name).iterdir():
            push(args.serial, file, f"{DEVICE_DIR}/{name}/{file.name}")
        launcher = (root / "launch_project.js").read_text(encoding="utf-8").replace("PROJECT_DIR", f"{DEVICE_DIR}/{name}")
        local = HERE / "__pycache__" / f"p25_launch_{name}.js"
        local.parent.mkdir(exist_ok=True)
        local.write_text(launcher, encoding="utf-8")
        remote = f"{DEVICE_DIR}/launch_{name}.js"
        push(args.serial, local, remote)
    else:
        remote = f"{DEVICE_DIR}/{name}.js"
        push(args.serial, root / f"{name}.js", remote)
    if name == "automator" and not accessibility_on(args.serial):
        print("automator: skipped, the AutoJs6 accessibility service is off")
        return False
    mark = {"coroutine": "AJ6P25C", "ui": "AJ6P25U", "images": "AJ6P25I", "automator": "AJ6P25A"}[name]
    wait = args.ui_wait if name == "ui" else args.wait
    notice = "ui: tap OK or CANCEL on the AutoJs6 dialog on the phone" if name == "ui" else None
    lines, done = run(args.serial, remote, mark, wait, notice)
    for line in lines:
        print(f"{name}: {line}")
    if not done:
        print(f"{name}: no end line within {wait} s")
    return done


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--probe", action="store_true")
    parser.add_argument("--save", help="write the probe items to this JSON file")
    parser.add_argument("--compare", help="compare the probe items with this JSON file")
    parser.add_argument("--smoke", nargs="*", default=[], choices=["coroutine", "ui", "images", "automator"])
    parser.add_argument("--wait", type=int, default=60, help="seconds to wait for a script")
    parser.add_argument("--ui-wait", type=int, default=180, help="seconds to wait for the tap in the ui smoke")
    args = parser.parse_args()
    ok = True
    try:
        if args.probe:
            ok = probe(args) and ok
        for name in args.smoke:
            ok = smoke(args, name) and ok
    finally:
        adb(args.serial, "shell", "rm", "-rf", DEVICE_DIR)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
