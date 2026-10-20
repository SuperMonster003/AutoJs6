"""Run the runtime leak gate (docs/memory/ROADMAP.md T3) on one device and write the evidence.

Each scenario and mode runs in its own instrumentation process (`am instrument` restarts the
host), so a leak in one scenario cannot distort the next. The device-side test prints one JSON
report per scenario and mode; this script collects them, writes
docs/memory/evidence/gate-<model>-<versionCode>[-<label>]-<yyyyMMdd>.md and the same-named .json,
and prints one line per scenario with the verdict and the six T1 numbers.

The runner never installs APKs, never taps the screen and never changes settings. Build and
install the debug host and its test APK first (see tools/memory/README.md). Dialogs that need a
person (screen capture consent for S4a) are confirmed by the maintainer on the phone.
"""
import argparse
import datetime
import json
from pathlib import Path
import re
import sys

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
    "S5": "s05UiWithWebView",
    "S6": "s06Floaty",
    "S7": "s07EventsKeysSensors",
    "S8": "s08JavaAdapterAndDex",
    "S9": "s09ExecScriptFileChain",
    "S10": "s10HttpAndWebSocket",
    "S11": "s11DeviceReceivers",
    "S12": "s12PluginBinding",
}
MODES = {"normal": "N", "force": "F"}
DEVICE_DIR = "/sdcard/Android/data/{package}/files/memory-gate"


def scenario_list(text):
    if not text:
        return list(SCENARIOS)
    chosen = []
    for item in text.split(","):
        key = next((name for name in SCENARIOS if name.lower() == item.strip().lower()), None)
        if key is None:
            raise argparse.ArgumentTypeError(f"Unknown scenario {item!r}; known: {', '.join(SCENARIOS)}")
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


def mb(value):
    return "-" if value is None else f"{value / 1048576:+.2f}"


def delta(value):
    return "-" if value is None else f"{value:+d}"


def cell(report):
    """Appendix A cell: retained / Java MB / native MB / threads / contexts / engines."""
    if report.get("status") == "skipped":
        return "skipped"
    m = report.get("metrics") or {}
    return (f"{m.get('retained', '-')} / {mb(m.get('javaHeapDeltaBytes'))} / {mb(m.get('nativeHeapDeltaBytes'))} / "
            f"{delta(m.get('scriptThreadDelta'))} / {delta(m.get('rhinoContextDelta'))} / {m.get('engines', '-')}")


def summary_line(report):
    scenario, mode = report.get("scenario", "?"), MODES.get(report.get("mode"), "?")
    status = report.get("status", "?").upper()
    if report.get("status") == "skipped":
        return f"{scenario:<4} {mode} SKIPPED {'; '.join(report.get('reasons') or [])}"
    m = report.get("metrics") or {}
    return (f"{scenario:<4} {mode} {status:<5} retained={m.get('retained')}/{report.get('iterations')} "
            f"java={mb(m.get('javaHeapDeltaBytes'))}MB native={mb(m.get('nativeHeapDeltaBytes'))}MB "
            f"threads={delta(m.get('scriptThreadDelta'))} contexts={delta(m.get('rhinoContextDelta'))} engines={m.get('engines')}")


def leak_lines(report):
    analysis = ((report.get("heap") or {}).get("analysis")) or {}
    lines = []
    for leak in analysis.get("leaks") or []:
        lines.append(f"{leak.get('runtimes')} runtime(s), root {leak.get('gcRoot')}: {leak.get('summary')}")
    alternate = analysis.get("alternate") or {}
    for leak in alternate.get("leaks") or []:
        lines.append(f"ignoring {alternate.get('ignoring')}: {leak.get('runtimes')} runtime(s), root {leak.get('gcRoot')}: {leak.get('summary')}")
    if (report.get("heap") or {}).get("error"):
        lines.append("heap evidence error: " + report["heap"]["error"])
    return lines


