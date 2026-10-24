"""Runs a memory gate scenario several times as an ordinary script and collects the LeakCanary analysis from logcat.

Usage: py -3.12 -I -u tools/memory/leakcanary_probe.py -s SERIAL [--scenario S2] [--runs 6] [--interval 3]
    [--wait 300] [--expect Loopers] [--out FILE]

docs/memory/ROADMAP.md P1.2 / T5: LeakCanary does not dump the heap while instrumentation tests run, so the gate itself
cannot show its reports. This script starts the scenario through RunIntentActivity (outside instrumentation) --runs
times, then waits up to --wait seconds for LeakCanary to dump and analyze the heap. LeakCanary watches the engine and the
runtime of every finished script (LeakCanarySetup.watchEngine) and dumps the heap once objects stay retained.
It pushes the script files (a bridge scenario removes its directory afterwards) and starts the entry; it does not tap the
screen or change settings.

Scenarios: S1 / S2 are Rhino scripts (the normal mode of app/src/androidTest/.../GateScenarios.kt). ND / BN / QJ / LU / PY are
plugin engine scripts after the bridge scenarios of BridgeScenarios.kt (docs/memory-phase2/ROADMAP.md P4.3): an empty script
and one that uses host capabilities (output callbacks for QuickJS), each in its own directory (Node declares its
capabilities in package.json there). Debug builds hand the session objects of every finished plugin engine execution to
LeakCanary (MemoryDiagnostics, P4.4), and their watch descriptions start with the engine name, so "bridge objects" counts
those. A bridge scenario whose plugin is not installed is skipped (exit code 3).

Output: one line per run, then the number of watched and retained objects LeakCanary logged, whether an analysis
appeared, its number of application leaks and whether the leak traces mention --expect; for bridge scenarios also the
watched bridge objects and the script output lines in logcat. --out writes the analysis text (the text LeakCanary shows
in its leak list) to a file. The exit code is 0 when the result matches --expect-leaks (yes: an analysis with application
leaks mentioning --expect; no: no application leak in --wait seconds, and for bridge scenarios also no retained object,
at least one watched bridge object and every run printed its line without an error), and 2 when leaks are expected but
LeakCanary's automatic heap dumps are off (the default since P1.2; Leaks app > About).
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
PLUGIN_PREFIX = "io.github.supermonster003.autojs6.plugin."

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

# Plugin engine scenarios: engine (as in the watch descriptions and memory.snapshot().bridgeSessions), plugin package
# suffix, files of the scenario directory (the first one is the entry). Every run prints one line starting with
# "mp2-p4 <id>"; a failure prints "mp2-p4 <id> error".
BRIDGE_SCENARIOS = {
    "ND1": ("node", "nodejs", {
        "main.js": '"nodejs";\nconsole.log("mp2-p4 ND1");\n',
    }),
    "ND2": ("node", "nodejs", {
        "main.js": (
            '"nodejs";\n'
            "(async () => {\n"
            '    const device = require("device");\n'
            '    const toast = require("toast");\n'
            "    const info = await device.info();\n"
            '    await toast.showToast("mp2-p4 ND2");\n'
            '    console.log("mp2-p4 ND2 " + Object.keys(info || {}).length);\n'
            '})().catch((e) => { console.error("mp2-p4 ND2 error " + e); process.exitCode = 1; });\n'
        ),
        "package.json": '{"autojs6":{"node":{"permissions":["device","toast"]}}}\n',
    }),
    "BN1": ("bun", "bun.runtime", {
        "main.js": '"bun";\nconsole.log("mp2-p4 BN1");\n',
    }),
    "BN2": ("bun", "bun.runtime", {
        "main.js": (
            '"bun";\n'
            "(async () => {\n"
            "    const unix = process.env.AUTOJS6_HOST_BRIDGE_SOCKET;\n"
            '    if (!unix) throw new Error("AUTOJS6_HOST_BRIDGE_SOCKET is not set");\n'
            "    const call = async (id, body) => {\n"
            '        const response = await fetch("http://autojs6/v1/" + id, { unix, method: "POST", body: JSON.stringify(body) });\n'
            "        const reply = await response.json();\n"
            '        if (!reply.ok) throw new Error(id + " " + response.status + " " + (reply.error && reply.error.code));\n'
            "        return reply.result;\n"
            "    };\n"
            '    const info = await call("device.info", {});\n'
            '    await call("ui.toast", { text: "mp2-p4 BN2" });\n'
            '    console.log("mp2-p4 BN2 " + Object.keys(info || {}).length);\n'
            '})().catch((e) => { console.error("mp2-p4 BN2 error " + e); process.exitCode = 1; });\n'
        ),
    }),
    "QJ1": ("quickjs", "quickjs.runtime", {
        "main.js": '"quickjs";\nconsole.log("mp2-p4 QJ1");\n',
    }),
    "QJ3": ("quickjs", "quickjs.runtime", {
        "main.js": (
            '"quickjs";\n'
            "function busy(ms) { const end = Date.now() + ms; while (Date.now() < end) {} }\n"
            "let ticks = 0;\n"
            "const tick = () => { busy(300); ticks++; console.log(\"mp2-p4 QJ3 tick \" + ticks); };\n"
            "Promise.resolve().then(tick).then(tick).then(tick)\n"
            '    .then(() => console.log("mp2-p4 QJ3 " + ticks))\n'
            '    .catch((e) => console.error("mp2-p4 QJ3 error " + e));\n'
        ),
    }),
    "LU1": ("lua", "lua.runtime", {
        "main.lua": 'local autojs = require("autojs")\nautojs.console.log("mp2-p4 LU1")\n',
    }),
    "LU2": ("lua", "lua.runtime", {
        "main.lua": (
            'local autojs = require("autojs")\n'
            "local info = autojs.device.info()\n"
            'autojs.storage.put("mp2p4", "x")\n'
            'local stored = autojs.storage.get("mp2p4") ~= nil\n'
            'autojs.storage.remove("mp2p4")\n'
            'autojs.ui.toast("mp2-p4 LU2")\n'
            'autojs.console.log("mp2-p4 LU2 " .. tostring(info.sdkInt) .. " " .. tostring(stored))\n'
        ),
    }),
    "PY1": ("python", "python.runtime", {
        "main.py": 'print("mp2-p4 PY1", flush=True)\n',
    }),
    "PY2": ("python", "python.runtime", {
        "main.py": (
            "from autojs6 import device, files, toast\n"
            "info = device.info()\n"
            'data = "x" * 8192\n'
            'files.write_text("mp2p4_py2.txt", data)\n'
            'same = files.read_text("mp2p4_py2.txt") == data\n'
            'toast("mp2-p4 PY2")\n'
            'print("mp2-p4 PY2 " + str(info["battery"]["percent"]) + " " + str(same), flush=True)\n'
        ),
    }),
}

WATCH_RE = re.compile(r"Watching instance of (\S+)(?: \((.*)\))? with key")
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


def output_lines(serial, since, marker, seen):
    """Script output lines containing [marker] since [since], accumulated in [seen] like [leakcanary_lines]."""
    out = adb(serial, "logcat", "-d", "-v", "time", "-T", since).stdout
    for line in out.splitlines():
        if marker in line and "LeakCanary" not in line:
            seen.add(line)
    return seen


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


def push_files(serial, remote_dir, files):
    """Pushes [files] (name -> text) into [remote_dir] and returns the remote path of the first one (the entry)."""
    adb(serial, "shell", "mkdir", "-p", remote_dir)
    with tempfile.TemporaryDirectory() as tmp:
        for name, text in files.items():
            local = pathlib.Path(tmp) / name
            local.write_bytes(text.encode("utf-8"))
            adb(serial, "push", str(local), f"{remote_dir}/{name}", check=True)
    return f"{remote_dir}/{next(iter(files))}"


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("-s", "--serial", required=True)
    ap.add_argument("--scenario", default="S2", choices=sorted(SCENARIOS) + sorted(BRIDGE_SCENARIOS))
    ap.add_argument("--runs", type=int, default=6)
    ap.add_argument("--interval", type=float, default=3.0, help="seconds between two runs")
    ap.add_argument("--wait", type=float, default=300.0, help="seconds to wait for the analysis after the last run")
    ap.add_argument("--expect", default="Loopers", help="text expected in the leak traces")
    ap.add_argument("--expect-leaks", choices=["yes", "no"], default="yes")
    ap.add_argument("--out", help="file for the analysis text")
    args = ap.parse_args()

    bridge = BRIDGE_SCENARIOS.get(args.scenario)
    if bridge:
        engine, plugin, files = bridge
        if not adb(args.serial, "shell", "pm", "path", PLUGIN_PREFIX + plugin).stdout.strip():
            print(f"SKIP: plugin {PLUGIN_PREFIX + plugin} is not installed")
            return 3

    # One shell argument: adb joins the arguments with spaces, which would split the format at its space.
    since = adb(args.serial, "shell", "date '+%m-%d %H:%M:%S.000'").stdout.strip()
    since_epoch_ms = int(adb(args.serial, "shell", "date +%s").stdout.strip()) * 1000
    if bridge:
        remote = push_files(args.serial, f"{DEVICE_DIR}/{args.scenario.lower()}", files)
    else:
        remote = push_files(args.serial, DEVICE_DIR, {f"memory_probe_{args.scenario}.js": SCENARIOS[args.scenario]})

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

    watches = [(m.group(1), m.group(2) or "") for l in lines for m in [WATCH_RE.search(l)] if m]
    retained = [int(next(g for g in m.groups() if g)) for l in lines for m in [RETAINED_RE.search(l)] if m]
    print(f"watched {len(watches)} object(s): {', '.join(sorted({c for c, _ in watches})) or '-'}")
    max_retained = max(retained) if retained else 0
    print(f"max retained reported: {max_retained}")
    bridge_ok = True
    if bridge:
        bridge_watches = [c for c, d in watches if d.startswith(engine + " ")]
        classes = ", ".join(sorted({c.rsplit(".", 1)[-1] for c in bridge_watches})) or "-"
        print(f"watched {engine} bridge objects: {len(bridge_watches)} ({classes})")
        marker = f"mp2-p4 {args.scenario}"
        printed = output_lines(args.serial, since, marker, set())
        errors = [l for l in printed if f"{marker} error" in l]
        finals = [l for l in printed if re.search(re.escape(marker) + r"(\s|$)", l) and " tick " not in l and l not in errors]
        print(f"script output lines: {len(finals)} / {args.runs} runs, errors {len(errors)}")
        for line in errors[:3]:
            print(f"  error: {line[line.find(marker):][:200]}")
        bridge_ok = max_retained == 0 and len(bridge_watches) > 0 and len(finals) >= args.runs and not errors
        adb(args.serial, "shell", "rm", "-rf", remote.rsplit("/", 1)[0])
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
    passed = (leaks > 0 and mentions) if args.expect_leaks == "yes" else (leaks == 0 and bridge_ok)
    print("PASS" if passed else "FAIL")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
