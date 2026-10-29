"""Run the runtime leak gate (docs/memory/ROADMAP.md T3) on one device and write the evidence.

Each scenario and mode runs in its own instrumentation process (`am instrument` restarts the
host), so a leak in one scenario cannot distort the next. The device-side test prints one JSON
report per scenario and mode; this script collects them, writes
docs/memory/evidence/gate-<model>-<versionCode>[-<label>]-<yyyyMMdd>.md and the same-named .json,
and prints one line per scenario with the verdict and the six T1 numbers.

The runner never installs APKs, never taps the screen and never changes settings. Build and
install the debug host and its test APK first (see tools/memory/README.md). Dialogs that need a
person (screen capture consent for S4a) are confirmed by the maintainer on the phone.

`--suite bridge` runs the bridge leak gate of docs/memory-phase2/ROADMAP.md (Node ND1-ND13, Bun
BN1-BN5, QuickJS QJ1-QJ5, test class BridgeLeakGateDeviceTest) instead and writes to
docs/memory-phase2/evidence. Around each of its
instrumentation runs it changes two things and puts them back, also when a run fails or is
interrupted: `settings put global hidden_api_policy 1` for the binding probe (ROADMAP D9), and
`pm grant` of the permissions a scenario needs when the host lacks them (ND12 RECORD_AUDIO,
ROADMAP D11; ND7c CAMERA, appendix C Q4), revoked afterwards.

`--attach <runId> --attach-dir <dir>` runs nothing: it writes the same report from the files a
device-side run left behind (tools/memory/device_gate.sh, started with nohup so that a dropped adb
link does not end the test; ROADMAP appendix C Q51 (b)) after they were pulled into <dir>.
"""
import argparse
import datetime
import json
from pathlib import Path
import re
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "sdk37"))
import physical_device  # noqa: E402

TEST_CLASS = "org.autojs.autojs.memory.RuntimeLeakGateDeviceTest"
STATUS_KEY = "aj6mem_json"
LOG_TAG = "AJ6MEM"
SCENARIOS = {
    "S1": "s01EmptyScript",
    "S2": "s02ThreadsAndPool",
    "S3": "s03NestedTimers",
    "S4a": "s04aCaptureScreen",
    "S4b": "s04bReadAndFindImage",
    "S4c": "s04cCopyClipResize",
    "S4d": "s04dMatchTemplateOneShot",
    "S4e": "s04eThreadedImages",
    "S4f": "s04fCaptureScreenNoRecycle",
    "S5": "s05UiWithWebView",
    "S6": "s06Floaty",
    "S7": "s07EventsKeysSensors",
    "S7b": "s07bSelectorFindLoop",
    "S8": "s08JavaAdapterAndDex",
    "S9": "s09ExecScriptFileChain",
    "S10": "s10HttpAndWebSocket",
    "S11": "s11DeviceReceivers",
    "S12": "s12PluginBinding",
    "S13": "s13SpeechSession",
    "S14": "s14ConsoleWindow",
    "S15": "s15InjectableWebView",
    "S16": "s16NestedUiScripts",
}
MODES = {"normal": "N", "force": "F"}
CAPTURE_SCENARIOS = {"S4a", "S4f"}
# Scenarios that fix their own loop counts in GateScenarios.kt (the memoryGateWarmups / memoryGateIterations arguments
# do not apply to them); --attach checks a device-side report against these instead of the call's values.
SCENARIO_PARAMS = {"S4f": {"warmups": 1, "iterations": 3}}
DEVICE_DIR = "/sdcard/Android/data/{package}/files/memory-gate"

BRIDGE_TEST_CLASS = "org.autojs.autojs.memory.bridge.BridgeLeakGateDeviceTest"
BRIDGE_SCENARIOS = {
    "ND1": "nd01EmptyScript",
    "ND2": "nd02HostCapabilities",
    "ND3": "nd03Callbacks",
    "ND4": "nd04PluginKilled",
    "ND5": "nd05UiSession",
    "ND6": "nd06Compose",
    "ND7a": "nd07aWebPageAndAction",
    "ND7b": "nd07bWebDownload",
    "ND7c": "nd07cWebMedia",
    "ND8": "nd08ScreenCapture",
    "ND9": "nd09Overlay",
    "ND10": "nd10Dialog",
    "ND11": "nd11ImageHandles",
    "ND12": "nd12AudioRecording",
    "ND13": "nd13ColorOsRebind",
    "BN1": "bn01EmptyScript",
    "BN2": "bn02HostCapabilities",
    "BN3": "bn03Callbacks",
    "BN4": "bn04PluginKilled",
    "BN5": "bn05ColorOsRebind",
    "QJ1": "qj01EmptyScript",
    "QJ2": "qj02FileSourceAndOutput",
    "QJ3": "qj03OutputCallbacks",
    "QJ4": "qj04PluginKilled",
    "QJ5": "qj05ColorOsRebind",
    "LU1": "lu01EmptyScript",
    "LU2": "lu02HostCapabilities",
    "LU3": "lu03OutputCallbacks",
    "LU4": "lu04PluginKilled",
    "LU5": "lu05ColorOsRebind",
    "PY1": "py01EmptyScript",
    "PY2": "py02HostCapabilities",
    "PY3": "py03OutputCallbacks",
    "PY4": "py04PluginKilled",
    "PY5": "py05ColorOsRebind",
}
NODE_PLUGIN = "io.github.supermonster003.autojs6.plugin.nodejs"
BUN_PLUGIN = "io.github.supermonster003.autojs6.plugin.bun.runtime"
QUICKJS_PLUGIN = "io.github.supermonster003.autojs6.plugin.quickjs.runtime"
LUA_PLUGIN = "io.github.supermonster003.autojs6.plugin.lua.runtime"
PYTHON_PLUGIN = "io.github.supermonster003.autojs6.plugin.python.runtime"
# Engine of a bridge scenario by its prefix (docs/memory-phase2/ROADMAP.md T2), and the plugin package of each engine.
BRIDGE_ENGINES = {"ND": "node", "BN": "bun", "QJ": "quickjs", "LU": "lua", "PY": "python"}
BRIDGE_PLUGINS = {"node": NODE_PLUGIN, "bun": BUN_PLUGIN, "quickjs": QUICKJS_PLUGIN, "lua": LUA_PLUGIN, "python": PYTHON_PLUGIN}
SUITES = {
    "runtime": {"name": "memory-gate", "testClass": TEST_CLASS, "scenarios": SCENARIOS, "capture": CAPTURE_SCENARIOS,
                "out": ROOT / "docs" / "memory" / "evidence", "grants": {}, "hiddenApi": False, "plugins": None},
    # ROADMAP D11 grants RECORD_AUDIO for ND12; CAMERA for ND7c is appendix C Q4 (recommended, applied ahead).
    "bridge": {"name": "bridge-gate", "testClass": BRIDGE_TEST_CLASS, "scenarios": BRIDGE_SCENARIOS, "capture": {"ND8"},
               "out": ROOT / "docs" / "memory-phase2" / "evidence", "hiddenApi": True, "plugins": BRIDGE_PLUGINS,
               "grants": {"ND7c": ["android.permission.CAMERA"], "ND12": ["android.permission.RECORD_AUDIO"]}},
}


def bridge_engine(scenario):
    """Engine of a bridge scenario (node, bun, quickjs, lua or python), or None for the first gate."""
    return BRIDGE_ENGINES.get(scenario[:2])


