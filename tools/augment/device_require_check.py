"""ROADMAP P3.7: smoke of require on a device, with the switch of the new script module layer off and then on.

    py -3.12 -I tools/augment/device_require_check.py -s SERIAL

Pushes bench/require_smoke to /sdcard/Download/aj6-augment-bench/p37 and, in each state, runs main.js (file modules,
JSON, packages, index.js, node_modules found upwards, a cycle, the three files of issue #564, core names, axios, errors,
runMain) and project/ (a project with the continuation feature whose modules suspend at their top level, started through
ProjectLauncher as the app starts a project). The "AJ6REQ <item> <value>" lines of the two runs are compared: an item
outside REGISTERED must print the same value in both states (and the value of EXPECTED where given); a registered item
(appendix E "P3.7") must differ, and its value with the switch on must contain every text listed for it. Prints one line
per item and PASS / FAIL. The switch is restored to its previous value and the device directory is removed afterwards.
Each run reads the GlobalConsole lines logged since its start second (logcat -T), as device_bootstrap_check.py does:
`logcat -c` does not reliably drop the previous run's lines on every device.
"""
import argparse
import pathlib
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import device_bench as db  # noqa: E402

MARK = "AJ6REQ"
LOCAL = db.HERE / "bench" / "require_smoke"
REMOTE = f"{db.DEVICE_DIR}/p37"

# Values that must be printed in both states.
EXPECTED = {
    "file": '["a","<root>/lib/a.js","<root>/lib",true,"undefined"]',
    "json": '{"x":1,"exports":2}',
    "package-main": '"pkg entry"',
    "index": '"idx index"',
    "node-modules-up": '"nm part"',
    "cycle": '{"loaded":true,"bSawLoaded":false}',
    "issue-564": '["a [x]","b [y]!","function","function"]',
    "axios": "true",
    "parse-error": '["SyntaxError",true,null]',
    "cont-modules": '["inner a / b",true,true]',
}

# Registered differences: the texts the value with the switch on must contain (appendix E "P3.7", Q81 fixes and Q83).
REGISTERED = {
    "missing": ["throws Error [Cannot find module './nope'", "code=MODULE_NOT_FOUND"],
    "missing-bare": ["throws Error [Cannot find module 'aj6-nope-pkg'", "code=MODULE_NOT_FOUND"],
    "run-main-missing": ["throws Error [Cannot find module './nope'", "code=MODULE_NOT_FOUND"],
    "module-error": ['["TypeError","<root>/lib/throws.js",2]'],
    "bad-arg": ['throws TypeError [Failed to call method "require": [ Argument "id" must be of type string, got number ]]'],
    "empty": ["{}"],
    "upper-ext": ['"upper"'],
    "package-error": ['["Error","Cannot load JSON file","PARSE_ERROR","[object Error]"]'],
}


def console_since(serial, epoch):
    out = db.adb(serial, "logcat", "-d", "-v", "epoch", "-T", f"{epoch}.000", "-s", "GlobalConsole").stdout
    return [line.split("GlobalConsole: ", 1)[1].rstrip() for line in out.splitlines() if "GlobalConsole: " in line]


def run_marked(serial, remote, wait):
    """Starts [remote] and returns ({item: value}, finished) from its marked console lines until "done" or [wait]."""
    # The lines of the previous run are then older than the start second.
    time.sleep(1.1)
    epoch = db.adb(serial, "shell", "date", "+%s", check=True).stdout.strip()
    db.adb(serial, "shell", "am", "start", "-n", f"{db.PACKAGE}/{db.RUN_ACTIVITY}", "-e", "path", remote, check=True)
    deadline = time.time() + wait
    items = {}
    while time.time() < deadline:
        time.sleep(0.5)
        for line in console_since(serial, epoch):
            start = line.find(MARK + " ")
            if start >= 0:
                item, _, value = line[start + len(MARK) + 1:].partition(" ")
                items.setdefault(item, value)
        if "done" in items:
            del items["done"]
            return items, True
    return items, False


def run_state(serial, launcher, wait):
    items, finished = run_marked(serial, f"{REMOTE}/main.js", wait)
    cont, cont_finished = run_marked(serial, launcher, wait)
    items.update(cont)
    return items, finished and cont_finished


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--wait", type=int, default=60, help="seconds to wait for each script")
    args = parser.parse_args()
    s = args.serial
    db.adb(s, "shell", "rm", "-rf", REMOTE)
    db.adb(s, "shell", "mkdir", "-p", db.DEVICE_DIR, check=True)
    db.adb(s, "push", str(LOCAL), REMOTE, check=True)
    launcher = db.HERE / "__pycache__" / "aj6_p37_launch.js"
    launcher.parent.mkdir(exist_ok=True)
    text = (db.HERE / "bench" / "bootstrap_smoke" / "launch_project.js").read_text(encoding="utf-8")
    launcher.write_text(text.replace("PROJECT_DIR", f"{REMOTE}/project"), encoding="utf-8")
    db.adb(s, "push", str(launcher), f"{REMOTE}/launch.js", check=True)
    previous = None
    runs = {}
    try:
        for on in (False, True):
            before = db.set_new_modules(s, on)
            previous = before if previous is None else previous
            runs[on] = run_state(s, f"{REMOTE}/launch.js", args.wait)
    finally:
        if previous is not None:
            db.set_new_modules(s, previous)
        db.adb(s, "shell", "rm", "-rf", REMOTE)
    (off, off_done), (on, on_done) = runs[False], runs[True]
    failures = [f"run did not finish: {name}" for name, done in (("off", off_done), ("on", on_done)) if not done]
    for item in list(dict.fromkeys(list(off) + list(on) + list(EXPECTED) + list(REGISTERED))):
        a, b = off.get(item), on.get(item)
        if a is None or b is None:
            failures.append(item)
            print(f"{item}: MISSING off {a} | on {b}")
        elif item in REGISTERED:
            ok = a != b and all(t in b for t in REGISTERED[item])
            if not ok:
                failures.append(item)
            print(f"{item}: {'registered' if ok else 'FAIL registered'} off {a} | on {b}")
        else:
            ok = a == b and (item not in EXPECTED or b == EXPECTED[item])
            if not ok:
                failures.append(item)
            print(f"{item}: same {b}" if ok else f"{item}: FAIL off {a} | on {b}")
    print("FAIL: " + ", ".join(failures) if failures else "PASS")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
