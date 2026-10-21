"""Runs a memory gate scenario several times as an ordinary script and collects the LeakCanary analysis from logcat.

Usage: py -3.12 -I -u tools/memory/leakcanary_probe.py -s SERIAL [--scenario S2] [--runs 6] [--interval 3]
    [--wait 300] [--expect Loopers] [--out FILE]

docs/memory/ROADMAP.md P1.2 / T5: LeakCanary does not dump the heap while instrumentation tests run, so the gate itself
cannot show its reports. This script starts the scenario through RunIntentActivity (outside instrumentation) --runs
times, then waits up to --wait seconds for LeakCanary to dump and analyze the heap. LeakCanary watches the engine and the
runtime of every finished script (LeakCanarySetup.watchEngine) and dumps the heap once objects stay retained.
It pushes one script file and starts it; it does not tap the screen or change settings.

Output: one line per run, then the number of watched and retained objects LeakCanary logged, whether an analysis
appeared, its number of application leaks and whether the leak traces mention --expect. --out writes the analysis text
(the text LeakCanary shows in its leak list) to a file. The exit code is 0 when the result matches --expect-leaks
(yes: an analysis with application leaks mentioning --expect; no: no application leak in --wait seconds), and 2 when
leaks are expected but LeakCanary's automatic heap dumps are off (the default since P1.2; Leaks app > About).
"""
import argparse
import pathlib
import re
import subprocess
import sys
import tempfile
import time

DEVICE_DIR = "/sdcard/Download/aj6-memory-probe"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PACKAGE = "org.autojs.autojs6"

# Ordinary-script versions of the gate scenarios (the normal mode of app/src/androidTest/.../GateScenarios.kt).
SCENARIOS = {
    "S1": "var gate = 1;\n",
    "S2": (
        "var HOLD = 50;\n"
        "for (var i = 0; i < 10; i++) {\n"
        "    threads.start(function () { sleep(HOLD); });\n"
        "}\n"
        "var pool = threads.pool({ corePoolSize: 2, maxPoolSize: 2 });\n"
        "for (var j = 0; j < 4; j++) {\n"
        "    pool.execute(function () { sleep(HOLD); });\n"
        "}\n"
        "pool.shutdown();\n"
    ),
}

WATCH_RE = re.compile(r"Watching instance of (\S+)")
RETAINED_RE = re.compile(r"(\d+) retained objects?|Found (\d+) objects? retained")
LEAKS_RE = re.compile(r"^(\d+) APPLICATION LEAKS?")
DUMP_TIME_RE = re.compile(r"^Heap dump timestamp: (\d+)")


def adb(serial, *args, check=False):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check)


def leakcanary_lines(serial, since, seen):
    """LeakCanary lines since [since], accumulated in [seen] (timestamped line -> text) across polls, because the analysis
    can take minutes and the ring buffer may drop the early lines meanwhile."""
    out = adb(serial, "logcat", "-d", "-v", "time", "-T", since, "-s", "LeakCanary:*").stdout
    for line in out.splitlines():
        if "LeakCanary" in line and line not in seen:
            seen[line] = re.sub(r"^.*?[VDIWEF]/LeakCanary\(\s*\d+\):\s?", "", line).rstrip()
    return list(seen.values())


def analysis_blocks(lines):
    """Every complete "HEAP ANALYSIS RESULT" block."""
    blocks = []
    starts = [i for i, l in enumerate(lines) if "HEAP ANALYSIS RESULT" in l]
    for n, first in enumerate(starts):
        limit = starts[n + 1] if n + 1 < len(starts) else len(lines)
        for j in range(first, limit):
            if lines[j].startswith("Heap dump duration"):
                end = j + 1 if j + 1 < limit and lines[j + 1].startswith("====") else j
                blocks.append(lines[max(first - 1, 0):end + 1])
                break
    return blocks


def dump_timestamp(block):
    for line in block:
        m = DUMP_TIME_RE.match(line.strip())
        if m:
            return int(m.group(1))
    return 0