def plugin_commits(text, engines):
    """--plugin-commit: one hash for every engine of the run, or `engine=hash` pairs separated by commas
    (e.g. bun=fb879f3,quickjs=0bcee52). Returns {engine: hash}; raises ValueError on an unknown engine or a bad hash."""
    if not text:
        return {}
    pattern = r"[0-9a-f]{4,40}(-dirty)?"
    if "=" not in text:
        if not re.fullmatch(pattern, text):
            raise ValueError("--plugin-commit must be a commit hash or engine=hash pairs")
        return {engine: text for engine in engines}
    commits = {}
    for item in text.split(","):
        engine, _, commit = item.strip().partition("=")
        if engine not in BRIDGE_PLUGINS or not re.fullmatch(pattern, commit):
            raise ValueError(f"--plugin-commit: bad item {item!r}; use engine=hash with engine one of {', '.join(BRIDGE_PLUGINS)}")
        commits[engine] = commit
    return commits


def scenario_list(text, scenarios=None):
    scenarios = SCENARIOS if scenarios is None else scenarios
    if not text:
        return list(scenarios)
    chosen = []
    for item in text.split(","):
        key = next((name for name in scenarios if name.lower() == item.strip().lower()), None)
        if key is None:
            raise argparse.ArgumentTypeError(f"Unknown scenario {item!r}; known: {', '.join(scenarios)}")
        if key not in chosen:
            chosen.append(key)
    return chosen


def parse_reports(text):
    """JSON reports printed as instrumentation status lines, in output order."""
    reports = []
    prefix = f"INSTRUMENTATION_STATUS: {STATUS_KEY}="
    for line in text.splitlines():
        if line.startswith(prefix):
            try:
                reports.append(json.loads(line[len(prefix):]))
            except json.JSONDecodeError:
                pass
    return reports


def parse_logcat_reports(text, run_id):
    """Reassemble `JSON <runId> <id> <i>/<n> <chunk>` lines written under the AJ6MEM tag."""
    parts = {}
    pattern = re.compile(r"JSON (\S+) (\S+) (\d+)/(\d+) (.*)$")
    for line in text.splitlines():
        if LOG_TAG not in line:
            continue
        found = pattern.search(line)
        if not found or found[1] != run_id:
            continue
        parts.setdefault(found[2], {})[int(found[3])] = (int(found[4]), found[5])
    reports = []
    for chunks in parts.values():
        total = next(iter(chunks.values()))[0]
        if sorted(chunks) == list(range(1, total + 1)):
            try:
                reports.append(json.loads("".join(chunks[i][1] for i in range(1, total + 1))))
            except json.JSONDecodeError:
                pass
    return reports


def test_outcome(text):
    """Outcome of the single selected test: pass, fail, skipped or crashed."""
    codes = [int(code) for code in re.findall(r"^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$", text, re.MULTILINE)]
    if "Process crashed." in text or "INSTRUMENTATION_FAILED" in text:
        return "crashed"
    if -4 in codes or -3 in codes:
        return "skipped"
    if -2 in codes or -1 in codes:
        return "fail"
    if 0 in codes:
        return "pass"
    return "unknown"


def crash_excerpt(crash):
    """The last fatal signal or exception of a crash buffer dump with 200 characters before it, 8000 at most."""
    start = max(crash.rfind("Fatal signal"), crash.rfind("*** *** ***"), crash.rfind("FATAL EXCEPTION"))
    return crash[max(0, start - 200):][:8000] if start >= 0 else crash[-6000:]


def read_optional(path):
    return path.read_text(encoding="utf-8", errors="replace") if path.is_file() else None


def pick_reports(text, pairs, run_id, json_text, logcat_text):
    """Reports of the scenario / mode pairs, in pair order: the instrumentation status lines first, then the JSON
    the test wrote per pair (`json_text(scenario, mode)`, None when there is none), then the AJ6MEM logcat chunks
    (`logcat_text()`), each only for the pairs still missing."""
    found = [r for r in parse_reports(text) if (r.get("scenario"), r.get("mode")) in pairs]
    for s, m in pairs:
        if any((r.get("scenario"), r.get("mode")) == (s, m) for r in found):
            continue
        raw = json_text(s, m)
        if raw:
            found.append(json.loads(raw))
    if len(found) < len(pairs):
        seen = {(r.get("scenario"), r.get("mode")) for r in found}
        found += [r for r in parse_logcat_reports(logcat_text(), run_id)
                  if (r.get("scenario"), r.get("mode")) in pairs and (r.get("scenario"), r.get("mode")) not in seen]
    found.sort(key=lambda r: pairs.index((r.get("scenario"), r.get("mode"))))
    return found


def attach_reports(folder, run_id, pairs, capture_scenarios=(), expected=None):
    """Reports of a run that tools/memory/device_gate.sh made on the device itself, from the files pulled into
    `folder`: per scenario and mode `<runId>-<S>-<mode>.log` (the `am instrument -w -r` output between the
    script's `start`, `projection_before=`, `rc=`, `projection_after=` and `end` lines), `.json` (the report the
    test wrote), `.logcat` (the AJ6MEM lines) and `.crash` (the crash buffer). A log whose start line carries
    `launch=detached` came from tools/memory/detached_gate.sh (`am instrument` without -w): it needs an `end` line
    instead of `rc=`, and the outcome is read from the TestRunner lines of the `.logcat`. Every selected pair needs a
    finished log; a report of another run, or one made with other parameters than `expected` (warmups,
    iterations, pollMs, forceDelayMs; a scenario listed in SCENARIO_PARAMS is checked against its own counts), is refused. Returns the reports, the pairs without one, the
    PROJECT_MEDIA record and the device-clock window of the logs."""
    reports, failures, projection, window = [], [], {"allowedDuring": []}, {}
    for s, m in pairs:
        log = folder / f"{run_id}-{s}-{m}.log"
        if not log.is_file():
            raise RuntimeError(f"No device-side log for {s} {m}: expected {log}")
        text = read_optional(log)
        detached = re.search(r"^start .*\blaunch=detached\b", text, re.MULTILINE) is not None
        if detached:
            if not re.search(r"^end \S+", text, re.MULTILINE):
                raise RuntimeError(f"{log.name} has no end line: detached_gate.sh collect has not run for {s} {m}")
            window["launch"] = "detached"
        elif not re.search(r"^rc=-?\d+\s*$", text, re.MULTILINE):
            raise RuntimeError(f"{log.name} has no rc= line: the device-side run of {s} {m} has not finished")
        started = re.search(r"^start (\S+)", text, re.MULTILINE)
        ended = re.search(r"^end (\S+)", text, re.MULTILINE)
        if started:
            window["started"] = min(window.get("started", started[1]), started[1])
        if ended:
            window["finished"] = max(window.get("finished", ended[1]), ended[1])
        logcat_file = folder / f"{run_id}-{s}-{m}.logcat"
        outcome = detached_outcome(read_optional(logcat_file) or "") if detached else test_outcome(text)
        if s in capture_scenarios:
            before = re.search(r"^projection_before=.*PROJECT_MEDIA: (\w+)", text, re.MULTILINE)
            after = re.search(r"^projection_after=.*PROJECT_MEDIA: (\w+)", text, re.MULTILINE)
            projection.setdefault("before", before[1] if before else "default")
            projection["allowedDuring"].append(f"{s} {m}")
            projection["restoredTo"] = after[1] if after else "default"
        found = pick_reports(text, [(s, m)], run_id,
                             lambda s2, m2: read_optional(folder / f"{run_id}-{s2}-{m2}.json"),
                             lambda s2=s, m2=m: read_optional(folder / f"{run_id}-{s2}-{m2}.logcat") or "")
        for report in found:
            if report.get("runId") not in (None, run_id):
                raise RuntimeError(f"{log.name}: a report of run {report.get('runId')}, expected {run_id}")
            for key, want in (expected or {}).items():
                want = SCENARIO_PARAMS.get(s, {}).get(key, want)
                if key in report and report[key] != want:
                    raise RuntimeError(f"{log.name}: the device-side run used {key} {report[key]}, this call expects {want}")
            report["instrumentation"] = outcome
            if detached:
                report["launch"] = "detached"
            reports.append(report)
            print(summary_line(report))
            for line in leak_lines(report)[:3]:
                print(f"     {line[:220]}")
        if not found:
            crashed = outcome == "crashed" or (detached and outcome == "unknown")
            crash = crash_excerpt(read_optional(folder / f"{run_id}-{s}-{m}.crash") or "") if crashed else ""
            failures.append({"scenario": s, "mode": m, "outcome": outcome, "tail": text[-1500:], "crashLog": crash})
            print(f"{s:<4} {MODES[m]} NO REPORT (instrumentation {outcome})")
    return reports, failures, projection, window


