"""One timing round of ROADMAP appendix A for red line R2, in one command (P2.4).

Usage: py -3.12 -I tools/augment/device_r2.py -s SERIAL [--restart] [--warmup 100] [--rounds 2] [--runs 5]
    [--errors] [--calls NAME ...] [--init] [--verify [--verify-retries 2]]

Steps: optionally force-stop the host (--restart, a new process), warm it up with --warmup startup timing scripts,
then --rounds times: run bench/error_path.js (--errors), the module call benchmarks bench/calls/NAME.js (--calls) and
the startup timing (--init) --runs times each. The scheduling group (PCY) of the host process is printed before and
after; a change in between makes the numbers incomparable (ROADMAP R2).

Printed per case: the median per round (us; startup steps in ms), and for failing calls the ratio to the Rhino
reference of the same script runs: argument errors to `undefined.x`, Java exceptions to a Java exception thrown through
Rhino's own bridge, `e.stack` reads to the same read after `undefined.x`. Until P5.2 every round ran with the switch of
the new script module layer off and then on (columns "rN off" / "rN on", the ratio for "on"), and the warm-up
alternated the switch every 20 scripts.

--verify (ROADMAP appendix C Q76, for judging R2 on XQ-AT72): every script (error_path first, then each calls file,
then the startup timing) gets all its rounds in a process checked before and after with device_profile: base64.encode()
must take <= 225 us per iteration and no ScriptMessages frame may run interpreted with more than
5 % of the samples. The debug host sometimes leaves those methods in the ART interpreter, which adds 30-50 us to every
failing call of the new layer. A script whose check fails is measured again in a new process (force-stop, the same
warm-up, then error_path once), at most --verify-retries times; discarded timings are printed as "NAME (discarded N)",
followed by the checks of every attempt. Implies --restart.
"""
import argparse
import pathlib
import re
import statistics
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import device_bench as db  # noqa: E402
import device_profile as dp  # noqa: E402

CALL_OR_ERROR_RE = re.compile(r"AJ6BENCH (?:call|error) (.+) ([\d.]+) us$")
STARTUP_STEPS = ["runtime-init-prologue", "runtime-init-epilogue"]
VERIFY_EXPRESSION = "base64.encode()"
VERIFY_LIMIT_US = 225.0
VERIFY_INTERPRETED_PERCENT = 5.0


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


def check(s):
    """Profiles base64.encode(); returns (clean, text) (appendix C Q76)."""
    us, interpreted = dp.profile(s, VERIFY_EXPRESSION, 5, quiet=True)
    messages = max([share for share, symbol in interpreted if ".ScriptMessages." in symbol] or [0.0])
    clean = us is not None and us <= VERIFY_LIMIT_US and messages <= VERIFY_INTERPRETED_PERCENT
    return clean, ("no timing" if us is None else f"{us:.1f} us") + f", ScriptMessages interpreted {messages:.1f}%"


def process_of(s):
    out = db.adb(s, "shell", "pid=$(pidof " + db.PACKAGE + "); echo $pid $(cat /proc/$pid/cpuset)").stdout
    return out.strip() or "not running"


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
    parser.add_argument("--rounds", type=int, default=2, help="rounds of all the scripts")
    parser.add_argument("--runs", type=int, default=5, help="runs of each script per round")
    parser.add_argument("--errors", action="store_true", help="bench/error_path.js")
    parser.add_argument("--calls", nargs="*", default=[], help="bench/calls/NAME.js")
    parser.add_argument("--init", action="store_true", help="startup timing (prologue / epilogue)")
    parser.add_argument("--verify", action="store_true", help="check the process around every script (Q76)")
    parser.add_argument("--verify-retries", type=int, default=2, help="new processes per script after a failed check")
    args = parser.parse_args()
    missing = [n for n in args.calls if not (db.HERE / "bench" / "calls" / f"{n}.js").is_file()]
    if missing:
        sys.exit(f"no such call benchmark: {', '.join(missing)}")
    s = args.serial
    if args.restart or args.verify:
        db.adb(s, "shell", "am", "force-stop", db.PACKAGE)
        time.sleep(1)
    init_script = db.push_script(s, "aj6_init_timing.js")
    # results[(group, label)][round] = median
    results = {}
    checks = []

    def warm_up():
        startup(s, init_script, args.warmup)
        print(f"warm-up: {args.warmup} scripts; host PCY {pcy(s)}")

    def measure(scripts, tag=""):
        """scripts: [(group, remote, row name)]; --rounds times over all of them."""
        for r in range(1, args.rounds + 1):
            for group, remote, into in scripts:
                runs = startup(s, remote, args.runs) if group == "startup ms" else sample(s, remote, args.runs)
                for label, vals in runs.items():
                    results.setdefault((into, label), {})[r] = (statistics.median(vals), len(vals))
            print(f"{tag}round {r} done; host PCY {pcy(s)}")

    warm_up()
    error_path = db.push_script(s, "error_path.js") if args.errors or args.verify else None
    scripts = ([("error_path", error_path)] if args.errors else []) + [(n, push_calls(s, n)) for n in args.calls]
    scripts += [("startup ms", init_script)] if args.init else []
    if not args.verify:
        measure([(group, remote, group) for group, remote in scripts])
    for group, remote in scripts if args.verify else []:
        for attempt in range(1, args.verify_retries + 2):
            process = process_of(s)
            clean, before = check(s)
            after = "-"
            if clean:
                into = group if attempt == 1 else f"{group} (attempt {attempt})"
                measure([(group, remote, into)], f"{into}: ")
                clean, after = check(s)
                if not clean:
                    for key in [k for k in results if k[0] == into]:
                        results[(f"{group} (discarded {attempt})", key[1])] = results.pop(key)
            checks.append((group, attempt, process, before, after, "clean" if clean else "not clean"))
            if clean or attempt > args.verify_retries:
                break
            db.adb(s, "shell", "am", "force-stop", db.PACKAGE)
            time.sleep(1)
            warm_up()
            sample(s, error_path, 1)
    columns = list(range(1, args.rounds + 1))
    print("case | " + " | ".join(f"r{r}" for r in columns))
    for (group, label), cells in results.items():
        ref = reference_of(group.split(" (")[0], label)
        parts = []
        for col in columns:
            if col not in cells:
                parts.append("-")
                continue
            value, n = cells[col]
            text = f"{value:.1f}" + ("" if n >= args.runs else f" (n={n})")
            base = results.get((group, ref), {}).get(col) if ref else None
            if base:
                text += f" ({value / base[0]:.2f})"
            parts.append(text)
        print(f"{group}: {label} | " + " | ".join(parts))
    if checks:
        print("verify: script | attempt | process (pid cpuset) | base64.encode() before | after | result")
        for row in checks:
            print("verify: " + " | ".join(str(x) for x in row))


if __name__ == "__main__":
    sys.exit(main())
