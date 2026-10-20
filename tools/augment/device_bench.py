"""Runs the Augment timing scripts on a device through RunIntentActivity and prints the timing lines.

Usage: py -3.12 -I tools/augment/device_bench.py -s SERIAL [--runs 5] [--skip-compile] [--skip-calls] [--new-modules on|off]
--new-modules sets the hidden switch of the new script module layer (ROADMAP T8) for the run and restores it afterwards.
GlobalConsole lines are polled from logcat every half second and accumulated, so a logcat clear by another
process in the middle of a run only loses lines that were never seen. logcat is never cleared here.
"""
import argparse
import pathlib
import re
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
HOST = HERE.parents[1]
DEVICE_DIR = "/sdcard/Download/aj6-augment-bench"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PACKAGE = "org.autojs.autojs6"

SWITCH_KEY = "key_$_new_script_module_layer"
SWITCH_MARK = "AJ6SWITCH"
TIMING_RE = re.compile(r"(runtime-init-prologue|script-init|runtime-init-epilogue): (\d+)")
TOTAL_RE = re.compile(r"用时 ([\d.]+) 秒")  # "用时 N 秒"
END_MARK = "运行结束"  # "运行结束"


def adb(serial, *args, check=False):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check)


def console_lines(serial):
    out = adb(serial, "logcat", "-d").stdout
    return [re.sub(r"^.*GlobalConsole: ", "", l).rstrip() for l in out.splitlines() if "GlobalConsole:" in l]


def run_script(serial, remote, wait):
    """Starts the script and returns the new GlobalConsole lines until the end mark or the timeout."""
    before = set(console_lines(serial))
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)
    deadline = time.time() + wait
    seen = []
    seen_set = set()

    def collect():
        for l in console_lines(serial):
            if l in before or l in seen_set:
                continue
            seen_set.add(l)
            seen.append(l)

    while time.time() < deadline:
        time.sleep(0.5)
        collect()
        if any(END_MARK in l for l in seen):
            time.sleep(0.5)
            collect()
            break
    return seen


def push_script(serial, name):
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    adb(serial, "push", str(HERE / "bench" / name), f"{DEVICE_DIR}/{name}", check=True)
    return f"{DEVICE_DIR}/{name}"


def set_new_modules(serial, value):
    """Sets the hidden switch of the new script module layer (ROADMAP T8) and returns its previous value."""
    script = HERE / "__pycache__" / "aj6_switch.js"
    script.parent.mkdir(exist_ok=True)
    lines = [
        "var pref = org.autojs.autojs.core.pref.Pref.INSTANCE;",
        f"var before = pref.getBoolean('{SWITCH_KEY}', false);",
        f"pref.putBooleanSync('{SWITCH_KEY}', {'true' if value else 'false'});",
        f"console.log('{SWITCH_MARK} ' + before + ' ' + pref.getBoolean('{SWITCH_KEY}', false));",
    ]
    script.write_text(chr(10).join(lines) + chr(10), encoding="utf-8")
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    adb(serial, "push", str(script), f"{DEVICE_DIR}/aj6_switch.js", check=True)
    for line in run_script(serial, f"{DEVICE_DIR}/aj6_switch.js", 30):
        start = line.find(SWITCH_MARK + " ")
        if start >= 0:
            before, after = line[start + len(SWITCH_MARK) + 1:].split()[:2]
            if after != ("true" if value else "false"):
                sys.exit(f"the switch did not change: {line}")
            print(f"new script module layer: {before} -> {after}")
            return before == "true"
    sys.exit("no switch output from the device")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--skip-compile", action="store_true", help="skip the obsolete JS compile timing")
    parser.add_argument("--skip-calls", action="store_true", help="skip the per-call overhead benchmark")
    parser.add_argument("--new-modules", choices=["on", "off"], help="set the new script module layer switch for the run")
    args = parser.parse_args()
    s = args.serial
    previous = set_new_modules(s, args.new_modules == "on") if args.new_modules else None
    try:
        bench(args)
    finally:
        if previous is not None and previous != (args.new_modules == "on"):
            set_new_modules(s, previous)


def bench(args):
    s = args.serial

    init_script = push_script(s, "aj6_init_timing.js")
    rows = []
    for i in range(args.runs):
        lines = run_script(s, init_script, 30)
        row = {}
        for l in lines:
            m = TIMING_RE.search(l)
            if m:
                row[m.group(1)] = int(m.group(2))
            m = TOTAL_RE.search(l)
            if m:
                row["total_s"] = float(m.group(1))
        rows.append(row)
        print(f"run {i + 1}: {row}" if row else f"run {i + 1}: no timing lines; raw={lines[:6]}")
        time.sleep(2)
    for k in ["runtime-init-prologue", "script-init", "runtime-init-epilogue", "total_s"]:
        vals = sorted(r[k] for r in rows if k in r)
        if vals:
            print(f"{k}: min={vals[0]} median={vals[len(vals) // 2]} max={vals[-1]} n={len(vals)}")

    if not args.skip_compile:
        print("--- obsolete JS module compile timing ---")
        obsolete = HOST / "app/src/main/assets/modules/obsolete"
        adb(s, "shell", "rm", "-rf", f"{DEVICE_DIR}/obsolete")
        adb(s, "push", str(obsolete), f"{DEVICE_DIR}/obsolete", check=True)
        for l in run_script(s, push_script(s, "obsolete_compile_timing.js"), 120):
            if "AJ6BENCH" in l or TIMING_RE.search(l) or TOTAL_RE.search(l):
                print(l)

    if not args.skip_calls:
        print("--- per-call overhead ---")
        for l in run_script(s, push_script(s, "call_overhead.js"), 240):
            if "AJ6BENCH" in l or "/E:" in l:
                print(l)


if __name__ == "__main__":
    sys.exit(main())