def detached_outcome(logcat):
    """Outcome of a detached run (`am instrument` without -w, tools/memory/detached_gate.sh) from the TestRunner
    lines of its logcat: AndroidJUnitRunner logs `started:`, then `finished:`, `failed:`, `ignored:` or
    `assumption failed:` per test, and `run finished: N tests, M failed, K ignored`. Without a `run finished` line
    the outcome is unknown (the process died or the run was still going when the logcat was taken)."""
    lines = [line for line in logcat.splitlines() if re.search(r"\bTestRunner\b", line)]
    text = "\n".join(lines)
    if re.search(r"TestRunner[^:]*: failed: ", text):
        return "fail"
    if re.search(r"TestRunner[^:]*: (ignored|assumption failed): ", text):
        return "skipped"
    done = re.search(r"TestRunner[^:]*: run finished: (\d+) tests?, (\d+) failed, (\d+) ignored", text)
    if done and re.search(r"TestRunner[^:]*: finished: ", text) and done[1] == "1" and done[2] == "0" and done[3] == "0":
        return "pass"
    return "unknown"


def mb(value):
    return "-" if value is None else f"{value / 1048576:+.2f}"


def delta(value):
    return "-" if value is None else f"{value:+d}"


def is_bridge(report):
    return report.get("suite") == "bridge-gate"


def bindings_text(metrics):
    """Binding counts of docs/memory-phase2/ROADMAP.md T1 (h): host bridge registries against the start (h, summed) and
    system-side connections of the host to the plugin packages (s; n/a when LoadedApk.mServices was unreadable)."""
    registries = metrics.get("bridgeRegistriesDelta")
    host = "-" if registries is None else delta(sum(registries.values()))
    system = (metrics.get("bindings") or {}).get("pluginConnectionsDelta")
    return f"h{host} " + ("s n/a" if system is None else f"s{delta(system)}")


def pss_text(metrics):
    """Plugin process PSS at the end in MB, with the change since the second measured run (T1 (i), recorded only)."""
    pss = metrics.get("pluginPss") or {}
    final = pss.get("final") if isinstance(pss.get("final"), dict) else {}
    if final.get("totalPssKb") is None:
        return "-"
    change = pss.get("deltaKb")
    return f"{final['totalPssKb'] / 1024:.1f}" + ("" if change is None else f" ({kb(change)})")


def cell(report):
    """Appendix A cell: retained / Java MB / native MB / threads / contexts / engines. Bridge gate (docs/memory-phase2/ROADMAP.md
    appendix A): retained / Java MB / native MB / script threads, bridge threads / bindings / engines / plugin PSS MB."""
    if report.get("status") == "skipped":
        return "skipped"
    m = report.get("metrics") or {}
    if is_bridge(report):
        return (f"{m.get('retained', '-')} / {mb(m.get('javaHeapDeltaBytes'))} / {mb(m.get('nativeHeapDeltaBytes'))} / "
                f"{delta(m.get('scriptThreadDelta'))} b{delta(m.get('bridgeThreadDelta'))} / {bindings_text(m)} / "
                f"{m.get('engines', '-')} / {pss_text(m)}")
    return (f"{m.get('retained', '-')} / {mb(m.get('javaHeapDeltaBytes'))} / {mb(m.get('nativeHeapDeltaBytes'))} / "
            f"{delta(m.get('scriptThreadDelta'))} / {delta(m.get('rhinoContextDelta'))} / {m.get('engines', '-')}")


def summary_line(report):
    scenario, mode = report.get("scenario", "?"), MODES.get(report.get("mode"), "?")
    status = report.get("status", "?").upper()
    if report.get("status") == "skipped":
        return f"{scenario:<4} {mode} SKIPPED {'; '.join(report.get('reasons') or [])}"
    m = report.get("metrics") or {}
    native = m.get("native") or {}
    extra = (f" peak={mb(native.get('nativePeakMaxBytes'))}MB images={delta(native.get('imagesLiveDelta'))} "
             f"media={native.get('openMediaImages')} mats={delta(native.get('matsLiveDelta'))}") if native else ""
    if is_bridge(report):
        extra += f" bindings={bindings_text(m)} bridgeThreads={delta(m.get('bridgeThreadDelta'))} pss={pss_text(m)}"
    return (f"{scenario:<4} {mode} {status:<5} retained={m.get('retained')}/{report.get('iterations')} "
            f"java={mb(m.get('javaHeapDeltaBytes'))}MB native={mb(m.get('nativeHeapDeltaBytes'))}MB "
            f"threads={delta(m.get('scriptThreadDelta'))} contexts={delta(m.get('rhinoContextDelta'))} engines={m.get('engines')}{extra}")


def kb(value):
    return "-" if value is None else f"{value / 1024:+.2f}"


def bridge_stats(report):
    """Largest value of every gateStat the script printed, over all runs of the report."""
    stats = {}
    for sample in report.get("samples") or []:
        for key, value in (sample.get("stats") or {}).items():
            stats[key] = max(stats.get(key, value), value)
    return ", ".join(f"{key} {value:g}" for key, value in stats.items())


def bridge_cell(report):
    """Bridge detail: registries above the start / system bindings start -> final / new bridge threads / plugin PSS MB
    at the start, after measured run 2 and at the end."""
    m = report.get("metrics") or {}
    registries = ", ".join(f"{name.split('.', 1)[-1]} {delta(value)}" for name, value in (m.get("bridgeRegistriesDelta") or {}).items()) or "+0"
    bindings = m.get("bindings") or {}

    def connections(snapshot):
        snapshot = snapshot or {}
        return "n/a" if "pluginConnections" not in snapshot else f"{snapshot['pluginConnections']}/{snapshot.get('serviceConnections')}"

    pss = m.get("pluginPss") or {}
    phases = " / ".join("-" if not isinstance(pss.get(key), dict) else f"{pss[key].get('totalPssKb', 0) / 1024:.1f}"
                        for key in ("start", "afterSecondRun", "final"))
    threads = ", ".join(m.get("bridgeThreadsNew") or []).replace("|", "/") or "-"
    return f"{registries} | {connections(bindings.get('start'))} -> {connections(bindings.get('final'))} | {threads} | {phases}"


def session_text(report):
    """Bun / QuickJS session objects counted through VMDebug (docs/memory-phase2/ROADMAP.md P2.1): reachable instances above
    the start at the end, and the longest wait after a run until they were back at the start; empty for Node."""
    sessions = (report.get("metrics") or {}).get("sessionInstances")
    if not sessions:
        return ""
    final = sessions.get("final") or {}
    if final and all(value < 0 for value in final.values()):
        return "session n/a"
    retained = sessions.get("retained") or {}
    text = "session " + (", ".join(f"{kind.split('.', 1)[-1]} {delta(count)}" for kind, count in retained.items()) or "+0")
    cleared = [sample["sessionClearedMs"] for sample in report.get("samples") or [] if sample.get("sessionClearedMs") is not None]
    return text + (f", cleared <= {max(cleared)} ms" if cleared else "")