def markdown(meta, reports, failures):
    identity = meta["device"]
    lines = [
        f"# Memory gate {identity.get('model')} ({meta['versionCode']}) {meta['date']}",
        "",
        f"- Device: {identity.get('manufacturer')} {identity.get('model')}, API {identity.get('sdk')}, serial {identity.get('serial')}",
        f"- Host: {meta['package']} versionCode {meta['versionCode']} ({meta.get('versionName')}), debuggable",
        f"- Run: {meta['runId']}, label {meta.get('label') or '-'}, {meta['started']} -> {meta['finished']}",
        f"- Device state at start: {meta.get('deviceState')}",
        f"- Warm-ups {meta['warmups']}, iterations {meta['iterations']}, poll {meta['pollMs']} ms, force delay {meta['forceDelayMs']} ms, capture {meta['capture']}",
        "- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)",
        "- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start",
        "",
        "| Scenario | Mode | Status | Cell | Reasons |",
        "| --- | --- | --- | --- | --- |",
    ]
    for report in reports:
        reasons = "; ".join(report.get("reasons") or []).replace("|", "/")
        lines.append(f"| {report.get('scenario')} {report.get('title', '')} | {MODES.get(report.get('mode'), '?')} | "
                     f"{report.get('status')} | {cell(report)} | {reasons} |")
    for item in failures:
        lines.append(f"| {item['scenario']} | {MODES.get(item['mode'], '?')} | no report | - | instrumentation {item['outcome']} |")
    leaks = [(report, leak_lines(report)) for report in reports if leak_lines(report)]
    if leaks:
        lines += ["", "## Shortest strong reference paths (shark, exited ScriptRuntime instances)", ""]
        for report, entries in leaks:
            analysis = ((report.get("heap") or {}).get("analysis")) or {}
            lines.append(f"### {report.get('scenario')} {MODES.get(report.get('mode'), '?')}: "
                         f"{analysis.get('exitedRuntimeInstances', '?')} exited runtimes in the dump, "
                         f"dump {report['heap'].get('dumpMs', '?')} ms, analysis {analysis.get('analysisMs', '?')} ms")
            lines.append("")
            for entry in entries:
                lines.append(f"- {entry}")
            alternate = analysis.get("alternate") or {}
            for title, group in (("", analysis.get("leaks")), (f" (second pass ignoring {alternate.get('ignoring')})", alternate.get("leaks"))):
                for leak in group or []:
                    lines.append("")
                    lines.append("```")
                    lines += [f"{leak.get('gcRoot')}{title}"] + [f"  {step}" for step in leak.get("path") or []]
                    lines.append("```")
            lines.append("")
    lines += ["", "Raw reports: the .json file with the same name (one report per line under `results`)."]
    return "\n".join(lines) + "\n"


def write_evidence(path_json, meta, reports, failures):
    body = ",\n".join("    " + json.dumps(report, ensure_ascii=False, separators=(",", ":")) for report in reports)
    head = json.dumps({**meta, "failures": failures}, ensure_ascii=False, indent=2)
    path_json.write_text(head[:-2] + ',\n  "results": [\n' + body + "\n  ]\n}\n", encoding="utf-8")


