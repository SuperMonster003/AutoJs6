"""CI entry of the runtime leak gate (docs/memory/ROADMAP.md P6.1) and, with --suite bridge, of the bridge leak gate
(docs/memory-phase2/ROADMAP.md P5.1): prepare a disposable emulator, run tools/memory/run_gate.py on it in
normal-exit mode and rerun once after a system crash or a lost adb.

.github/workflows/memory-gate.yml only calls this script, and a local run on an AVD of the same recipe (API 35,
google_apis, x86_64, pixel_7) takes the same steps:

1. Wait until the framework answers (boot completed, boot animation stopped, activity and package services up).
2. Prepare: install the debug host for the device ABI with its runtime permissions, its test APK and the --plugin
   APKs (the official OpenCV plugin for S4b / S4d / S4e; for the bridge suite the five runtime plugins and 3-Block
   Compose, each checked as installed before the run); unlock, animations off, stay awake, overlay app-op allowed
   (S6 and ND9 are skipped without it).
3. Run run_gate.py (--suite --mode normal --capture on --wake --hprof) with the logcat of the whole run kept in --work. An adb
   probe every 30 s stops it once adb is gone; a new system_server pid or "System has crashed" marks a framework
   restart.
4. After a system crash or a lost adb (and only then) recover adb (--restart-adb-server, then --relaunch), prepare
   again and rerun once the scenarios that did not pass, merged into the same report; the first attempt's report
   stays in <work>/attempt-1.
5. Verdict: every selected scenario passed, or was skipped for a reason the emulator cannot avoid (--allow-skip).

Unlike run_gate.py this script installs APKs and changes settings, so it refuses devices that are not emulators.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import threading
import time

# -I leaves the script's own directory off sys.path.
HERE = str(Path(__file__).resolve().parent)
sys.path.insert(0, HERE)
import run_gate  # noqa: E402
import physical_device  # noqa: E402 (on sys.path through run_gate)

# run_gate.py puts tools/sdk37 first, which has a test_run_gate.py of its own; keep this directory ahead of it so that
# unittest discovery in tools/memory still finds the right one after importing this module.
sys.path.remove(HERE)
sys.path.insert(0, HERE)

ROOT = run_gate.ROOT
PACKAGE = "org.autojs.autojs6"
# S1-S12 of ROADMAP T2 (S4 split into S4a-S4f, S7 with S7b); S13-S16 came later and run only when --scenarios names them.
CI_SCENARIOS = list(run_gate.SCENARIOS)[:list(run_gate.SCENARIOS).index("S12") + 1]
# Skips a fresh emulator cannot avoid; any other skip fails the gate, so a lost grant cannot pass silently.
UNAVOIDABLE_SKIPS = {
    "S7b": "the host accessibility service is never bound to the test process without the maintainer (README, S7 / S7b)",
    "S12": "the official plugin 3-ImGui is not installed",
    "S13": "no speech plugin (3-Macaw Speech or 3-Stone AI) is installed",
}
# --suite bridge (docs/memory-phase2/ROADMAP.md T8): every bridge scenario. The ColorOS rebind scenarios report skipped
# on any other system, ND6 without the official 3-Block Compose plugin (AVD column of appendix C Q5).
BRIDGE_CI_SCENARIOS = list(run_gate.BRIDGE_SCENARIOS)
BRIDGE_UNAVOIDABLE_SKIPS = {
    **{s: "runs on ColorOS only (OPD2413), which no emulator has" for s in ("ND13", "BN5", "QJ5", "LU5", "PY5")},
    "ND6": "the official plugin 3-Block Compose is not installed",
}
BLOCK_COMPOSE_PLUGIN = "io.github.supermonster003.autojs6.plugin.three.block.compose"
SUITES = {
    "runtime": {"scenarios": CI_SCENARIOS, "skips": UNAVOIDABLE_SKIPS, "label": "ci", "title": "Memory gate"},
    "bridge": {"scenarios": BRIDGE_CI_SCENARIOS, "skips": BRIDGE_UNAVOIDABLE_SKIPS, "label": "mp2-ci", "title": "Bridge gate"},
}
# Printed by am instrument when system_server died under the test ("INSTRUMENTATION_ABORTED: System has crashed.").
SYSTEM_CRASH = re.compile(r"System has crashed")
EVIDENCE_LINE = re.compile(r"; evidence (.+)\.md/\.json\s*$")
FRAMEWORK_TIMEOUT = 300
BOOT_TIMEOUT = 600
# The adb watchdog of 3-Stove Agent's run_main_suite.py: an emulator guest may go silent for some 20 s and come back,
# so one missed probe is tolerated; three in a row (90 s) mean adb is gone.
PROBE_INTERVAL = 30
PROBE_TIMEOUT = 15
PROBE_FAILURES = 3


def host_apk_name(metadata, abis):
    """outputFile of the split for the first device ABI that has one, else of the universal APK."""
    elements = metadata.get("elements") or []
    for abi in abis:
        for element in elements:
            if any(f.get("filterType") == "ABI" and f.get("value") == abi for f in element.get("filters") or []):
                return element["outputFile"], element.get("versionCode")
    for element in elements:
        if element.get("type") in ("UNIVERSAL", "SINGLE"):
            return element["outputFile"], element.get("versionCode")
    raise RuntimeError(f"No host APK for ABIs {abis} in output-metadata.json")


def needed_plugins(scenarios, allowed_skips):
    """Plugin packages a bridge run needs installed, by name: the runtime plugin of every engine with a scenario that
    may not be skipped, and 3-Block Compose for ND6 unless ND6 may be skipped."""
    needed = {}
    for scenario in scenarios:
        engine = run_gate.bridge_engine(scenario)
        if engine and scenario not in allowed_skips:
            needed[engine] = run_gate.BRIDGE_PLUGINS[engine]
            if scenario == "ND6":
                needed["block-compose"] = BLOCK_COMPOSE_PLUGIN
    return needed


def normal_results(evidence):
    return {r.get("scenario"): r for r in evidence.get("results", []) if r.get("mode") == "normal"}


def rerun_scenarios(evidence, scenarios, allowed_skips):
    """Scenarios of a crashed or disconnected attempt to run again: all but those that passed or were skipped as expected."""
    results = normal_results(evidence) if evidence else {}
    done = {s for s, r in results.items()
            if r.get("status") == "pass" or (r.get("status") == "skipped" and s in allowed_skips)}
    return [s for s in scenarios if s not in done]


def verdict(evidence, scenarios, allowed_skips):
    """(passed, skipped, problems): problems lists every scenario that did not pass or was skipped unexpectedly."""
    results = normal_results(evidence)
    passed, skipped, problems = [], [], []
    for scenario in scenarios:
        report = results.get(scenario)
        if report is None:
            failure = next((f for f in evidence.get("failures", []) if f.get("scenario") == scenario), None)
            problems.append(f"{scenario} no report" + (f" (instrumentation {failure.get('outcome')})" if failure else " (not run)"))
        elif report.get("status") == "pass":
            passed.append(scenario)
        elif report.get("status") == "skipped" and scenario in allowed_skips:
            skipped.append(scenario)
        elif report.get("status") == "skipped":
            problems.append(f"{scenario} skipped unexpectedly: {'; '.join(report.get('reasons') or [])}")
        else:
            problems.append(f"{scenario} {report.get('status')}: {'; '.join(report.get('reasons') or [])[:300]}")
    return passed, skipped, problems


def kill(process):
    # On Windows (local runs) take the tree: run_gate.py waits on adb child processes.
    if os.name == "nt":
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(process.pid)], capture_output=True)
    else:
        process.kill()
    process.wait()


class Gate:
    def __init__(self, args, scenarios):
        self.args = args
        self.scenarios = scenarios
        self.device = physical_device.Device(args.adb, args.serial)
        self.work = args.work.resolve()
        self.work.mkdir(parents=True, exist_ok=True)
        self.logcat = None

    def adb_server(self, *command):
        subprocess.run([self.args.adb, *command], capture_output=True, timeout=60)

    def probe(self):
        result = self.device.shell("echo", "ok", timeout=PROBE_TIMEOUT)
        return result["exitCode"] == 0 and "ok" in result["stdout"]

    def getprop(self, name):
        return self.device.shell("getprop", name, timeout=PROBE_TIMEOUT)["stdout"].strip()

    def system_server_pid(self):
        return self.device.shell("pidof", "system_server", timeout=PROBE_TIMEOUT)["stdout"].strip() or None

    def wait_for_framework(self):
        """sys.boot_completed stays 1 across a framework restart, so also wait until the boot animation has stopped
        and the activity and package services answer, unchanged for 10 seconds."""
        began, steady_since = time.monotonic(), None
        while time.monotonic() - began < FRAMEWORK_TIMEOUT:
            ready = (self.getprop("sys.boot_completed") == "1"
                     and self.getprop("init.svc.bootanim") in ("stopped", "")
                     and "activity: found" in self.device.shell("service", "check", "activity", timeout=PROBE_TIMEOUT)["stdout"]
                     and self.device.shell("pm", "path", "android", timeout=PROBE_TIMEOUT)["stdout"].startswith("package:"))
            if ready:
                steady_since = steady_since or time.monotonic()
                if time.monotonic() - steady_since >= 10:
                    print(f"CI_GATE_FRAMEWORK_READY waited={time.monotonic() - began:.0f}s")
                    return
            else:
                steady_since = None
            time.sleep(2)
        raise RuntimeError(f"The framework did not answer within {FRAMEWORK_TIMEOUT}s")

    def install(self, apk, *flags):
        result = self.device.call("install", "-r", *flags, str(apk), timeout=600)
        if result["exitCode"] != 0 or "Success" not in result["stdout"]:
            raise RuntimeError(f"Installing {apk.name} failed: {(result['stdout'] + result['stderr']).strip()[-400:]}")

    def prepare(self):
        identity = self.device.identity()
        if not identity.get("emulator"):
            raise RuntimeError(f"{self.args.serial} is not an emulator; ci_gate.py installs APKs and changes settings, "
                               "so phones go through run_gate.py (tools/memory/README.md)")
        abis = [abi for abi in (identity.get("abiList") or "").split(",") if abi]
        host_dir = self.args.apk_dir / "app" / "debug"
        test_dir = self.args.apk_dir / "androidTest" / "app" / "debug"
        host_name, version_code = host_apk_name(json.loads((host_dir / "output-metadata.json").read_text(encoding="utf-8")), abis)
        test_name, _ = host_apk_name(json.loads((test_dir / "output-metadata.json").read_text(encoding="utf-8")), [])
        # -g: the host's runtime permissions (notifications on API 33+, storage), as on the maintainer's phones.
        self.install(host_dir / host_name, "-t", "-g")
        self.install(test_dir / test_name, "-t")
        for plugin in self.args.plugin:
            self.install(plugin)
        installed = re.search(r"versionCode=(\d+)", self.device.shell("dumpsys", "package", PACKAGE)["stdout"])
        if not installed or (version_code is not None and int(installed[1]) != int(version_code)):
            raise RuntimeError(f"The installed host has versionCode {installed and installed[1]}, the APK {version_code}")
        # A missing runtime plugin would only skip its engine's scenarios; name it before the run instead.
        plugins = {}
        if self.args.suite == "bridge":
            for name, package in needed_plugins(self.scenarios, self.args.allow_skip).items():
                found = re.search(r"versionCode=(\d+)", self.device.shell("dumpsys", "package", package)["stdout"])
                if not found:
                    raise RuntimeError(f"The bridge suite needs the {name} plugin {package}; pass its x86_64 APK with --plugin")
                plugins[name] = int(found[1])
        # What reactivecircus/android-emulator-runner does after the boot (unlock, no animations), repeated here so
        # that a local AVD and a relaunched emulator match; the screen stays on for S4a / S4f / S5.
        self.device.shell("input", "keyevent", "82")
        for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            self.device.shell("settings", "put", "global", setting, "0.0")
        self.device.shell("svc", "power", "stayon", "true")
        self.device.shell("appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "allow")
        overlay = re.search(r"SYSTEM_ALERT_WINDOW: (\w+)", self.device.shell("appops", "get", PACKAGE, "SYSTEM_ALERT_WINDOW")["stdout"])
        print(f"CI_GATE_PREPARED model={identity.get('model')} sdk={identity.get('sdk')} abi={abis[:1]} host={host_name} "
              f"versionCode={installed[1]} test={test_name} plugins={[p.name for p in self.args.plugin]} "
              f"overlay={overlay[1] if overlay else '?'}"
              + (f" pluginVersions={','.join(f'{name}:{code}' for name, code in plugins.items())}" if plugins else ""))

    def start_logcat(self, attempt):
        path = self.work / "logcat.txt"
        with path.open("ab") as sink:
            sink.write(f"==== memory gate attempt {attempt}".encode() + os.linesep.encode())
        self.device.call("logcat", "-G", "16M")
        # run_gate.py clears the device log before each scenario, so stream it rather than dump it at the end.
        sink = path.open("ab")
        self.logcat = subprocess.Popen([self.args.adb, "-s", self.args.serial, "logcat", "-v", "threadtime",
                                        "-b", "main", "-b", "system", "-b", "crash", "-b", "events"],
                                       stdin=subprocess.DEVNULL, stdout=sink, stderr=subprocess.STDOUT)
        sink.close()

    def stop_logcat(self):
        if self.logcat:
            self.logcat.terminate()
            try:
                self.logcat.wait(timeout=30)
            except subprocess.TimeoutExpired:
                self.logcat.kill()
            self.logcat = None

    def run_gate(self, scenarios, merge, attempt):
        """Returns (exit code, evidence stem or None, system crashed, adb lost)."""
        command = [sys.executable, "-I", "-u", str(Path(run_gate.__file__).resolve()), "-s", self.args.serial,
                   "--adb", self.args.adb, "--suite", self.args.suite, "--mode", "normal", "--scenarios", ",".join(scenarios), "--capture", "on",
                   "--wake", "--hprof", "--out", str(self.args.out)]
        command += ["--label", self.args.label] if self.args.label else []
        command += ["--merge"] if merge else []
        print(f"CI_GATE_RUN attempt={attempt} scenarios={','.join(scenarios)}{' merge' if merge else ''}")
        server_before = self.system_server_pid()
        process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                                   encoding="utf-8", errors="replace", env=dict(os.environ, PYTHONIOENCODING="utf-8"))
        lost, stop = threading.Event(), threading.Event()

        def watchdog():
            misses = 0
            while not stop.wait(PROBE_INTERVAL):
                misses = 0 if self.probe() else misses + 1
                if misses >= PROBE_FAILURES:
                    lost.set()
                    print(f"CI_GATE_ADB_LOST no answer to {misses} probes over {misses * PROBE_INTERVAL}s; stopping run_gate.py")
                    kill(process)
                    return

        thread = threading.Thread(target=watchdog, daemon=True)
        thread.start()
        crashed, stem = False, None
        with (self.work / f"run-gate-attempt-{attempt}.txt").open("w", encoding="utf-8") as transcript:
            for line in process.stdout:
                sys.stdout.write(line)
                transcript.write(line)
                crashed = crashed or SYSTEM_CRASH.search(line) is not None
                found = EVIDENCE_LINE.search(line)
                stem = found[1] if found else stem
        code = process.wait()
        stop.set()
        thread.join()
        if code and not lost.is_set():
            # A run that failed between two probes (adb erroring out at once rather than hanging) is still the
            # device's fault when adb does not answer afterwards.
            for _ in range(PROBE_FAILURES):
                if self.probe():
                    break
                time.sleep(5)
            else:
                lost.set()
                print("CI_GATE_ADB_LOST adb does not answer after the failed run")
        if not lost.is_set():
            server_after = self.system_server_pid()
            if server_before and server_after and server_before != server_after:
                crashed = True
                print(f"CI_GATE_SYSTEM_RESTART system_server pid {server_before} -> {server_after}")
        evidence = self.evidence(stem)
        if evidence and any(SYSTEM_CRASH.search(f.get("tail") or "") for f in evidence.get("failures", [])):
            crashed = True
        return code, stem, crashed, lost.is_set()

    def evidence(self, stem):
        if not stem:
            return None
        path = Path(stem + ".json")
        path = path if path.is_absolute() else ROOT / path
        return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else None

    def recover_adb(self):
        """With --restart-adb-server a fresh adb server first reconnects to the emulator's adb port (CI only: locally
        it would cut every other session's devices). With --relaunch the emulator console, which does not depend on the
        guest's adbd, kills the emulator and the same command starts the AVD again. Returns False when adb stayed lost."""
        if self.args.restart_adb_server:
            self.adb_server("kill-server")
            self.adb_server("start-server")
        began = time.monotonic()
        while time.monotonic() - began < 45:
            if self.probe():
                print(f"CI_GATE_ADB_REVIVED after {time.monotonic() - began:.0f}s")
                return True
            time.sleep(5)
        if not self.args.relaunch:
            print("CI_GATE_ADB_LOST stays lost; no --relaunch given")
            return False
        self.device.call("emu", "kill", timeout=30)
        began = time.monotonic()
        while time.monotonic() - began < 60 and self.args.serial in physical_device.run([self.args.adb, "devices"])["stdout"]:
            time.sleep(2)
        with (self.work / "emulator-relaunch.log").open("ab") as sink:
            subprocess.Popen(shlex.split(self.args.relaunch), stdin=subprocess.DEVNULL, stdout=sink, stderr=subprocess.STDOUT)
        print(f"CI_GATE_RELAUNCH {self.args.relaunch}")
        began = time.monotonic()
        while time.monotonic() - began < BOOT_TIMEOUT:
            if self.getprop("sys.boot_completed") == "1":
                print(f"CI_GATE_BOOTED waited={time.monotonic() - began:.0f}s")
                return True
            time.sleep(3)
        print(f"CI_GATE_RELAUNCH the emulator did not boot within {BOOT_TIMEOUT}s")
        return False

    def keep_attempt(self, stem):
        kept = self.work / "attempt-1"
        shutil.rmtree(kept, ignore_errors=True)
        kept.mkdir(parents=True)
        for suffix in (".md", ".json"):
            source = Path(stem + suffix) if stem else None
            source = source if source is None or source.is_absolute() else ROOT / source
            if source and source.is_file():
                shutil.copy2(source, kept / source.name)
        return kept

    def summary(self, evidence, scenarios, attempts):
        passed, skipped, problems = verdict(evidence, scenarios, self.args.allow_skip)
        result = "PASS" if not problems and not evidence.get("failures") else "FAIL"
        print(f"CI_GATE_RESULT {result} passed={len(passed)} skipped={len(skipped)} ({', '.join(skipped)}) "
              f"problems={len(problems)} attempts={attempts} versionCode={evidence.get('versionCode')}")
        for problem in problems:
            print(f"CI_GATE_PROBLEM {problem}")
        target = os.environ.get("GITHUB_STEP_SUMMARY")
        if target:
            lines = [f"## {SUITES[self.args.suite]['title']}: {result}", "",
                     f"- passed {len(passed)}, skipped {len(skipped)} ({', '.join(skipped) or '-'}), attempts {attempts}",
                     *[f"- {problem}" for problem in problems], ""]
            with open(target, "a", encoding="utf-8") as sink:
                sink.write("\n".join(lines) + "\n" + run_gate.markdown(evidence, evidence.get("results", []),
                                                                      evidence.get("failures", [])) + "\n")
        return 0 if result == "PASS" else 1