def native_cell(report):
    """Native detail (ROADMAP P3.1): peak in run MB / meminfo Native Pss, Heap Alloc, Graphics, Unknown MB /
    images, open media images, Mats / instance deltas Bitmap, cv Mat, AccessibilityNodeInfo."""
    native = (report.get("metrics") or {}).get("native")
    if not native:
        return "-"
    mem = native.get("meminfoDeltaKb") or {}
    inst = native.get("instanceDeltas") or {}
    memory = "/".join(kb(mem.get(key)) for key in ("nativePssKb", "nativeHeapAllocKb", "graphicsKb", "unknownPssKb")) if mem else "-"
    instances = "/".join("-" if inst.get(key) is None else f"{inst[key]:+d}" for key in ("bitmap", "cvMat", "accessibilityNode")) if inst else "-"
    nodes = (native.get("instanceDeltasFromBaseline") or {}).get("accessibilityNode")
    if nodes is not None and inst:
        instances += f" (nodes {nodes:+d} vs warm-ups)"
    return (f"{mb(native.get('nativePeakMaxBytes'))} | {memory} | {delta(native.get('imagesLiveDelta'))} / "
            f"{native.get('openMediaImages', '-')} / {delta(native.get('matsLiveDelta'))} | {instances}")


def classes_cell(report):
    """ROADMAP P4.4, against the end of the warm-ups: loaded classes (with the median growth per measured run where judged) /
    VMDebug Class objects / VMDebug DexClassLoader / host registered DEX loaders / VMDebug WebView."""
    native = (report.get("metrics") or {}).get("native") or {}
    base, final = report.get("nativeBaseline") or {}, report.get("nativeFinal") or {}

    def counter(key):
        a, b = base.get(key), final.get(key)
        return b - a if isinstance(a, int) and isinstance(b, int) and a >= 0 and b >= 0 else None

    inst = native.get("instanceDeltasFromBaseline") or {}
    loaded = counter("loadedClasses")
    values = [loaded, inst.get("javaClass"), inst.get("dexClassLoader"), counter("hostDexLoaders"), inst.get("webView")]
    if all(value is None for value in values):
        return "-"
    cells = [delta(value) for value in values]
    median = ((native.get("perRunCounterGrowth") or {}).get("loadedClasses") or {}).get("median")
    if median is not None:
        cells[0] += f" (median {median:+d} per run)"
    return " / ".join(cells)


def stats_text(report):
    stats = ((report.get("metrics") or {}).get("native") or {}).get("scriptStatsMax") or {}
    return ", ".join(f"{key} {value:g}" for key, value in stats.items())


def leak_lines(report):
    analysis = ((report.get("heap") or {}).get("analysis")) or {}
    lines = []
    noun = "object(s)" if is_bridge(report) else "runtime(s)"
    for leak in analysis.get("leaks") or []:
        lines.append(f"{leak.get('runtimes')} {noun}, root {leak.get('gcRoot')}: {leak.get('summary')}")
    alternate = analysis.get("alternate") or {}
    for leak in alternate.get("leaks") or []:
        lines.append(f"ignoring {alternate.get('ignoring')}: {leak.get('runtimes')} runtime(s), root {leak.get('gcRoot')}: {leak.get('summary')}")
    if (report.get("heap") or {}).get("error"):
        lines.append("heap evidence error: " + report["heap"]["error"])
    # Terminated threads whose LooperHelper entries stayed (docs/memory-phase2/ROADMAP.md C34).
    looper = looper_heap(report)
    for leak in (looper.get("analysis") or {}).get("leaks") or []:
        lines.append(f"LooperHelper thread: {leak.get('runtimes')} thread(s), root {leak.get('gcRoot')}: {leak.get('summary')}")
    if looper.get("error"):
        lines.append("looper heap evidence error: " + looper["error"])
    return lines


def looper_heap(report):
    return (((report.get("metrics") or {}).get("looperThreads") or {}).get("heap")) or {}