def analysis_block(lines, since_epoch_ms):
    """The last analysis of a heap dump taken after [since_epoch_ms]. LeakCanary analyzes through WorkManager, so a new
    process (for example after reinstalling the app) may still report a heap dump of the previous process; those are skipped."""
    fresh = [b for b in analysis_blocks(lines) if dump_timestamp(b) >= since_epoch_ms]
    return fresh[-1] if fresh else None


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("-s", "--serial", required=True)
    ap.add_argument("--scenario", default="S2", choices=sorted(SCENARIOS))
    ap.add_argument("--runs", type=int, default=6)
    ap.add_argument("--interval", type=float, default=3.0, help="seconds between two runs")
    ap.add_argument("--wait", type=float, default=300.0, help="seconds to wait for the analysis after the last run")
    ap.add_argument("--expect", default="Loopers", help="text expected in the leak traces")
    ap.add_argument("--expect-leaks", choices=["yes", "no"], default="yes")
    ap.add_argument("--out", help="file for the analysis text")
    args = ap.parse_args()

    # One shell argument: adb joins the arguments with spaces, which would split the format at its space.
    since = adb(args.serial, "shell", "date '+%m-%d %H:%M:%S.000'").stdout.strip()
    since_epoch_ms = int(adb(args.serial, "shell", "date +%s").stdout.strip()) * 1000
    remote = f"{DEVICE_DIR}/memory_probe_{args.scenario}.js"
    local = pathlib.Path(tempfile.gettempdir()) / f"memory_probe_{args.scenario}.js"
    local.write_text(SCENARIOS[args.scenario], encoding="utf-8")
    adb(args.serial, "shell", "mkdir", "-p", DEVICE_DIR)
    adb(args.serial, "push", str(local), remote, check=True)
    local.unlink()

    for i in range(args.runs):
        result = adb(args.serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote)
        ok = result.returncode == 0 and "Error" not in result.stdout
        print(f"run {i + 1}/{args.runs} {'started' if ok else 'FAILED to start'}", flush=True)
        time.sleep(args.interval)

    deadline = time.time() + args.wait
    block = None
    lines = []
    seen = {}
    while time.time() < deadline:
        lines = leakcanary_lines(args.serial, since, seen)
        block = analysis_block(lines, since_epoch_ms)
        if block:
            break
        time.sleep(5)

    watched = [m.group(1) for l in lines for m in [WATCH_RE.search(l)] if m]
    retained = [int(next(g for g in m.groups() if g)) for l in lines for m in [RETAINED_RE.search(l)] if m]
    print(f"watched {len(watched)} object(s): {', '.join(sorted(set(watched))) or '-'}")
    print(f"max retained reported: {max(retained) if retained else 0}")
    leaks = 0
    mentions = False
    if block:
        for l in block:
            m = LEAKS_RE.match(l.strip())
            if m:
                leaks = int(m.group(1))
                break
        mentions = any(args.expect in l for l in block)
        print(f"analysis: {leaks} application leak(s), mentions {args.expect}: {'yes' if mentions else 'no'}")
        if args.out:
            pathlib.Path(args.out).write_text("\n".join(block) + "\n", encoding="utf-8")
            print(f"analysis text written to {args.out}")
    else:
        print(f"analysis: none within {args.wait:.0f} s")

    dump_off = any("disabled from LeakCanary About screen" in l for l in lines)
    if dump_off:
        print("automatic heap dump: off (Leaks app > About > Dump heap automatically, off by default since P1.2)")
    if args.expect_leaks == "yes" and not block and dump_off:
        # Retained objects are only counted, not analyzed, while automatic heap dumps are off.
        # zh-CN: 自动转储关闭时只计数残留对象, 不做分析.
        print("INCONCLUSIVE: turn on automatic heap dumps in the Leaks app, or dump from its notification, to get an analysis")
        return 2
    passed = (leaks > 0 and mentions) if args.expect_leaks == "yes" else (leaks == 0)
    print("PASS" if passed else "FAIL")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
