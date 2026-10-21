"""One timing round of ROADMAP appendix A for red line R2, in one command (P2.4).

Usage: py -3.12 -I tools/augment/device_r2.py -s SERIAL [--restart] [--warmup 100] [--rounds 2] [--runs 5]
    [--errors] [--calls NAME ...] [--init]

Steps: optionally force-stop the host (--restart, a new process), warm it up with --warmup startup timing scripts
(alternating the switch of the new script module layer off / on every 20), then --rounds times "off, on": with the
switch off and then on, run bench/error_path.js (--errors), the module call benchmarks bench/calls/NAME.js (--calls)
and the startup timing (--init) --runs times each. The scheduling group (PCY) of the host process is printed before
and after; a change in between makes the numbers incomparable (ROADMAP R2).

Printed per case: the median per state and round (us; startup steps in ms), and for failing calls the ratio to the
Rhino reference of the same script runs: argument errors to `undefined.x`, Java exceptions to a Java exception thrown
through Rhino's own bridge, `e.stack` reads to the same read after `undefined.x`. The switch is restored to its
previous value at the end.
"""
import argparse
import pathlib
import re
import statistics
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import device_bench as db  # noqa: E402

CALL_OR_ERROR_RE = re.compile(r"AJ6BENCH (?:call|error) (.+) ([\d.]+) us$")
STARTUP_STEPS = ["runtime-init-prologue", "runtime-init-epilogue"]


def pcy(s):
    out = db.adb(s, "shell", "ps", "-A", "-o", "PID,PCY,NAME").stdout
    rows = [l.split() for l in out.splitlines() if l.strip().endswith(db.PACKAGE)]
    return ", ".join(f"{r[0]} {r[1]}" for r in rows) or "not running"


def push_calls(s, name):
    local = db.HERE / "__pycache__" / f"aj6_calls_{name}.js"
    local.parent.mkdir(exist_ok=True)
    prelude = (db.HERE / "bench" / "calls_prelude.js").read_text(encoding="utf-8")
    calls = (db.HERE / "bench" / "calls" / f"{name}.js").read_text(encoding="utf-8")
    local.write_text(prelude + chr(10) + calls, encoding="utf-8")
    db.adb(s, "shell", "mkdir", "-p", db.DEVICE_DIR, check=True)
    remote = f"{db.DEVICE_DIR}/{local.name}"
    db.adb(s, "push", str(local), remote, check=True)
    return remote


def sample(s, remote, runs, wait=240):
    """Runs a benchmark script [runs] times; returns {label: [us, ...]}."""
    samples = {}
    for _ in range(runs):
        for line in db.run_script(s, remote, wait):
            m = CALL_OR_ERROR_RE.search(line)
            if m:
                samples.setdefault(m.group(1), []).append(float(m.group(2)))
        time.sleep(2)
    return samples


def startup(s, script, runs):
    samples = {}
    for _ in range(runs):
        for line in db.run_script(s, script, 30):
            m = db.TIMING_RE.search(line)
            if m and m.group(1) in STARTUP_STEPS:
                samples.setdefault(m.group(1), []).append(float(m.group(2)))
        time.sleep(1)
    return samples


def reference_of(group, label):
    """The Rhino reference case a failing case is compared with, or None for a case that is not a failing call."""
    if group == "error_path":
        if label == "module-java-exception":
            return "rhino-java-exception"
        if label == "module-arity-read-stack":
            return "rhino-undefined-property-read-stack"
        if label.startswith("module-") and label != "module-success":
            return "rhino-undefined-property"
        return None
    return "ref rhino-undefined-property" if label.startswith("fail ") else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--restart", action="store_true", help="force-stop the host first (a new process)")
    parser.add_argument("--warmup", type=int, default=100, help="startup timing scripts run before measuring")
    parser.add_argument("--rounds", type=int, default=2, help="rounds of switch off, then on")
    parser.add_argument("--runs", type=int, default=5, help="runs of each script per state and round")
    parser.add_argument("--errors", action="store_true", help="bench/error_path.js")
    parser.add_argument("--calls", nargs="*", default=[], help="bench/calls/NAME.js")
    parser.add_argument("--init", action="store_true", help="startup timing (prologue / epilogue)")
    args = parser.parse_args()
    missing = [n for n in args.calls if not (db.HERE / "bench" / "calls" / f"{n}.js").is_file()]
    if missing:
        sys.exit(f"no such call benchmark: {', '.join(missing)}")
    s = args.serial
    if args.restart:
        db.adb(s, "shell", "am", "force-stop", db.PACKAGE)
        time.sleep(1)
    init_script = db.push_script(s, "aj6_init_timing.js")
    previous = None
    # results[(group, label)][(round, state)] = median
    results = {}
    try:
        state = False
        for start in range(0, args.warmup, 20):
            first = db.set_new_modules(s, state)
            previous = first if previous is None else previous
            startup(s, init_script, min(20, args.warmup - start))
            state = not state
        print(f"warm-up: {args.warmup} scripts; host PCY {pcy(s)}")
        scripts = ([("error_path", db.push_script(s, "error_path.js"))] if args.errors else []) + [(n, push_calls(s, n)) for n in args.calls]
        for r in range(1, args.rounds + 1):
            for on in (False, True):
                before = db.set_new_modules(s, on)
                previous = before if previous is None else previous
                key = (r, "on" if on else "off")
                for group, remote in scripts:
                    for label, vals in sample(s, remote, args.runs).items():
                        results.setdefault((group, label), {})[key] = (statistics.median(vals), len(vals))
                if args.init:
                    for label, vals in startup(s, init_script, args.runs).items():
                        results.setdefault(("startup ms", label), {})[key] = (statistics.median(vals), len(vals))
                print(f"round {r} {key[1]} done; host PCY {pcy(s)}")
    finally:
        if previous is not None:
            db.set_new_modules(s, previous)
    columns = [(r, st) for r in range(1, args.rounds + 1) for st in ("off", "on")]
    print("case | " + " | ".join(f"r{r} {st}" for r, st in columns))
    for (group, label), cells in results.items():
        ref = reference_of(group, label)
        parts = []
        for col in columns:
            if col not in cells:
                parts.append("-")
                continue
            value, n = cells[col]
            text = f"{value:.1f}" + ("" if n >= args.runs else f" (n={n})")
            base = results.get((group, ref), {}).get(col) if ref else None
            if base and col[1] == "on":
                text += f" ({value / base[0]:.2f})"
            parts.append(text)
        print(f"{group}: {label} | " + " | ".join(parts))


if __name__ == "__main__":
    sys.exit(main())