def markdown(meta, reports, failures):
    identity = meta["device"]
    bridge = meta.get("suite") == "bridge-gate"
    # Reports before P2.1 name one Node plugin as `plugin` with `pluginCommit`.
    plugins = meta.get("plugins") or ([{**meta["plugin"], "commit": meta.get("pluginCommit")}] if meta.get("plugin") else [])
    if bridge:
        cell_lines = [
            "- Cell (docs/memory-phase2/ROADMAP.md appendix A): retained / Java heap MB / native heap MB / script threads, "
            "b bridge threads / bindings: h host bridge registries vs start, s system connections to the plugin vs start / "
            "live engines / plugin PSS MB at the end (change since measured run 2)",
            "- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), script threads, engines and bridge "
            "registries back to start (the connection pools of the Node, Bun and QuickJS clients may keep 1), Bun / QuickJS "
            "session objects back to start, system connections <= start + 1, bridge threads back to "
            "the end of the warm-ups (idle workers of the cached binder-call pool get up to 65 s to expire first, Q6); plugin PSS "
            "recorded only (R9)",
        ]
    else:
        cell_lines = [
            "- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)",
            "- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start",
        ]
    lines = [
        f"# {'Bridge' if bridge else 'Memory'} gate {identity.get('model')} ({meta['versionCode']}) {meta['date']}",
        "",
        f"- Device: {identity.get('manufacturer')} {identity.get('model')}, API {identity.get('sdk')}, serial {identity.get('serial')}",
        f"- Host: {meta['package']} versionCode {meta['versionCode']} ({meta.get('versionName')}), debuggable",
        *[f"- Plugin: {plugin.get('package')} " + (f"versionCode {plugin.get('versionCode')} ({plugin.get('versionName')})"
                                                    if plugin.get("versionCode") else "not installed")
          + f", commit {plugin.get('commit') or '-'}" for plugin in (plugins if bridge else [])],
        *([f"- hidden_api_policy (ROADMAP D9): 1 during each run, before `{meta['hiddenApiPolicy'].get('before')}`, "
           f"afterwards `{meta['hiddenApiPolicy'].get('restoredTo')}`"] if meta.get("hiddenApiPolicy") else []),
        *([f"- Granted for the run and revoked afterwards (ROADMAP D11 / Q4): " + "; ".join(
            f"{g['scenario']} {g['permission'].rsplit('.', 1)[-1]} ({'revoked' if g.get('revoked') else 'kept: ' + str(g.get('error'))})"
            for g in meta["grants"])] if meta.get("grants") else []),
        f"- Run: {meta['runId']}, label {meta.get('label') or '-'}, {meta['started']} -> {meta['finished']}",
        f"- Device state at start: {meta.get('deviceState')}",
        *([f"- Merged runs: " + "; ".join(f"{r.get('runId')} ({', '.join(r.get('scenarios') or ['all'])})"
                                         for r in meta["mergedRuns"])] if meta.get("mergedRuns") else []),
        *([f"- Stopped early: {meta['interrupted']}"] if meta.get("interrupted") else []),
        *([f"- Attached (ROADMAP appendix C Q51 (b)): `am instrument` without -w, started and collected by single adb commands "
           f"({meta['attached'].get('deviceScript')}) into {meta['attached'].get('dir')}; the test outcome is read from the "
           f"TestRunner logcat lines; the run times are the host clock; without -w the test has no UiAutomation connection, "
           f"so the meminfo column (dumpsys meminfo through that shell) stays empty"
           if meta["attached"].get("launch") == "detached" else
           f"- Attached (ROADMAP appendix C Q51 (b)): a device-side run of {meta['attached'].get('deviceScript')}, collected "
           f"from {meta['attached'].get('dir')}; the run times are the device clock"] if meta.get("attached") else []),
        *([f"- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during {', '.join(meta['projectionAppOp']['allowedDuring'])}, "
           f"before `{meta['projectionAppOp'].get('before')}`, afterwards `{meta['projectionAppOp'].get('restoredTo')}`"]
          if meta.get("projectionAppOp") else []),
        f"- Warm-ups {meta['warmups']}, iterations {meta['iterations']}, poll {meta['pollMs']} ms, force delay {meta['forceDelayMs']} ms, capture {meta['capture']}, meminfo {meta.get('meminfo', 'off')}",
        *cell_lines,
        "",
        "| Scenario | Mode | Status | Cell | Reasons |",
        "| --- | --- | --- | --- | --- |",
    ]
    for report in reports:
        reasons = "; ".join(report.get("reasons") or []).replace("|", "/")
        lines.append(f"| {report.get('scenario')} {report.get('title', '')} | {MODES.get(report.get('mode'), '?')} | "
                     f"{report.get('status')} | {cell(report)} | {reasons} |")
    for item in failures:
        signal = next((l.strip() for l in (item.get("crashLog") or "").splitlines()
                       if re.search(r"Fatal signal|Abort message|FATAL EXCEPTION", l)), "")
        detail = f"; {signal[:160]}".replace("|", "/") if signal else ""
        lines.append(f"| {item['scenario']} | {MODES.get(item['mode'], '?')} | no report | - | instrumentation {item['outcome']}{detail} |")
    bridged = [report for report in reports if is_bridge(report) and report.get("metrics")]
    if bridged:
        lines += ["", "## Bridge detail (docs/memory-phase2/ROADMAP.md T1 (d) / (h) / (i))", "",
                  "- Registries: host bridge tables whose size differs from the start (StaticRegistries.bridgeCounts, and the "
                  "AidlPluginHost connection pools of the Bun and QuickJS clients)",
                  "- Bindings: connections of the host to the plugin / all bound service connections in LoadedApk.mServices, "
                  "before the first run -> at the end (n/a: unreadable)",
                  "- New threads: bridge threads alive at the end that were not alive at the end of the warm-ups",
                  "- Plugin PSS MB: all processes of the plugin before the first run / after measured run 2 / at the end (recorded only, R9)",
                  "- Script: largest value of each gateStat over all runs; ended by error: runs of an expected failure (ND4 / BN4 / QJ4); "
                  "session: Bun / QuickJS session objects above the start at the end (VMDebug counts), and the longest wait "
                  "after a run until they were back",
                  "", "| Scenario | Mode | Registries | Bindings | New threads | Plugin PSS MB | Script |",
                  "| --- | --- | --- | --- | --- | --- | --- |"]
        for report in bridged:
            script = bridge_stats(report)
            if "runsEndedByError" in report:
                script = (script + "; " if script else "") + f"ended by error {report['runsEndedByError']}"
            if session_text(report):
                script = (script + "; " if script else "") + session_text(report)
            lines.append(f"| {report.get('scenario')} | {MODES.get(report.get('mode'), '?')} | {bridge_cell(report)} | {script or '-'} |")
    detailed = [report for report in reports if (report.get("metrics") or {}).get("native")]
    if detailed:
        lines += ["", "## Native detail (ROADMAP P3.1)", "",
                  "- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)",
                  "- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss",
                  "- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)",
                  "- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, "
                  "then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)",
                  "- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; "
                  "S8 fails when they grow in most measured runs, one-time steps do not count) / "
                  "VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / "
                  "VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)",
                  "", "| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |",
                  "| --- | --- | --- | --- | --- | --- | --- | --- |"]
        for report in detailed:
            lines.append(f"| {report.get('scenario')} | {MODES.get(report.get('mode'), '?')} | {native_cell(report)} | "
                         f"{classes_cell(report)} | {stats_text(report) or '-'} |")
    leaks = [(report, leak_lines(report)) for report in reports if leak_lines(report)]
    if leaks:
        lines += ["", "## Shortest strong reference paths (shark, " + ("bridge session objects and exited ScriptRuntime instances)"
                                                                     if bridge else "exited ScriptRuntime instances)"), ""]
        for report, entries in leaks:
            analysis = ((report.get("heap") or {}).get("analysis")) or {}
            looper = (looper_heap(report).get("analysis") or {}).get("leaks")
            if "instances" in analysis:
                found = ", ".join(f"{name} {count}" for name, count in analysis["instances"].items())
            elif not analysis and looper_heap(report):
                analysis, looper = looper_heap(report).get("analysis") or {}, None
                found = f"{analysis.get('threadInstances', '?')} terminated LooperHelper thread(s)"
            else:
                found = f"{analysis.get('exitedRuntimeInstances', '?')} exited runtimes"
            dump = (report.get("heap") or looper_heap(report)).get("dumpMs", "?")
            lines.append(f"### {report.get('scenario')} {MODES.get(report.get('mode'), '?')}: {found} in the dump, "
                         f"dump {dump} ms, analysis {analysis.get('analysisMs', '?')} ms")
            lines.append("")
            for entry in entries:
                lines.append(f"- {entry}")
            alternate = analysis.get("alternate") or {}
            for title, group in (("", analysis.get("leaks")), (f" (second pass ignoring {alternate.get('ignoring')})", alternate.get("leaks")),
                                 (" (LooperHelper thread)", looper)):
                for leak in group or []:
                    lines.append("")
                    lines.append("```")
                    lines += [f"{leak.get('gcRoot')}{title}"] + [f"  {step}" for step in leak.get("path") or []]
                    lines.append("```")
            lines.append("")
    after_last = [report for report in reports if report.get("heapAfterLast")]
    if after_last:
        lines += ["", "## Heap right after the last run (--dump-after-last, docs/memory-phase2/ROADMAP.md C31)", "",
                  "Dumped before the gate waits for collection; bridge class instances, then the largest classes by shallow size "
                  "over the whole dump (app, zygote and image heaps).", ""]
        for report in after_last:
            heap = report["heapAfterLast"]
            analysis = heap.get("analysis") or {}
            histogram = heap.get("histogram") or {}
            found = ", ".join(f"{name} {count}" for name, count in (analysis.get("instances") or {}).items()) or "-"
            total = histogram.get("totalBytes")
            lines.append(f"### {report.get('scenario')} {MODES.get(report.get('mode'), '?')}: {found}"
                         + (f", {total / 1048576:.2f} MB in the dump" if total is not None else ""))
            lines.append("")
            if heap.get("error"):
                lines.append(f"- heap evidence error: {heap['error']}")
            for leak in analysis.get("leaks") or []:
                lines.append(f"- {leak.get('runtimes')} object(s), root {leak.get('gcRoot')}: {leak.get('summary')}")
            for entry in (histogram.get("top") or [])[:10]:
                lines.append(f"- {entry.get('class')}: {entry.get('count')} objects, {entry.get('bytes', 0) / 1048576:.2f} MB")
            lines.append("")
    lines += ["", "Raw reports: the .json file with the same name (one report per line under `results`)."]
    return "\n".join(lines) + "\n"


def write_evidence(path_json, meta, reports, failures):
    body = ",\n".join("    " + json.dumps(report, ensure_ascii=False, separators=(",", ":")) for report in reports)
    head = json.dumps({**meta, "failures": failures}, ensure_ascii=False, indent=2)
    path_json.write_text(head[:-2] + ',\n  "results": [\n' + body + "\n  ]\n}\n", encoding="utf-8")


