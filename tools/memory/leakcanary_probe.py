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
(yes: an analysis with application leaks mentioning --expect; no: no application leak in --wait seconds).
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


def adb(serial, *args, check=False):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check)


def leakcanary_lines(serial, since):
    out = adb(serial, "logcat", "-d", "-v", "brief", "-T", since, "-s", "LeakCanary:*").stdout
    return [re.sub(r"^[VDIWEF]/LeakCanary\(\s*\d+\):\s?", "", l).rstrip() for l in out.splitlines() if "LeakCanary" in l]


def analysis_block(lines):
    """The last complete "HEAP ANALYSIS RESULT" block, or None."""
    starts = [i for i, l in enumerate(lines) if "HEAP ANALYSIS RESULT" in l]
    if not starts:
        return None
    start = max(starts[-1] - 1, 0)
    for j in range(starts[-1], len(lines)):
        if lines[j].startswith("Heap dump duration") or lines[j].startswith("Analysis duration"):
            end = j
            while end + 1 < len(lines) and not lines[end + 1].startswith("===="):
                end += 1
            return lines[start:min(end + 2, len(lines))]
    return None


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

    since = adb(args.serial, "shell", "date", "+%m-%d %H:%M:%S.000").stdout.strip()
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
    while time.time() < deadline:
        lines = leakcanary_lines(args.serial, since)
        block = analysis_block(lines)
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

    passed = (leaks > 0 and mentions) if args.expect_leaks == "yes" else (leaks == 0)
    print("PASS" if passed else "FAIL")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
