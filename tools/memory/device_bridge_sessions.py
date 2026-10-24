"""Device check of the bridge session diagnostics (docs/memory-phase2/ROADMAP.md P4.4).

Usage:
  py -3.12 -I -u tools/memory/device_bridge_sessions.py -s SERIAL [--engines node,bun,quickjs,lua,python] [--hold 6] [--shots DIR]
  py -3.12 -I -u tools/memory/device_bridge_sessions.py -s SERIAL --final [--shots DIR]

For each engine whose plugin is installed, a Rhino probe started through RunIntentActivity in the installed debug host:
1. runs an empty script of the engine (engines.execScriptFile) and waits until its engine is destroyed; right afterwards
   memory.retained() lists the runtime of a Node script with engine "node" (the other engines have no script runtime), and
   memory.collect(10000).bridgeSessions[engine] must be 0;
2. runs it again while a BridgeExecutionObserver listener keeps every session object the execution creates (the test holding
   the session on purpose); after the end memory.collect(3000).bridgeSessions[engine] must be at least 1, and
   MemoryDiagnostics.retainedBridgeSessions() lists the execution with its objects. The probe keeps them --hold seconds, in which
   --shots screenshots the memory diagnostics page (top and end, through device_diagnostics_page.py; it holds them at least 16 s);
3. drops them; memory.collect(10000).bridgeSessions[engine] must be 0 again.
An engine whose plugin refuses to run the script (no bridge object is created and logcat names an error code such as
PYTHON_RUNTIME_PROVIDER_UNTRUSTED for a runtime signed with another key than the host, or LUA_RUNTIME_UNAVAILABLE for a Lua
runtime package the host does not accept) is reported as SKIP with that code.
--final only collects once (memory.collect(10000)) and checks that no finished runtime and no bridge session is left, for
example after leakcanary_probe.py ran the bridge scenarios (P4.3); --shots adds the end of the diagnostics page.
The probe writes its result to a file next to it (polled with adb, not read from logcat), and the pushed files are removed
afterwards. The probe first brings the memory diagnostics page to the front and keeps it there: once RunIntentActivity finishes,
a host without a foreground service becomes a cached process, and the cached apps freezer stops it (cgroup.freeze 1, seen on
OPD2413), which would stall the probe until the host comes back to the front. Output: one PASS / FAIL line per check with the key numbers. The screen must be on (RunIntentActivity starts an
activity); the script never taps or changes settings.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import tempfile
import time

HERE = str(pathlib.Path(__file__).resolve().parent)
# -I leaves the script's own directory off sys.path.
sys.path.insert(0, HERE)
import device_diagnostics_page as page  # noqa: E402

sys.path.remove(HERE)

DEVICE_DIR = "/sdcard/Download/aj6-bridge-sessions"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PACKAGE = "org.autojs.autojs6"
PLUGIN_PREFIX = "io.github.supermonster003.autojs6.plugin."
STEM = "aj6_bs_"
ENGINES = {
    # engine: (plugin package suffix, script file, script text)
    "node": ("nodejs", f"{STEM}node.js", '"nodejs";\nconsole.log("aj6 bridge sessions node");\n'),
    "bun": ("bun.runtime", f"{STEM}bun.js", '"bun";\nconsole.log("aj6 bridge sessions bun");\n'),
    "quickjs": ("quickjs.runtime", f"{STEM}quickjs.js", '"quickjs";\nconsole.log("aj6 bridge sessions quickjs");\n'),
    "lua": ("lua.runtime", f"{STEM}lua.lua", 'local autojs = require("autojs")\nautojs.console.log("aj6 bridge sessions lua")\n'),
    "python": ("python.runtime", f"{STEM}python.py", 'print("aj6 bridge sessions python", flush=True)\n'),
}


def adb(serial, *args, check=False, timeout=60):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check, timeout=timeout)


def push(serial, name, text):
    with tempfile.TemporaryDirectory() as tmp:
        local = pathlib.Path(tmp) / name
        local.write_bytes(text.encode("utf-8"))
        remote = f"{DEVICE_DIR}/{name}"
        adb(serial, "push", str(local), remote, check=True)
    return remote


def start(serial, remote):
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)


def read_result(serial, path):
    text = adb(serial, "shell", "cat", path).stdout.strip()
    return json.loads(text) if text.startswith("{") else None


HELPERS = [
    "(function () {",
    f"    var intent = new android.content.Intent(context, {page.DIAGNOSTICS});",
    "    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);",
    "    context.startActivity(intent);",
    "    sleep(1500);",
    "})();",
    "function save(phase) { result.phase = phase; files.write(RESULT, JSON.stringify(result)); }",
    "function sessions() {",
    "    var list = org.autojs.autojs.runtime.memory.MemoryDiagnostics.INSTANCE.retainedBridgeSessions(), out = [];",
    "    for (var i = 0; i < list.size(); i++) {",
    "        var b = list.get(i), objects = [];",
    "        for (var j = 0; j < b.getObjects().size(); j++) objects.push(String(b.getObjects().get(j)));",
    "        out.push({ engine: String(b.getEngine()), executionId: String(b.getExecutionId()), objects: objects });",
    "    }",
    "    return out;",
    "}",
    "function retainedEngines() {",
    f"    return memory.retained().filter(function (r) {{ return r.name.indexOf('{STEM}') >= 0; }}).map(function (r) {{ return r.engine; }});",
    "}",
]


def probe_lines(engine, script, result, hold_ms):
    return [
        f"var ENGINE = '{engine}', SCRIPT = '{script}', RESULT = '{result}', HOLD_MS = {hold_ms};",
        "var result = { engine: ENGINE };",
        *HELPERS,
        "function runAndWait(timeoutMs) {",
        "    var exec = engines.execScriptFile(SCRIPT), t0 = Date.now();",
        "    while (Date.now() - t0 < timeoutMs) {",
        "        var e = exec.getEngine();",
        "        if (e != null && e.isDestroyed()) return Date.now() - t0;",
        "        sleep(100);",
        "    }",
        "    return -1;",
        "}",
        "try {",
        "    var observer = org.autojs.autojs.runtime.memory.BridgeExecutionObserver.INSTANCE;",
        "    var created = new java.util.concurrent.atomic.AtomicInteger(0);",
        "    observer.setListener(function (engine, kind, id, instance) { if (String(engine) == ENGINE) created.incrementAndGet(); });",
        "    try { result.plainMs = runAndWait(60000); } finally { observer.setListener(null); }",
        "    result.plainObjects = created.get();",
        "    sleep(300);",
        "    result.retainedRightAfter = retainedEngines();",
        "    result.plain = memory.collect(10000).bridgeSessions[ENGINE];",
        "    var held = new java.util.concurrent.CopyOnWriteArrayList(), kinds = new java.util.concurrent.CopyOnWriteArrayList();",
        "    observer.setListener(function (engine, kind, id, instance) {",
        "        if (String(engine) == ENGINE) { held.add(instance); kinds.add(String(kind)); }",
        "    });",
        "    try { result.heldMs = runAndWait(60000); } finally { observer.setListener(null); }",
        "    result.heldObjects = held.size();",
        "    result.heldKinds = String(kinds.toString());",
        "    sleep(300);",
        "    var s = memory.collect(3000);",
        "    result.held = s.bridgeSessions[ENGINE];",
        "    result.heldAll = s.bridgeSessions;",
        "    result.heldSessions = sessions().filter(function (b) { return b.engine == ENGINE; });",
        "    result.retainedHeld = retainedEngines();",
        "    save('holding');",
        "    sleep(HOLD_MS);",
        "    held.clear();",
        "    held = null;",
        "    result.released = memory.collect(10000).bridgeSessions[ENGINE];",
        "    result.releasedSessions = sessions().length;",
        "} catch (e) {",
        "    result.error = String(e).split('\\n')[0].slice(0, 300);",
        "}",
        "save('done');",
    ]


def final_lines(result):
    return [
        f"var RESULT = '{result}';",
        "var result = {};",
        *HELPERS,
        "try {",
        "    var s = memory.collect(10000);",
        "    result.retainedRuntimes = s.retainedRuntimes;",
        "    result.bridgeSessions = s.bridgeSessions;",
        "    result.retained = memory.retained().map(function (r) { return r.name + ' (' + r.engine + ')'; });",
        "    result.sessions = sessions();",
        "} catch (e) {",
        "    result.error = String(e).split('\\n')[0].slice(0, 300);",
        "}",
        "save('done');",
    ]


def report(ok, text):
    print(f"{'PASS' if ok else 'FAIL'} {text}", flush=True)
    return ok


def shots(serial, out, label):
    ok = True
    for tag, scroll in (("page", None),
                        ("actions", "var list = fragment.getListView(); list.scrollToPosition(list.getAdapter().getItemCount() - 1);")):
        state = page.open_page(serial, page.DIAGNOSTICS, scroll, tag)
        time.sleep(2.5)
        shot = page.screenshot(serial, out, f"bridge-sessions-{label}-{tag}")
        ok &= report(state == "shown", f"screenshot {label} {tag}: {shot}")
    return ok


def wait_phase(serial, path, phases, wait):
    deadline = time.time() + wait
    while time.time() < deadline:
        time.sleep(1)
        result = read_result(serial, path)
        if result and result.get("phase") in phases:
            return result
    return None


def refusal(serial, since):
    """The error code of a plugin that refused to start (e.g. a runtime signed with another key), from logcat since `since`."""
    log = adb(serial, "logcat", "-d", "-T", since, "-s", "ScriptEngineService:E", "GlobalConsole:*").stdout
    codes = re.findall(r"\b([A-Z][A-Z0-9_]*_(?:UNTRUSTED|UNAVAILABLE|NOT_INSTALLED|DISABLED|INCOMPATIBLE)\b[^\n]{0,100})", log)
    return codes[-1].strip() if codes else None


def check_engine(serial, engine, hold, out):
    plugin, name, text = ENGINES[engine]
    if not adb(serial, "shell", "pm", "path", PLUGIN_PREFIX + plugin).stdout.strip():
        print(f"SKIP {engine}: plugin {PLUGIN_PREFIX + plugin} is not installed")
        return True
    script = push(serial, name, text)
    since = adb(serial, "shell", "date '+%m-%d %H:%M:%S.000'").stdout.strip()
    result_path = f"{DEVICE_DIR}/result_{engine}.json"
    adb(serial, "shell", "rm", "-f", result_path)
    probe = push(serial, f"aj6_probe_{engine}.js", "\n".join(probe_lines(engine, script, result_path, hold * 1000)) + "\n")
    start(serial, probe)
    result = wait_phase(serial, result_path, ("holding", "done"), 120)
    if result and result["phase"] == "holding" and out:
        shots(serial, out, f"{engine}-held")
    if result and result["phase"] == "holding":
        result = wait_phase(serial, result_path, ("done",), 60 + hold)
    if result is None:
        return report(False, f"{engine}: no probe result")
    if "error" in result:
        return report(False, f"{engine}: probe error {result['error']}")
    if result["plainObjects"] == 0 and result["heldObjects"] == 0:
        reason = refusal(serial, since)
        if reason:
            print(f"SKIP {engine}: the plugin refused to run the script, no bridge object was created ({reason})")
            return True
    ok = True
    expected = ["node"] if engine == "node" else []
    ok &= report(result["plainMs"] >= 0 and result["plain"] == 0 and result["retainedRightAfter"] == expected,
                 f"{engine} plain run: ended after {result['plainMs']} ms, retained right after {result['retainedRightAfter']}, "
                 f"bridgeSessions.{engine} after collect {result['plain']}")
    sessions = result["heldSessions"]
    objects = sorted({o for b in sessions for o in b["objects"]})
    ok &= report(result["heldMs"] >= 0 and result["held"] >= 1 and len(sessions) >= 1,
                 f"{engine} held run: {result['heldObjects']} object(s) held ({result['heldKinds']}), bridgeSessions.{engine} "
                 f"{result['held']}, listed {len(sessions)} ({', '.join(objects) or '-'}), retained {result['retainedHeld']}")
    ok &= report(result["released"] == 0 and result["releasedSessions"] == 0,
                 f"{engine} released: bridgeSessions.{engine} {result['released']}, listed sessions {result['releasedSessions']}")
    return ok


def final_check(serial, out):
    result_path = f"{DEVICE_DIR}/result_final.json"
    adb(serial, "shell", "rm", "-f", result_path)
    start(serial, push(serial, "aj6_probe_final.js", "\n".join(final_lines(result_path)) + "\n"))
    result = wait_phase(serial, result_path, ("done",), 60)
    if result is None or "error" in result:
        return report(False, f"final: {result and result.get('error') or 'no probe result'}")
    counts = result["bridgeSessions"]
    ok = report(result["retainedRuntimes"] == 0 and not any(counts.values()) and not result["sessions"],
                f"final collect: retainedRuntimes {result['retainedRuntimes']} {result['retained']}, bridgeSessions {counts}, "
                f"listed sessions {len(result['sessions'])}")
    if out:
        ok &= shots(serial, out, "final")
    return ok


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--engines", default=",".join(ENGINES))
    parser.add_argument("--hold", type=int, default=6, help="seconds the probe keeps the session objects")
    parser.add_argument("--shots", type=pathlib.Path, help="directory for screenshots of the memory diagnostics page")
    parser.add_argument("--final", action="store_true", help="only check that nothing is left after a collect")
    args = parser.parse_args()
    serial = args.serial

    package = adb(serial, "shell", "dumpsys", "package", PACKAGE).stdout
    version = next((line.split("versionCode=")[1].split()[0] for line in package.splitlines() if "versionCode=" in line), None)
    if version is None:
        sys.exit(f"{PACKAGE} is not installed on {serial}")
    print(f"host versionCode {version}, debuggable {'DEBUGGABLE' in package}")
    if "mWakefulness=Awake" not in adb(serial, "shell", "dumpsys", "power").stdout:
        sys.exit("Turn the screen on first")
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR)
    try:
        if args.final:
            ok = final_check(serial, args.shots)
        else:
            ok = True
            for engine in [e.strip() for e in args.engines.split(",") if e.strip()]:
                # Two screenshots take about 9 s; the held session must still be listed in the second one.
                ok &= check_engine(serial, engine, max(args.hold, 16) if args.shots else args.hold, args.shots)
    finally:
        adb(serial, "shell", "rm", "-rf", DEVICE_DIR, page.DEVICE_DIR)
    print("ALL PASS" if ok else "SOME FAILED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