def merge_evidence(previous, reports, failures, meta, scenarios=None):
    """Keep earlier results for scenario / mode pairs that this run did not produce (same device and versionCode)."""
    scenarios = SCENARIOS if scenarios is None else scenarios
    order = [(s, m) for s in scenarios for m in MODES]
    rerun = {(r.get("scenario"), r.get("mode")) for r in reports} | {(f["scenario"], f["mode"]) for f in failures}
    kept = [r for r in previous.get("results", []) if (r.get("scenario"), r.get("mode")) not in rerun]
    kept_failures = [f for f in previous.get("failures", []) if (f["scenario"], f["mode"]) not in rerun
                     and (f["scenario"], f["mode"]) not in {(r.get("scenario"), r.get("mode")) for r in kept}]
    merged = sorted(kept + reports, key=lambda r: order.index((r.get("scenario"), r.get("mode"))))
    runs = previous.get("mergedRuns") or [{"runId": previous.get("runId"), "started": previous.get("started"),
                                           "finished": previous.get("finished")}]
    meta = {**meta, "started": previous.get("started", meta["started"]), "deviceState": previous.get("deviceState"),
            "scenarios": sorted({r.get("scenario") for r in merged}, key=list(scenarios).index),
            "mergedRuns": runs + [{"runId": meta["runId"], "started": meta["started"], "finished": meta["finished"],
                                   "scenarios": sorted({s for s, _ in rerun}, key=list(scenarios).index)}]}
    if previous.get("grants"):
        meta["grants"] = previous["grants"] + (meta.get("grants") or [])
    earlier = previous.get("plugins") or ([{"engine": "node", **previous["plugin"], "commit": previous.get("pluginCommit")}]
                                          if previous.get("plugin") else [])
    if earlier:
        # Plugins of this run replace the earlier entries of the same package; the rest stay, in engine order.
        packages = {plugin.get("package"): plugin for plugin in earlier}
        packages.update({plugin.get("package"): plugin for plugin in meta.get("plugins") or []})
        order = list(BRIDGE_PLUGINS.values())
        meta["plugins"] = sorted(packages.values(), key=lambda p: order.index(p["package"]) if p.get("package") in order else len(order))
    return merged, kept_failures + failures, meta


def global_setting(device, name):
    """Value of a global setting, or None when it is unset."""
    value = device.shell("settings", "get", "global", name)["stdout"].strip()
    return None if value in ("", "null") else value


def put_global_setting(device, name, value):
    """Sets a global setting, or deletes it when value is None (ROADMAP D9 sets hidden_api_policy 1 for the binding probe)."""
    command = ("delete", "global", name) if value is None else ("put", "global", name, value)
    result = device.shell("settings", *command)
    if result["exitCode"] != 0:
        raise RuntimeError(f"settings {' '.join(command)} failed: {(result['stdout'] + result['stderr']).strip()}")


def granted_permissions(device, package):
    """Runtime permissions the package holds (`granted=true` lines of dumpsys package)."""
    dump = device.shell("dumpsys", "package", package)["stdout"]
    return set(re.findall(r"^\s*(android\.permission\.[A-Z_]+): granted=true", dump, re.MULTILINE))


def projection_mode(device, package):
    """PROJECT_MEDIA app-op mode of the host (`default` when none is set)."""
    found = re.search(r"PROJECT_MEDIA: (\w+)", device.shell("appops", "get", package, "PROJECT_MEDIA")["stdout"])
    return found[1] if found else "default"


def set_projection(device, package, mode):
    """ROADMAP appendix C Q3 (decided 2026-10-20): PROJECT_MEDIA is `allow` only while S4a runs, then `default`."""
    result = device.shell("appops", "set", package, "PROJECT_MEDIA", mode)
    if result["exitCode"] != 0:
        raise RuntimeError(f"appops set PROJECT_MEDIA {mode} failed: {(result['stdout'] + result['stderr']).strip()}")


def package_stamp(device, package):
    """versionCode and lastUpdateTime of the host and its test APK; a change means another session reinstalled them."""
    stamp = []
    for name in (package, package + ".test"):
        dump = device.shell("dumpsys", "package", name)["stdout"]
        stamp += re.findall(r"versionCode=(\d+)", dump)[:1] + re.findall(r"lastUpdateTime=([^\r\n]+)", dump)[:1]
    return stamp


def foreign_instrumentation(device):
    """Command lines of `am instrument` processes already running (ROADMAP R6: no parallel device tasks)."""
    result = device.shell("ps", "-A", "-o", "ARGS")
    if result["exitCode"] != 0:
        return []
    return [line.strip() for line in result["stdout"].splitlines() if "am instrument" in line and "grep" not in line]


def device_state(device):
    battery = device.shell("dumpsys", "battery")["stdout"]
    power = device.shell("dumpsys", "power")["stdout"]
    level = re.search(r"^\s*level: (\d+)", battery, re.MULTILINE)
    powered = [kind for kind in ("AC", "USB", "Wireless") if re.search(rf"^\s*{kind} powered: true", battery, re.MULTILINE)]
    wakefulness = re.search(r"mWakefulness=(\w+)", power)
    return {"batteryLevel": int(level[1]) if level else None, "powered": powered or ["none"],
            "wakefulness": wakefulness[1] if wakefulness else None}