def main():
    sys.stdout.reconfigure(line_buffering=True)
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--apk-dir", type=Path, default=ROOT / "app" / "build" / "outputs" / "apk",
                        help="Holds app/debug and androidTest/app/debug with their output-metadata.json (one build)")
    parser.add_argument("--suite", choices=tuple(SUITES), default="runtime",
                        help="runtime: the first gate (docs/memory); bridge: the plugin engine bridge gate (docs/memory-phase2)")
    parser.add_argument("--plugin", type=Path, action="append", default=[], help="Extra APK to install, e.g. the OpenCV plugin")
    parser.add_argument("--scenarios", default="",
                        help=f"Comma list; empty runs {CI_SCENARIOS[0]}-{CI_SCENARIOS[-1]} (bridge: all its scenarios)")
    parser.add_argument("--allow-skip",
                        help="Scenarios that may report skipped; any other skip fails the gate (default "
                             + "; bridge: ".join(",".join(suite["skips"]) for suite in SUITES.values()) + ")")
    parser.add_argument("--label", help="Report label (default ci; bridge: mp2-ci)")
    parser.add_argument("--out", type=Path, default=ROOT / "build" / "memory-gate" / "evidence")
    parser.add_argument("--work", type=Path, default=ROOT / "build" / "memory-gate",
                        help="Logcat, run_gate.py transcripts and the first attempt's report of a rerun")
    parser.add_argument("--no-retry", action="store_true", help="Do not rerun after a system crash or a lost adb")
    parser.add_argument("--restart-adb-server", action="store_true",
                        help="Restart the adb server when adb is lost (CI only; it disconnects every device of this host)")
    parser.add_argument("--relaunch", metavar="COMMAND",
                        help="Emulator command line that starts this AVD again (same port) when adb stays lost")
    args = parser.parse_args()
    suite, known = SUITES[args.suite], run_gate.SUITES[args.suite]["scenarios"]
    scenarios = run_gate.scenario_list(args.scenarios, known) if args.scenarios.strip() else list(suite["scenarios"])
    args.allow_skip = ",".join(suite["skips"]) if args.allow_skip is None else args.allow_skip
    args.allow_skip = set(run_gate.scenario_list(args.allow_skip, known)) if args.allow_skip.strip() else set()
    args.label = suite["label"] if args.label is None else args.label
    args.out = args.out.resolve()

    gate = Gate(args, scenarios)
    gate.wait_for_framework()
    gate.prepare()
    gate.start_logcat(1)
    try:
        code, stem, crashed, lost = gate.run_gate(scenarios, merge=False, attempt=1)
        attempts = 1
        if code and (crashed or lost) and not args.no_retry:
            evidence = gate.evidence(stem)
            again = rerun_scenarios(evidence, scenarios, args.allow_skip)
            kept = gate.keep_attempt(stem)
            print(f"CI_GATE_RETRY {'adb was lost' if lost else 'the system crashed'} in attempt 1; "
                  f"first report in {kept}; rerunning {','.join(again)}")
            gate.stop_logcat()
            if lost and not gate.recover_adb():
                raise RuntimeError("adb was lost and not recovered")
            gate.wait_for_framework()
            gate.prepare()
            gate.start_logcat(2)
            # Without a report of attempt 1 (run_gate.py was stopped) the rerun writes a new one.
            code, stem2, crashed, lost = gate.run_gate(again, merge=evidence is not None, attempt=2)
            stem, attempts = stem2 or stem, 2
        evidence = gate.evidence(stem)
        if evidence is None:
            why = " (adb was lost)" if lost else " (the system crashed)" if crashed else ""
            raise RuntimeError(f"run_gate.py wrote no report{why}; exit code {code}")
        print(f"CI_GATE_EVIDENCE {stem}.md")
        return gate.summary(evidence, scenarios, attempts)
    finally:
        gate.stop_logcat()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, OSError, argparse.ArgumentTypeError) as error:
        print(f"CI_GATE_ERROR {error}", file=sys.stderr)
        raise SystemExit(2)
