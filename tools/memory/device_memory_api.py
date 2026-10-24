"""Device check of the script API `memory` module (docs/memory/ROADMAP.md P5.1 / T9).

Usage:
  py -3.12 -I -u tools/memory/device_memory_api.py -s SERIAL [--keep]

One probe script, started through RunIntentActivity in the installed debug host, checks on the device:
- snapshot: the 13 fields of T9 (plus allThreads) are all numbers, bridgeSessions (docs/memory-phase2/ROADMAP.md P4.4) holds a
  number for each plugin engine, the heaps and PSS are positive, and the time a call takes;
- retained / collect: a second script stores its own `runtime` in a static Java map (System properties) and ends; after
  `memory.collect(5000)` it must be listed by `memory.retained()` (with its exit kind), and once the probe removes it from the map,
  `memory.collect()` must reclaim it (the analogue of the S2 scenario before and after the H1 fix);
- dumpHeap: writes an hprof next to the probe (debug builds always allow it), checks its size and deletes it;
- watch: hands a short-lived object to LeakCanary (true when LeakCanary is on);
- argument errors of collect / watch / dumpHeap reach the script as errors.
With --sample, it runs the bundled sample "工具/内存诊断" instead (or the given copy of it) and checks its console lines.
The probe writes its result to a file next to it (not to logcat: the error checks print stack traces, and ColorOS drops the
log lines of a process over its quota). The pushed files are removed afterwards unless --keep is given. Output: one PASS / FAIL
line per check with the key numbers. The screen must be on (RunIntentActivity starts an activity); the script never taps or
changes settings.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time

DEVICE_DIR = "/sdcard/Download/aj6-memory-api"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PACKAGE = "org.autojs.autojs6"
MARK = "AJ6MEMAPI"
LEAK_PROPERTY = "aj6.memory.api.leak"
LEAK_STEM = "aj6_mem_api_leak"
LEAK_NAME = f"{LEAK_STEM}.js"
PROBE_NAME = "aj6_mem_api_probe.js"
RESULT = f"{DEVICE_DIR}/result.json"
SAMPLE = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/assets-app/sample/工具/内存诊断 [v6.8.0+].js"
SAMPLE_NAME = "aj6_mem_sample.js"
SAMPLE_BEFORE = "回收前: "
SAMPLE_AFTER = "回收后: "
SAMPLE_DONE = ("没有未回收的脚本运行时", "未回收: ")
SAMPLE_BRIDGE = ("没有未回收的桥接会话", "未回收的桥接会话: ")
# A GlobalConsole logcat line ends with the console time, level and text: "... GlobalConsole: 16:02:37.529/D: text".
CONSOLE_LINE = re.compile(r"GlobalConsole.*?: \d\d:\d\d:\d\d\.\d{3}/[A-Z]: (.*)")
SNAPSHOT_FIELDS = [
    "javaHeapUsed", "javaHeapTotal", "javaHeapMax", "nativeHeapAllocated", "nativeHeapSize", "pss",
    "engines", "retainedRuntimes", "bridgeSessions", "images", "threads", "allThreads", "contexts", "timestamp",
]
BRIDGE_ENGINES = ["node", "bun", "quickjs", "lua", "python"]

LEAK_LINES = [
    f"java.lang.System.getProperties().put('{LEAK_PROPERTY}', runtime);",
    f"console.log('{MARK} leak stored');",
]


def probe_lines(leak_remote):
    return [
        "var result = {};",
        "function err(f) { try { f(); return null; } catch (e) { return String(e && e.message || e).split('\\n')[0].slice(0, 160); } }",
        "var t0 = Date.now(); var s = memory.snapshot(); for (var i = 0; i < 4; i++) memory.snapshot();",
        "result.snapshotMs = (Date.now() - t0) / 5;",
        "result.snapshot = s;",
        "result.types = Object.keys(s).filter(function (k) { return k != 'bridgeSessions' && typeof s[k] !== 'number'; });",
        "result.bridgeTypes = Object.keys(s.bridgeSessions).filter(function (k) { return typeof s.bridgeSessions[k] !== 'number'; });",
        "var baseEngines = s.engines;",
        f"(function () {{ engines.execScriptFile('{leak_remote}'); }})();",
        "var waited = 0;",
        "while (waited < 15000 && (memory.snapshot().engines > baseEngines || java.lang.System.getProperties().get("
        f"'{LEAK_PROPERTY}') == null)) {{ sleep(100); waited += 100; }}",
        "sleep(500);",
        "result.leakEndedAfterMs = waited;",
        "t0 = Date.now(); var held = memory.collect(5000); result.collectHeldMs = Date.now() - t0;",
        "result.retainedHeld = held.retainedRuntimes;",
        f"function isLeak(r) {{ return r.name.indexOf('{LEAK_STEM}') >= 0; }}",
        "result.leakListed = memory.retained().filter(isLeak);",
        f"java.lang.System.getProperties().remove('{LEAK_PROPERTY}');",
        "t0 = Date.now(); var after = memory.collect(); result.collectReleasedMs = Date.now() - t0;",
        "result.retainedAfter = after.retainedRuntimes;",
        "result.leakAfter = memory.retained().filter(isLeak).length;",
        "result.retainedNamesAfter = memory.retained().map(function (r) { return r.name; });",
        "t0 = Date.now(); var path = memory.dumpHeap('aj6-mem-api-check.hprof'); result.dumpMs = Date.now() - t0;",
        "var hprof = new java.io.File(path); result.dumpPath = path; result.dumpBytes = hprof.length(); result.dumpDeleted = hprof.delete();",
        "(function () { var o = { p51: true }; result.watch = memory.watch(o, 'aj6 memory api device check'); })();",
        "result.errors = {",
        "    collectNegative: err(function () { memory.collect(-1); }),",
        "    collectString: err(function () { memory.collect('500'); }),",
        "    watchNumber: err(function () { memory.watch(1); }),",
        "    dumpHeapEmpty: err(function () { memory.dumpHeap(''); }),",
        "    snapshotArgument: err(function () { memory.snapshot(1); }),",
        "};",
        f"files.write('{RESULT}', JSON.stringify(result));",
        f"console.log('{MARK} result written');",
    ]


def adb(serial, *args, check=False, timeout=60):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check, timeout=timeout)


def push(serial, name, lines, local_dir):
    local = local_dir / name
    local.write_text(chr(10).join(lines) + chr(10), encoding="utf-8")
    remote = f"{DEVICE_DIR}/{name}"
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR)
    adb(serial, "push", str(local), remote, check=True)
    return remote


def wait_result(serial, wait):
    deadline = time.time() + wait
    while time.time() < deadline:
        time.sleep(1)
        text = adb(serial, "shell", "cat", RESULT).stdout.strip()
        if text.startswith("{"):
            return json.loads(text)
    return None


def report(ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {text}")
    return ok


def run_sample(serial, sample, wait):
    """Runs the sample through RunIntentActivity and checks the lines it prints (GlobalConsole in logcat)."""
    remote = f"{DEVICE_DIR}/{SAMPLE_NAME}"
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR)
    adb(serial, "push", str(sample), remote, check=True)
    adb(serial, "logcat", "-c")
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)
    lines = []
    deadline = time.time() + wait
    while time.time() < deadline and not any(line.startswith(SAMPLE_BRIDGE) for line in lines):
        time.sleep(1)
        log = adb(serial, "logcat", "-d", "-s", "GlobalConsole:*").stdout
        lines = [match.group(1) for match in CONSOLE_LINE.finditer(log)]
    adb(serial, "shell", "rm", "-rf", DEVICE_DIR)
    ok = report(any(line.startswith(SAMPLE_BEFORE) for line in lines) and any(line.startswith(SAMPLE_AFTER) for line in lines),
                f"sample printed the readings before and after collect ({len(lines)} console lines)")
    for line in lines:
        if line.startswith((SAMPLE_BEFORE, SAMPLE_AFTER) + SAMPLE_DONE + SAMPLE_BRIDGE):
            print(f"INFO {line}")
    ok &= report(any(line.startswith(SAMPLE_DONE) for line in lines), "sample reached the retained list")
    ok &= report(any(line.startswith(SAMPLE_BRIDGE) for line in lines), "sample reached the bridge session counts")
    print("ALL PASS" if ok else "SOME FAILED")
    return 0 if ok else 1


def main():
    import tempfile

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--keep", action="store_true", help="keep the pushed scripts on the device")
    parser.add_argument("--wait", type=int, default=120, help="seconds to wait for the probe result")
    parser.add_argument("--sample", nargs="?", const=SAMPLE, type=pathlib.Path, help="run the sample script instead of the probe")
    args = parser.parse_args()
    serial = args.serial

    package = adb(serial, "shell", "dumpsys", "package", PACKAGE).stdout
    version = next((line.split("versionCode=")[1].split()[0] for line in package.splitlines() if "versionCode=" in line), None)
    if version is None:
        sys.exit(f"{PACKAGE} is not installed on {serial}")
    debuggable = "DEBUGGABLE" in package
    print(f"host versionCode {version}, debuggable {debuggable}")
    if args.sample:
        if "mWakefulness=Awake" not in adb(serial, "shell", "dumpsys", "power").stdout:
            sys.exit("Turn the screen on first")
        return run_sample(serial, args.sample, args.wait)
    if not debuggable:
        sys.exit("The installed host must be the debug build (dumpHeap is checked with debug builds' rule)")
    if "mWakefulness=Awake" not in adb(serial, "shell", "dumpsys", "power").stdout:
        sys.exit("Turn the screen on first")

    with tempfile.TemporaryDirectory() as tmp:
        local_dir = pathlib.Path(tmp)
        leak_remote = push(serial, LEAK_NAME, LEAK_LINES, local_dir)
        probe_remote = push(serial, PROBE_NAME, probe_lines(leak_remote), local_dir)
    adb(serial, "shell", "rm", "-f", RESULT)
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", probe_remote, check=True)
    result = wait_result(serial, args.wait)
    if not args.keep:
        adb(serial, "shell", "rm", "-rf", DEVICE_DIR)
    if result is None:
        sys.exit(f"FAIL no {MARK} result within {args.wait} s")

    ok = True
    snapshot = result["snapshot"]
    mb = 1024 * 1024
    ok &= report(list(snapshot) == SNAPSHOT_FIELDS and not result["types"],
                 f"snapshot fields {len(snapshot)} in T9 order, non-numbers {result['types']}, {result['snapshotMs']:.1f} ms per call")
    ok &= report(snapshot["javaHeapUsed"] > 0 and snapshot["nativeHeapAllocated"] > 0 and snapshot["pss"] > 0 and snapshot["engines"] >= 1,
                 f"snapshot values: Java heap {snapshot['javaHeapUsed'] / mb:.1f} / {snapshot['javaHeapMax'] / mb:.0f} MB, native "
                 f"{snapshot['nativeHeapAllocated'] / mb:.1f} MB, PSS {snapshot['pss'] / mb:.1f} MB, engines {snapshot['engines']}, "
                 f"threads {snapshot['threads']} / {snapshot['allThreads']}, contexts {snapshot['contexts']}, images {snapshot['images']}")
    bridge = snapshot["bridgeSessions"]
    ok &= report(list(bridge) == BRIDGE_ENGINES and not result["bridgeTypes"],
                 f"bridgeSessions engines {list(bridge)}, non-numbers {result['bridgeTypes']}, values {list(bridge.values())}")
    listed = result["leakListed"]
    ok &= report(len(listed) == 1 and listed[0]["exitKind"] == "normal" and listed[0].get("engine") == "rhino",
                 f"held runtime listed after collect(5000): {len(listed)} ({listed[0]['name'] + ', exitKind ' + listed[0]['exitKind'] + ', engine ' + str(listed[0].get('engine')) if listed else '-'}, "
                 f"retainedRuntimes {result['retainedHeld']}, collect took {result['collectHeldMs']} ms, leak script ended after "
                 f"{result['leakEndedAfterMs']} ms)")
    ok &= report(result["leakAfter"] == 0,
                 f"released runtime reclaimed by collect(): listed {result['leakAfter']}, retainedRuntimes {result['retainedAfter']} "
                 f"{result['retainedNamesAfter']}, collect took {result['collectReleasedMs']} ms")
    ok &= report(result["dumpBytes"] > mb and result["dumpDeleted"],
                 f"dumpHeap: {result['dumpBytes'] / mb:.1f} MB in {result['dumpMs']} ms, deleted {result['dumpDeleted']}")
    print(f"INFO watch returned {result['watch']} (true when LeakCanary is on)")
    errors = result["errors"]
    missing = [name for name, message in errors.items() if not message]
    ok &= report(not missing, f"argument errors thrown: {len(errors) - len(missing)} / {len(errors)}, missing {missing}")
    print(f"INFO collect(-1) message: {errors['collectNegative']}")
    print("ALL PASS" if ok else "SOME FAILED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