def host_facts(device, package):
    """The device identity, the installed debug host and its test runner, checked before any scenario runs."""
    device.require_online()
    identity = device.identity()
    package_dump = device.shell("dumpsys", "package", package)["stdout"]
    version = re.search(r"versionCode=(\d+)", package_dump)
    version_name = re.search(r"versionName=(\S+)", package_dump)
    if not version:
        raise RuntimeError(f"{package} is not installed on {device.serial}")
    if not re.search(r"(?:pkgFlags|flags)=\[[^\]]*\bDEBUGGABLE\b", package_dump):
        raise RuntimeError("The installed host must be the debug build")
    runner = f"{package}.test/androidx.test.runner.AndroidJUnitRunner"
    if f"instrumentation:{runner} (target={package})" not in device.shell("pm", "list", "instrumentation")["stdout"]:
        raise RuntimeError("Install the matching test APK (app-*-debug-androidTest.apk) first")
    return {"identity": identity, "versionCode": int(version[1]), "versionName": version_name[1] if version_name else None,
            "runner": runner, "state": device_state(device)}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--package", type=physical_device.package_name, default="org.autojs.autojs6")
    parser.add_argument("--suite", choices=tuple(SUITES), default="runtime",
                        help="runtime: the first gate S1-S16 (docs/memory); bridge: the bridge gate ND1-ND13 / BN1-BN5 / QJ1-QJ5 (docs/memory-phase2)")
    parser.add_argument("--scenarios", help="Comma list, e.g. S1,S2 or ND1,ND7a (default all of the suite)")
    parser.add_argument("--plugin-commit",
                        help="Commit of the installed plugin builds, written to the bridge report header: one hash for every engine "
                             "of the run, or engine=hash pairs, e.g. bun=fb879f3,quickjs=0bcee52 (engines node, bun, quickjs, lua, python)")
    parser.add_argument("--mode", choices=("normal", "force", "both"), default="both")
    parser.add_argument("--hprof", action="store_true", help="Keep heap dumps of failing scenarios and pull them to <out>/hprof")
    parser.add_argument("--no-analyze", action="store_true", help="Skip the on-device shark analysis (ROADMAP T4 fallback)")
    parser.add_argument("--dump-after-last", action="store_true",
                        help="Bridge suite: one more heap dump right after the last run of every scenario, before the gate waits for "
                             "collection, with the bridge class paths and a class histogram (docs/memory-phase2 C31); kept and pulled with --hprof")
    parser.add_argument("--capture", choices=("off", "on"), default="off", help="Run S4a / S4f (bridge: ND8) with the PROJECT_MEDIA app-op set to allow for its duration, then reset to default (ROADMAP Q3)")
    parser.add_argument("--warmups", type=int, default=2)
    parser.add_argument("--iterations", type=int, default=20)
    parser.add_argument("--poll-ms", type=int, default=10000)
    parser.add_argument("--force-delay-ms", type=int, default=1000)
    parser.add_argument("--timeout", type=int, default=1500, help="Seconds per scenario and mode")
    parser.add_argument("--label", default="", help="Inserted into the report name, e.g. baseline")
    parser.add_argument("--out", type=Path, help="Evidence directory (default docs/memory/evidence, bridge: docs/memory-phase2/evidence)")
    parser.add_argument("--wake", action="store_true", help="Send KEYCODE_WAKEUP before each scenario (no taps)")
    parser.add_argument("--one-process", action="store_true",
                        help="Run all selected scenarios and modes in one instrumentation process (S7 / S7b with the accessibility service)")
    parser.add_argument("--a11y-wait", type=int, default=0,
                        help="Seconds (0-900) S7 / S7b wait, showing a toast, for the maintainer to turn the host accessibility service off and on")
    parser.add_argument("--keep-process", action="store_true",
                        help="Skip am force-stop around scenarios; force-stop removes the host accessibility service from the enabled list")
    parser.add_argument("--merge", action="store_true", help="Replace only the rerun scenarios in an existing report of the same name")
    parser.add_argument("--meminfo", choices=("off", "phases", "runs"), default="phases",
                        help="dumpsys meminfo of the test process at the start, after the warm-ups and at the end (phases), after every run as well (runs), or never")
    parser.add_argument("--attach", metavar="RUN_ID",
                        help="Run nothing: write the report from the files of a device-side run (tools/memory/device_gate.sh, ROADMAP "
                             "appendix C Q51 (b)) pulled into --attach-dir; the device is only asked for its identity and the host version, "
                             "cached in <attach-dir>/device.json so that a later call works while it is offline")
    parser.add_argument("--attach-dir", type=Path, help="Directory holding the <runId>-<S>-<mode>.log / .json / .logcat / .crash files of --attach")
    args = parser.parse_args()
    if args.label and not re.fullmatch(r"[a-z0-9-]{1,32}", args.label):
        parser.error("--label must be lower-case letters, digits or '-'")
    if args.attach:
        if not re.fullmatch(r"[A-Za-z0-9_.-]{1,80}", args.attach):
            parser.error("--attach must be the run id the device-side script was started with")
        if not args.attach_dir or not args.attach_dir.is_dir():
            parser.error("--attach needs --attach-dir, an existing directory with the pulled files")
        if args.suite != "runtime" or args.hprof or args.one_process:
            parser.error("--attach supports --suite runtime without --hprof and --one-process")
    if not 60 <= args.timeout <= 7200:
        parser.error("--timeout must be between 60 and 7200 seconds")
    # The test rejects memoryGateAccessibilityWaitMs above 900 s before showing any toast.
    if not 0 <= args.a11y_wait <= 900:
        parser.error("--a11y-wait must be between 0 and 900 seconds")
    suite = SUITES[args.suite]
    try:
        args.scenarios = scenario_list(args.scenarios, suite["scenarios"])
    except argparse.ArgumentTypeError as error:
        parser.error(str(error))
    engines = list(dict.fromkeys(bridge_engine(s) for s in args.scenarios)) if suite["plugins"] else []
    try:
        commits = plugin_commits(args.plugin_commit, engines)
    except ValueError as error:
        parser.error(str(error))
    if args.dump_after_last and args.suite != "bridge":
        parser.error("--dump-after-last needs --suite bridge")
    args.out = args.out or suite["out"]

    device = physical_device.Device(args.adb, args.serial)
    cache = args.attach_dir / "device.json" if args.attach else None
    if cache and cache.is_file():
        # A device-side run is attached while the device may be offline: the facts read by the first call are reused.
        facts = json.loads(cache.read_text(encoding="utf-8"))
        if facts.get("identity", {}).get("serial") != args.serial:
            print(f"Note: {cache} was recorded from {facts.get('identity', {}).get('serial')}, now {args.serial}", file=sys.stderr)
    else:
        facts = host_facts(device, args.package)
        if cache:
            physical_device.write_json(cache, facts)
    identity, version_code, version_name, runner, state = (facts["identity"], facts["versionCode"], facts["versionName"],
                                                           facts["runner"], facts["state"])

    started = physical_device.utc_now()
    today = datetime.date.today().strftime("%Y%m%d")
    model = re.sub(r"[^A-Za-z0-9_.-]", "_", identity.get("model") or "device")
    run_id = args.attach or f"{model}-{version_code}-{datetime.datetime.now().strftime('%Y%m%d%H%M%S')}"
    modes = ["normal", "force"] if args.mode == "both" else [args.mode]
    if state["powered"] == ["none"] or state["wakefulness"] != "Awake":
        print(f"Warning: ROADMAP R6 asks for a charging, awake device; state {state}", file=sys.stderr)
    args.out.mkdir(parents=True, exist_ok=True)
    device_dir = DEVICE_DIR.format(package=args.package)
    reports, failures, window = [], [], {}
    stamp = [] if args.attach else package_stamp(device, args.package)
    interrupted = None
    projection = {"allowedDuring": []}
    hidden_api, grants = {}, []
    plugins = []
    for engine in engines:
        plugin_dump = device.shell("dumpsys", "package", suite["plugins"][engine])["stdout"]
        plugin_version = re.search(r"versionCode=(\d+)", plugin_dump)
        plugin_name = re.search(r"versionName=(\S+)", plugin_dump)
        plugins.append({"engine": engine, "package": suite["plugins"][engine],
                        "versionCode": int(plugin_version[1]) if plugin_version else None,
                        "versionName": plugin_name[1] if plugin_name else None, "commit": commits.get(engine)})
    groups = [] if args.attach else ([(list(args.scenarios), ",".join(modes))] if args.one_process
                                     else [([s], m) for s in args.scenarios for m in modes])
    for group, mode in groups:
            scenario = "+".join(group)
            pairs = [(s, m) for s in group for m in mode.split(",")]
            # The device test reads one plugin commit per process: pass it when every scenario of the group shares it.
            group_commits = {commits.get(bridge_engine(s)) for s in group}
            group_commit = next(iter(group_commits)) if len(group_commits) == 1 else None
            if interrupted:
                break
            waited = 0
            while foreign_instrumentation(device) and waited < 600:
                if waited == 0:
                    print(f"Another instrumentation is running on {args.serial}; waiting (ROADMAP R6)", file=sys.stderr)
                time.sleep(15)
                waited += 15
            if foreign_instrumentation(device):
                interrupted = "another instrumentation kept running for 10 minutes"
                break
            if args.wake:
                device.shell("input", "keyevent", "KEYCODE_WAKEUP")
            if not args.keep_process:
                device.shell("am", "force-stop", args.package)
            device.call("logcat", "-c")
            capture = args.capture == "on" and any(s in suite["capture"] for s in group)
            if capture:
                projection.setdefault("before", projection_mode(device, args.package))
                set_projection(device, args.package, "allow")
                projection["allowedDuring"].append(f"{scenario} {mode}")
            command = ["am", "instrument", "-w", "-r",
                       "-e", "class", ",".join(f"{suite['testClass']}#{suite['scenarios'][s]}" for s in group),
                       "-e", "memoryGateRunId", run_id, "-e", "memoryGateModes", mode,
                       "-e", "memoryGateWarmups", str(args.warmups), "-e", "memoryGateIterations", str(args.iterations),
                       "-e", "memoryGatePollMs", str(args.poll_ms), "-e", "memoryGateForceDelayMs", str(args.force_delay_ms),
                       "-e", "memoryGateKeepHprof", "true" if args.hprof else "false",
                       "-e", "memoryGateAnalyze", "false" if args.no_analyze else "true",
                       "-e", "memoryGateCapture", args.capture,
                       "-e", "memoryGateAccessibilityWaitMs", str(args.a11y_wait * 1000),
                       "-e", "memoryGateMeminfo", args.meminfo,
                       *(["-e", "memoryGatePluginCommit", group_commit] if group_commit else []),
                       *(["-e", "memoryGateDumpAfterLast", "true"] if args.dump_after_last else []), runner]
            granted = []
            try:
                if suite["hiddenApi"]:
                    hidden_api.setdefault("before", global_setting(device, "hidden_api_policy"))
                    put_global_setting(device, "hidden_api_policy", "1")
                wanted = [(s, p) for s in group for p in suite["grants"].get(s, [])]
                held = granted_permissions(device, args.package) if wanted else set()
                for s, permission in wanted:
                    if permission in held:
                        continue
                    answer = device.shell("pm", "grant", args.package, permission)
                    if answer["exitCode"] != 0:
                        raise RuntimeError(f"pm grant {permission} failed: {(answer['stdout'] + answer['stderr']).strip()}")
                    granted.append({"scenario": s, "permission": permission})
                    held.add(permission)
                result = device.shell(*command, timeout=args.timeout)
            finally:
                if capture:
                    set_projection(device, args.package, "default")
                    projection["restoredTo"] = projection_mode(device, args.package)
                # pm revoke kills the host process, which the next scenario restarts anyway.
                for grant in granted:
                    revoked = device.shell("pm", "revoke", args.package, grant["permission"])
                    grant["revoked"] = revoked["exitCode"] == 0
                    if not grant["revoked"]:
                        grant["error"] = (revoked["stdout"] + revoked["stderr"]).strip()[:200]
                    grants.append(grant)
                if suite["hiddenApi"] and "before" in hidden_api:
                    put_global_setting(device, "hidden_api_policy", hidden_api["before"])
                    hidden_api["restoredTo"] = global_setting(device, "hidden_api_policy")
            text = result["stdout"] + result["stderr"]
            outcome = "timeout" if result.get("timeout") else test_outcome(text)
            def pulled_json(s, m):
                local = args.out / f".{run_id}-{s}-{m}.json"
                pulled = device.call("pull", f"{device_dir}/{run_id}-{s}-{m}.json", str(local))
                if pulled["exitCode"] == 0 and local.is_file():
                    raw = local.read_text(encoding="utf-8")
                    local.unlink()
                    return raw
                return None

            # -v brief keeps the tag in each line, which parse_logcat_reports looks for (-v raw drops it).
            found = pick_reports(text, pairs, run_id, pulled_json,
                                 lambda: device.call("logcat", "-d", "-v", "brief", "-s", f"{LOG_TAG}:I", timeout=60)["stdout"])
            for report in found:
                report["instrumentation"] = outcome
                reports.append(report)
                print(summary_line(report))
                for line in leak_lines(report)[:3]:
                    print(f"     {line[:220]}")
            missing = [pair for pair in pairs if pair not in {(r.get("scenario"), r.get("mode")) for r in found}]
            if missing:
                # The next scenario clears logcat, so keep the crash buffer of a crashed process now.
                crash = crash_excerpt(device.call("logcat", "-d", "-b", "crash", "-v", "brief", timeout=60)["stdout"]) if outcome == "crashed" else ""
                for s, m in missing:
                    failures.append({"scenario": s, "mode": m, "outcome": outcome, "tail": text[-1500:], "crashLog": crash})
                    print(f"{s:<4} {MODES[m]} NO REPORT (instrumentation {outcome})")
                for line in [l for l in crash.splitlines() if re.search(r"Fatal signal|Abort message|FATAL EXCEPTION", l)][:2]:
                    print(f"     {line[:220]}")
            if args.hprof:
                for report, key in [(report, key) for report in found for key in ("heapAfterLast", "heap")]:
                    hprof = (report.get(key) or {}).get("hprof")
                    if hprof and (report.get(key) or {}).get("hprofKept"):
                        target = args.out / "hprof"
                        target.mkdir(parents=True, exist_ok=True)
                        pulled = device.call("pull", hprof, str(target / Path(hprof).name), timeout=600)
                        if pulled["exitCode"] == 0:
                            device.shell("rm", "-f", hprof)
                            local = target / Path(hprof).name
                            report[key]["pulledTo"] = str(local.relative_to(ROOT) if local.is_relative_to(ROOT) else local).replace("\\", "/")
            current = package_stamp(device, args.package)
            if not current:
                # dumpsys failed: the device left adb, which is not a reinstall; reports already read stay valid.
                interrupted = f"the device went offline after {scenario} {mode}"
            elif current != stamp:
                interrupted = f"the host or test APK was reinstalled during {scenario} {mode} (another session?)"
                for report in found:
                    report["status"], report["reasons"] = "invalid", [interrupted]
    if interrupted:
        print(f"Stopped: {interrupted}; scenarios after this point were not run", file=sys.stderr)
    if args.attach:
        reports, failures, projection, window = attach_reports(
            args.attach_dir, run_id, [(s, m) for s in args.scenarios for m in modes], suite["capture"] if args.capture == "on" else (),
            {"warmups": args.warmups, "iterations": args.iterations, "pollMs": args.poll_ms, "forceDelayMs": args.force_delay_ms})
    elif not args.keep_process:
        device.shell("am", "force-stop", args.package)
    meta = {"suite": suite["name"], "runId": run_id, "label": args.label, "started": window.get("started", started),
            "finished": window.get("finished", physical_device.utc_now()), "date": today, "package": args.package,
            "versionCode": version_code, "versionName": version_name,
            "device": {k: identity.get(k) for k in ("serial", "manufacturer", "model", "sdk", "release", "abiList", "emulator")},
            "deviceState": state, "modes": modes, "scenarios": args.scenarios, "warmups": args.warmups,
            "iterations": args.iterations, "pollMs": args.poll_ms, "forceDelayMs": args.force_delay_ms,
            "capture": args.capture, "meminfo": args.meminfo, "hprof": args.hprof, "interrupted": interrupted, "keepProcess": args.keep_process, "oneProcess": args.one_process,
            **({"attached": {"dir": args.attach_dir.name, "deviceScript": "tools/memory/detached_gate.sh", "launch": "detached"}
                if window.get("launch") == "detached" else
                {"dir": args.attach_dir.name, "deviceScript": "tools/memory/device_gate.sh"}} if args.attach else {}),
            **({"projectionAppOp": projection} if projection["allowedDuring"] else {}),
            **({"plugins": plugins} if plugins else {}),
            **({"hiddenApiPolicy": hidden_api} if hidden_api else {}), **({"grants": grants} if grants else {})}
    stem = f"gate-{model}-{version_code}" + (f"-{args.label}" if args.label else "") + f"-{today}"
    if args.merge and (args.out / f"{stem}.json").is_file():
        reports, failures, meta = merge_evidence(json.loads((args.out / f"{stem}.json").read_text(encoding="utf-8")),
                                                 reports, failures, meta, suite["scenarios"])
    write_evidence(args.out / f"{stem}.json", meta, reports, failures)
    (args.out / f"{stem}.md").write_text(markdown(meta, reports, failures), encoding="utf-8")
    counts = {}
    for report in reports:
        counts[report.get("status")] = counts.get(report.get("status"), 0) + 1
    print(f"Totals: {counts}, no report {len(failures)}; evidence {(args.out / stem).relative_to(ROOT) if args.out.is_relative_to(ROOT) else args.out / stem}.md/.json")
    return 0 if not failures and all(r.get("status") in ("pass", "skipped") for r in reports) else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, OSError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(2)