def device_state(device):
    battery = device.shell("dumpsys", "battery")["stdout"]
    power = device.shell("dumpsys", "power")["stdout"]
    level = re.search(r"^\s*level: (\d+)", battery, re.MULTILINE)
    powered = [kind for kind in ("AC", "USB", "Wireless") if re.search(rf"^\s*{kind} powered: true", battery, re.MULTILINE)]
    wakefulness = re.search(r"mWakefulness=(\w+)", power)
    return {"batteryLevel": int(level[1]) if level else None, "powered": powered or ["none"],
            "wakefulness": wakefulness[1] if wakefulness else None}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--package", type=physical_device.package_name, default="org.autojs.autojs6")
    parser.add_argument("--scenarios", type=scenario_list, default=list(SCENARIOS), help="Comma list, e.g. S1,S2 (default all)")
    parser.add_argument("--mode", choices=("normal", "force", "both"), default="both")
    parser.add_argument("--hprof", action="store_true", help="Keep heap dumps of failing scenarios and pull them to <out>/hprof")
    parser.add_argument("--no-analyze", action="store_true", help="Skip the on-device shark analysis (ROADMAP T4 fallback)")
    parser.add_argument("--capture", choices=("off", "on"), default="off", help="Run S4a; each run asks for screen capture consent")
    parser.add_argument("--warmups", type=int, default=2)
    parser.add_argument("--iterations", type=int, default=20)
    parser.add_argument("--poll-ms", type=int, default=10000)
    parser.add_argument("--force-delay-ms", type=int, default=1000)
    parser.add_argument("--timeout", type=int, default=1500, help="Seconds per scenario and mode")
    parser.add_argument("--label", default="", help="Inserted into the report name, e.g. baseline")
    parser.add_argument("--out", type=Path, default=ROOT / "docs" / "memory" / "evidence")
    parser.add_argument("--wake", action="store_true", help="Send KEYCODE_WAKEUP before each scenario (no taps)")
    args = parser.parse_args()
    if args.label and not re.fullmatch(r"[a-z0-9-]{1,32}", args.label):
        parser.error("--label must be lower-case letters, digits or '-'")
    if not 60 <= args.timeout <= 7200:
        parser.error("--timeout must be between 60 and 7200 seconds")

    device = physical_device.Device(args.adb, args.serial)
    device.require_online()
    identity = device.identity()
    package_dump = device.shell("dumpsys", "package", args.package)["stdout"]
    version = re.search(r"versionCode=(\d+)", package_dump)
    version_name = re.search(r"versionName=(\S+)", package_dump)
    if not version:
        raise RuntimeError(f"{args.package} is not installed on {args.serial}")
    if not re.search(r"(?:pkgFlags|flags)=\[[^\]]*\bDEBUGGABLE\b", package_dump):
        raise RuntimeError("The installed host must be the debug build")
    runner = f"{args.package}.test/androidx.test.runner.AndroidJUnitRunner"
    if f"instrumentation:{runner} (target={args.package})" not in device.shell("pm", "list", "instrumentation")["stdout"]:
        raise RuntimeError("Install the matching test APK (app-*-debug-androidTest.apk) first")

    started = physical_device.utc_now()
    today = datetime.date.today().strftime("%Y%m%d")
    model = re.sub(r"[^A-Za-z0-9_.-]", "_", identity.get("model") or "device")
    run_id = f"{model}-{version[1]}-{datetime.datetime.now().strftime('%Y%m%d%H%M%S')}"
    modes = ["normal", "force"] if args.mode == "both" else [args.mode]
    state = device_state(device)
    if state["powered"] == ["none"] or state["wakefulness"] != "Awake":
        print(f"Warning: ROADMAP R6 asks for a charging, awake device; state {state}", file=sys.stderr)
    args.out.mkdir(parents=True, exist_ok=True)
    device_dir = DEVICE_DIR.format(package=args.package)
    reports, failures = [], []
    for scenario in args.scenarios:
        for mode in modes:
            if args.wake:
                device.shell("input", "keyevent", "KEYCODE_WAKEUP")
            device.shell("am", "force-stop", args.package)
            device.call("logcat", "-c")
            command = ["am", "instrument", "-w", "-r",
                       "-e", "class", f"{TEST_CLASS}#{SCENARIOS[scenario]}",
                       "-e", "memoryGateRunId", run_id, "-e", "memoryGateModes", mode,
                       "-e", "memoryGateWarmups", str(args.warmups), "-e", "memoryGateIterations", str(args.iterations),
                       "-e", "memoryGatePollMs", str(args.poll_ms), "-e", "memoryGateForceDelayMs", str(args.force_delay_ms),
                       "-e", "memoryGateKeepHprof", "true" if args.hprof else "false",
                       "-e", "memoryGateAnalyze", "false" if args.no_analyze else "true",
                       "-e", "memoryGateCapture", args.capture, runner]
            result = device.shell(*command, timeout=args.timeout)
            text = result["stdout"] + result["stderr"]
            outcome = "timeout" if result.get("timeout") else test_outcome(text)
            found = [r for r in parse_reports(text) if r.get("scenario") == scenario and r.get("mode") == mode]
            if not found:
                local = args.out / f".{run_id}-{scenario}-{mode}.json"
                pulled = device.call("pull", f"{device_dir}/{run_id}-{scenario}-{mode}.json", str(local))
                if pulled["exitCode"] == 0 and local.is_file():
                    found = [json.loads(local.read_text(encoding="utf-8"))]
                    local.unlink()
            if not found:
                logcat = device.call("logcat", "-d", "-v", "raw", "-s", f"{LOG_TAG}:I", timeout=60)
                found = [r for r in parse_logcat_reports(logcat["stdout"], run_id) if r.get("scenario") == scenario and r.get("mode") == mode]
            for report in found:
                report["instrumentation"] = outcome
                reports.append(report)
                print(summary_line(report))
                for line in leak_lines(report)[:3]:
                    print(f"     {line[:220]}")
            if not found:
                failures.append({"scenario": scenario, "mode": mode, "outcome": outcome,
                                 "tail": text[-1500:]})
                print(f"{scenario:<4} {MODES[mode]} NO REPORT (instrumentation {outcome})")
            if args.hprof:
                for report in found:
                    hprof = (report.get("heap") or {}).get("hprof")
                    if hprof and (report.get("heap") or {}).get("hprofKept"):
                        target = args.out / "hprof"
                        target.mkdir(parents=True, exist_ok=True)
                        pulled = device.call("pull", hprof, str(target / Path(hprof).name), timeout=600)
                        if pulled["exitCode"] == 0:
                            device.shell("rm", "-f", hprof)
                            report["heap"]["pulledTo"] = str((target / Path(hprof).name).relative_to(ROOT)).replace("\\", "/")
    device.shell("am", "force-stop", args.package)
    meta = {"suite": "memory-gate", "runId": run_id, "label": args.label, "started": started,
            "finished": physical_device.utc_now(), "date": today, "package": args.package,
            "versionCode": int(version[1]), "versionName": version_name[1] if version_name else None,
            "device": {k: identity.get(k) for k in ("serial", "manufacturer", "model", "sdk", "release", "abiList", "emulator")},
            "deviceState": state, "modes": modes, "scenarios": args.scenarios, "warmups": args.warmups,
            "iterations": args.iterations, "pollMs": args.poll_ms, "forceDelayMs": args.force_delay_ms,
            "capture": args.capture, "hprof": args.hprof}
    stem = f"gate-{model}-{version[1]}" + (f"-{args.label}" if args.label else "") + f"-{today}"
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
