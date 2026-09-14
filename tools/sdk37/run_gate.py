#!/usr/bin/env python3
"""One-command SDK 37 gate for a selected device.

Checks the built APKs (aapt2 badging, SHA-256), optionally installs them, runs every unattended
instrumentation suite that applies to the device, optionally runs the emulator-only steps (full Looper
matrix with accessibility rebind, legacy accessibility smoke) and writes one JSON plus one Markdown
report. Suite selection is automatic: `--suite auto` picks by API level, installed target, installed
plugins and emulator / physical type; explicit `--suite` values override it.

The gate never grants runtime permissions, never changes compatibility overrides and never uninstalls
anything from a physical device. `--reinstall-on-mismatch` is honoured on emulators only.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import time
import uuid
import xml.etree.ElementTree as ET
import zipfile

from physical_device import Device, SUITES, parse_instrumentation, run, snapshot, utc_now, write_json
from run_parcel_tests import CONTROLS, WIRE, effective_parcel_policy

ROOT = Path(__file__).resolve().parents[2]
HOST = "org.autojs.autojs6"
INRT = "org.autojs.autojs6.inrt"
RUNNER_SUFFIX = ".test/androidx.test.runner.AndroidJUnitRunner"
ANDROID_TEST = ROOT / "app/src/androidTest/java"
APK_ROOT = ROOT / "app/build/outputs/apk"
PLUGIN_PREFIX = "io.github.supermonster003.autojs6.plugin."
A11Y_SERVICE = HOST + "/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
RUN_INTENT_ACTIVITY = HOST + "/org.autojs.autojs.external.open.RunIntentActivity"
SMOKE_SCRIPT = ROOT / "sample/accessibility/smoke-legacy-api.js"
PROJECT_ASSET = "assets/project/project.json"
WRAPPER = Path(__file__).with_name("gate-a11y-wrapper.js")
MEDIA_CLASS = "org.autojs.autojs.core.media.ScriptMusicDeviceTest"
LOOPER_PLATFORM_CLASS = "org.autojs.autojs.core.looper.LooperPlatformCompatibilityDeviceTest"
LOOPER_SCRIPT_CLASS = "org.autojs.autojs.core.looper.ScriptLooperCompatibilityDeviceTest"
LOOPER_CHANGES = ("USE_NEW_MESSAGEQUEUE", "LOOPER_CLEARS_THREAD_INTERRUPTED", "OVERRIDDEN_THREAD_START_METHOD")
NATIVE_PLUGINS = (PLUGIN_PREFIX + "opencv", PLUGIN_PREFIX + "imagequant")
DEVICE_SUITES = ("cleartext", "dex", "install-state", "looper-platform", "media", "parcel", "native")
SETTLE_SECONDS = 3
EMULATOR_SUITES = ("looper-full", "a11y-smoke")
ALL_SUITES = DEVICE_SUITES + EMULATOR_SUITES


def test_methods(class_name):
    """List `Class#method` for every @Test in the checked-out source of an instrumentation class."""
    source = ANDROID_TEST / (class_name.replace(".", "/") + ".kt")
    text = source.read_text(encoding="utf-8")
    names = re.findall(r"@Test(?:\([^)]*\))?\s+(?:@\w+(?:\([^)]*\))?\s+)*fun\s+(?:`([^`]+)`|(\w+))\s*\(", text)
    methods = [class_name + "#" + (backticked or plain) for backticked, plain in names]
    if not methods or len(set(methods)) != len(methods):
        raise ValueError("Cannot list @Test methods from " + str(source))
    return methods


def find_aapt2(sdk_root):
    candidates = []
    for directory in (Path(sdk_root) / "build-tools").iterdir():
        for name in ("aapt2.exe", "aapt2"):
            if (directory / name).is_file():
                candidates.append((tuple(int(p) if p.isdigit() else 0 for p in directory.name.split(".")), directory / name))
    if not candidates:
        raise RuntimeError("No aapt2 under " + str(sdk_root))
    return max(candidates)[1]


def parse_badging(text):
    """Extract the fields the gate asserts on from `aapt2 dump badging`."""
    package = re.search(r"^package: name='([^']+)' versionCode='(\d*)' versionName='([^']*)'", text, re.MULTILINE)
    if not package:
        raise ValueError("Unrecognized badging output")
    field = lambda pattern: (re.search(pattern, text, re.MULTILINE) or [None, None])[1]
    return {"package": package[1], "versionCode": int(package[2]) if package[2] else None, "versionName": package[3],
            "compileSdk": int(field(r"compileSdkVersion='(\d+)'") or 0) or None,
            "minSdk": int(field(r"^(?:min)?[sS]dkVersion:'(\d+)'") or 0) or None,
            "targetSdk": int(field(r"^targetSdkVersion:'(\d+)'") or 0) or None,
            "debuggable": bool(re.search(r"^application-debuggable", text, re.MULTILINE)),
            "localNetworkPermission": "uses-permission: name='android.permission.ACCESS_LOCAL_NETWORK'" in text,
            "abis": sorted(re.findall(r"native-code: (.*)", text)[0].replace("'", "").split()) if "native-code:" in text else []}


def describe_apk(aapt2, path):
    result = run([str(aapt2), "dump", "badging", str(path)], timeout=120)
    if result["exitCode"] != 0:
        raise RuntimeError("aapt2 failed for " + str(path) + ": " + result["stderr"][:400])
    with zipfile.ZipFile(path) as archive:
        has_project = PROJECT_ASSET in archive.namelist()
    return {"path": str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path),
            "size": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "hasProject": has_project, **parse_badging(result["stdout"])}


def collect_apks(apk_root, with_inrt, inrt_apk=None):
    groups = {"app": sorted((apk_root / "app/debug").glob("*.apk")),
              "test": sorted((apk_root / "androidTest/app/debug").glob("*.apk"))}
    if with_inrt:
        groups["inrt"] = [inrt_apk] if inrt_apk else sorted((apk_root / "inrt/debug").glob("*.apk"))
    missing = [name for name, files in groups.items() if not files]
    if missing:
        raise RuntimeError("No APKs built for: " + ", ".join(missing) + " (run the assemble tasks first)")
    return groups


def pick_for_abi(apks, abilist):
    """Prefer the split matching the device's primary ABI, else universal, else the only file."""
    primary = (abilist or "").split(",")[0]
    for apk in apks:
        if primary and primary in apk["path"]:
            return apk
    universal = [apk for apk in apks if "universal" in apk["path"] or not apk["abis"] or len(apk["abis"]) > 1]
    if universal:
        return universal[0]
    if len(apks) == 1:
        return apks[0]
    raise RuntimeError("No APK matches ABI " + primary)


def install(device, apk, identity, allow_test, reinstall_on_mismatch):
    command = ["install", "-r"] + (["-t"] if allow_test else []) + [str(ROOT / apk["path"])]
    result = device.call(*command, timeout=600)
    output = result["stdout"] + result["stderr"]
    if "Success" in output:
        return {"success": True, "output": output.strip()[-300:]}
    if reinstall_on_mismatch and identity["emulator"] and "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in output:
        removed = device.shell("pm", "uninstall", apk["package"], timeout=120)
        retry = device.call(*command, timeout=600)
        output = retry["stdout"] + retry["stderr"]
        return {"success": "Success" in output, "reinstalled": True, "uninstall": removed["stdout"].strip(),
                "output": output.strip()[-300:]}
    return {"success": False, "output": output.strip()[-600:]}


def installed_packages(device):
    result = device.shell("pm", "list", "packages")
    if result["exitCode"] != 0:
        raise RuntimeError("pm list packages failed")
    return {line.removeprefix("package:").strip() for line in result["stdout"].splitlines() if line.startswith("package:")}


def package_target(package_dump):
    target = re.search(r"\btargetSdk=(\d+)\b", package_dump)
    return int(target[1]) if target else None


def compat_overrides(compat_dump, package, changes):
    """Package overrides recorded in `dumpsys platform_compat` for the named changes."""
    overrides = {}
    for line in compat_dump.splitlines():
        name = re.search(r"name=(\w+)", line)
        if not name or name[1] not in changes:
            continue
        block = re.search(r"packageOverrides=\{([^}]*)\}", line)
        pairs = dict(re.findall(r"([\w.]+)=(true|false)", block[1])) if block else {}
        if package in pairs:
            overrides[name[1]] = pairs[package] == "true"
    return overrides


def looper_expectations(sdk, target, overrides):
    """Expected `sdk37.clearInterrupt` / `sdk37.poolStarts` per the tools README table (no overrides)."""
    if overrides:
        raise RuntimeError("Reset host compat overrides before the gate: " + ", ".join(sorted(overrides)))
    platform_default = sdk >= 37 and target >= 37
    return {"clearInterrupt": "true" if platform_default else "false", "poolStarts": "0" if platform_default else "1"}


def select_suites(identity, target, packages, requested):
    if requested != ["auto"]:
        return list(dict.fromkeys(requested))
    sdk = int(identity["sdk"])
    suites = ["cleartext", "dex", "install-state", "looper-platform", "media"]
    if sdk >= 37:
        suites.append("parcel")
    if all(plugin in packages for plugin in NATIVE_PLUGINS):
        suites.append("native")
    if identity["emulator"]:
        suites += ["looper-full", "a11y-smoke"]
    return suites


def instrument(device, package, methods, extra, timeout, output):
    output.mkdir(parents=True, exist_ok=False)
    command = ["am", "instrument", "-w", "-r", "-e", "class", ",".join(methods)]
    for key, value in extra.items():
        command += ["-e", key, value]
    command.append(package + RUNNER_SUFFIX)
    started = utc_now()
    time.sleep(SETTLE_SECONDS)  # let the previous instrumentation's process finish dying before the next am instrument
    result = device.shell(*command, timeout=timeout)
    log = result["stdout"] + result["stderr"]
    retried = False
    if "INSTRUMENTATION_STATUS_CODE" not in log and "Process crashed" in log:
        # The runner process died before any test started (seen on API 28 when suites run back to back). Retry once.
        (output / "instrumentation-attempt1.txt").write_text(log, encoding="utf-8")
        time.sleep(SETTLE_SECONDS * 3)
        result = device.shell(*command, timeout=timeout)
        log = result["stdout"] + result["stderr"]
        retried = True
    (output / "instrumentation.txt").write_text(log, encoding="utf-8")
    verdict = parse_instrumentation(log, methods, result["exitCode"])
    record = {"started": started, "finished": utc_now(), "command": result["command"], "exitCode": result["exitCode"],
              "timeout": result.get("timeout", False), "retried": retried, **verdict}
    write_json(output / "result.json", record)
    return record


def a11y_services(device):
    value = device.shell("settings", "get", "secure", "enabled_accessibility_services")["stdout"].strip()
    return [] if value in ("", "null") else value.split(":")


def set_a11y_services(device, services):
    value = ":".join(services)
    if value and not re.fullmatch(r"[A-Za-z0-9_.$/:]+", value):
        raise ValueError("Unexpected accessibility component name")
    if value:
        device.shell("settings", "put", "secure", "enabled_accessibility_services", value)
    else:
        device.shell("settings", "delete", "secure", "enabled_accessibility_services")


def a11y_bound(device):
    dump = device.shell("dumpsys", "accessibility")["stdout"]
    # The bound entry shows the app label only; the class name appears in the enabled list.
    return bool(re.search(r"Bound services:\{[^\n]*label=AutoJs6\b", dump)) and A11Y_SERVICE in dump


def enable_a11y(device, restart_host=False):
    """Emulator preparation: (re)bind the AutoJs6 service, allow overlays, wake the screen; wait until bound.

    A service whose process was killed by instrumentation stays in the system's "Crashed services" set and is
    not rebound until the secure setting changes, so the service is always removed and re-added once.
    """
    if restart_host:
        device.shell("am", "force-stop", HOST)
        time.sleep(1)
    others = [s for s in a11y_services(device) if s != A11Y_SERVICE]
    set_a11y_services(device, others)
    time.sleep(0.5)
    set_a11y_services(device, others + [A11Y_SERVICE])
    device.shell("settings", "put", "secure", "accessibility_enabled", "1")
    device.shell("appops", "set", HOST, "SYSTEM_ALERT_WINDOW", "allow")
    device.shell("input", "keyevent", "KEYCODE_WAKEUP")
    device.shell("wm", "dismiss-keyguard")
    for _ in range(20):
        if a11y_bound(device):
            time.sleep(2)
            return True
        time.sleep(1)
    return False


def run_looper_full(adb, serial, expect, soak_ms, output):
    """Delegate to run_looper_tests.py, which performs the mid-run accessibility rebind and restores the service list."""
    command = [sys.executable, str(Path(__file__).with_name("run_looper_tests.py")), serial, "--adb", adb,
               "--output", str(output), "--clear-interrupt", expect["clearInterrupt"], "--pool-starts", expect["poolStarts"],
               "--soak-ms", str(soak_ms)]
    completed = run(command, timeout=soak_ms / 1000 + 600)
    result_path = output / "result.json"
    record = json.loads(result_path.read_text(encoding="utf-8")) if result_path.exists() else {"success": False}
    codes = record.get("statusCodes", [])
    record.update({"command": command, "exitCode": completed["exitCode"], "timeout": completed.get("timeout", False),
                   "expected": ["looper"] * record.get("testsExpected", 14), "finished": ["looper"] * codes.count(0),
                   "stderr": completed["stderr"][-400:]})
    return record


def parse_smoke_log(text):
    # The smoke prints "\nN/M checks passed", so the summary may sit on the line after its "LOG " prefix.
    summary = re.search(r"^(?:LOG )?(\d+)/(\d+) checks passed", text, re.MULTILINE)
    done = re.search(r"^GATE_DONE (ok|error.*)$", text, re.MULTILINE)
    failed = re.findall(r"^ERR \[FAIL\] (.*)$", text, re.MULTILINE)
    skipped = re.findall(r"^WARN \[SKIP\] (.*)$", text, re.MULTILINE)
    passed, total = (int(summary[1]), int(summary[2])) if summary else (None, None)
    return {"passed": passed, "total": total, "failed": failed, "skipped": skipped,
            "done": done[1] if done else None,
            "success": bool(done) and done[1] == "ok" and passed is not None and passed == total and total > 0 and not failed}


def a11y_smoke(device, output, timeout):
    output.mkdir(parents=True, exist_ok=False)
    case = uuid.uuid4().hex
    files_dir = "/data/user/0/" + HOST + "/files/"
    remote_script = files_dir + "sdk37-gate-" + case + ".js"
    remote_log = files_dir + "sdk37-gate-" + case + ".log"
    staging = "/data/local/tmp/sdk37-gate-" + case + ".js"
    pushed = device.call("push", str(SMOKE_SCRIPT), staging, timeout=60)
    if pushed["exitCode"] != 0:
        raise RuntimeError("adb push failed: " + pushed["stderr"][:200])
    copied = device.shell("run-as", HOST, "cp", staging, remote_script)
    if copied["exitCode"] != 0:
        raise RuntimeError("run-as copy failed (debuggable host required): " + (copied["stdout"] + copied["stderr"])[:200])
    device.shell("rm", staging)
    wrapper = WRAPPER.read_text(encoding="utf-8").replace("__GATE_LOG__", remote_log).replace("__GATE_SCRIPT__", remote_script)
    if re.search(r"[^\x00-\x7f]", wrapper):
        raise ValueError("The wrapper must stay ASCII for the adb transport")
    (output / "wrapper.js").write_text(wrapper, encoding="utf-8")
    # No force-stop here: stopping the host disables its accessibility service on modern Android.
    # A freshly rebound service does not know the active window until a window change, so bring the
    # host's main screen to the front first; the smoke then runs over a window with real content.
    resolved = device.shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
                            "-c", "android.intent.category.LAUNCHER", HOST)
    main_activity = next((line.strip() for line in resolved["stdout"].splitlines() if "/" in line), None)
    if main_activity:
        device.shell("am", "start", "-W", "-n", main_activity)
        time.sleep(3)
    launch = device.shell("am", "start", "-n", RUN_INTENT_ACTIVITY, "-f", "0x10008000", "--es", "script", wrapper)
    write_json(output / "launch.json", launch)
    if launch["exitCode"] != 0 or "Error" in launch["stdout"]:
        raise RuntimeError("Cannot launch the smoke wrapper: " + launch["stdout"][:200])
    deadline = time.monotonic() + timeout
    text = ""
    while time.monotonic() < deadline:
        raw = device.shell("run-as", HOST, "cat", remote_log)
        if raw["exitCode"] == 0:
            text = raw["stdout"]
            if "GATE_DONE" in text:
                break
        time.sleep(2)
    (output / "smoke.log").write_text(text, encoding="utf-8")
    device.shell("run-as", HOST, "rm", remote_script, remote_log)
    record = {"caseId": case, "script": str(SMOKE_SCRIPT.relative_to(ROOT)),
              "scriptSha256": hashlib.sha256(SMOKE_SCRIPT.read_bytes()).hexdigest(), **parse_smoke_log(text)}
    if record["done"] is None:
        record["error"] = "Timed out waiting for the smoke summary; see smoke.log"
    write_json(output / "result.json", record)
    return record


def junit_summary(directories):
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "files": 0}
    failing = []
    for directory in directories:
        for report in sorted(Path(directory).rglob("TEST-*.xml")):
            suite = ET.parse(report).getroot()
            totals["files"] += 1
            for key in ("tests", "failures", "errors", "skipped"):
                totals[key] += int(suite.get(key, 0))
            for case in suite.iter("testcase"):
                if case.find("failure") is not None or case.find("error") is not None:
                    failing.append(case.get("classname", "") + "#" + case.get("name", ""))
    totals["failing"] = failing
    totals["success"] = totals["files"] > 0 and totals["failures"] == 0 and totals["errors"] == 0
    return totals


def launch_check(device, package):
    """Start the launcher activity; require a live process and no AndroidRuntime crash logged since the launch."""
    resolved = device.shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
                            "-c", "android.intent.category.LAUNCHER", package)
    component = next((line.strip() for line in resolved["stdout"].splitlines() if "/" in line), None)
    if not component:
        return {"success": False, "error": "No launcher activity: " + resolved["stdout"].strip()[:200]}
    device.shell("am", "force-stop", package)
    since = device.shell("date", "+%m-%d %H:%M:%S.000")["stdout"].strip()
    start = device.shell("am", "start", "-W", "-n", component)
    time.sleep(4)
    pid = device.shell("pidof", package)
    crash = device.call("logcat", "-d", "-t", since, "-s", "AndroidRuntime:E") if since else device.call("logcat", "-d", "-t", "400", "-s", "AndroidRuntime:E")
    crash_lines = [line for line in crash["stdout"].splitlines() if package in line]
    device.shell("am", "force-stop", package)
    alive = pid["exitCode"] == 0 and pid["stdout"].strip() != ""
    return {"component": component, "start": start["stdout"].strip()[-300:], "alive": alive, "logcatSince": since,
            "crashLogged": bool(crash_lines), "crashLines": crash_lines[:6], "success": alive and not crash_lines}


def markdown(gate):
    lines = ["# SDK 37 gate: " + gate["device"].get("model", "?") + " (" + gate["device"]["serial"] + ")", "",
             f"- Started {gate['started']}, finished {gate['finished']}",
             f"- API {gate['device']['sdk']}, fingerprint `{gate['device'].get('fingerprint')}`, kernel page {gate['device'].get('kernelPageSize')}",
             f"- Expected targetSdk {gate['expectedTarget']}; overall **{gate['overall']}**", ""]
    if gate.get("artifacts"):
        lines += ["| APK | package | compile / target / min | debuggable | LAN permission | SHA-256 |", "| --- | --- | --- | --- | --- | --- |"]
        for group, apks in gate["artifacts"].items():
            for apk in apks:
                lines.append(f"| {group}: `{Path(apk['path']).name}` | {apk['package']} | {apk['compileSdk']} / {apk['targetSdk']} / {apk['minSdk']} "
                             f"| {apk['debuggable']} | {apk['localNetworkPermission']} | `{apk['sha256'][:16]}...` |")
        lines.append("")
    lines += ["| Step | Result | Detail |", "| --- | --- | --- |"]
    for step in gate["steps"]:
        lines.append(f"| {step['name']} | {step['status']} | {step.get('detail', '')} |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--expected-target", type=int, required=True, help="targetSdk of the APKs under test")
    parser.add_argument("--output", type=Path, required=True, help="New evidence directory")
    parser.add_argument("--apk-root", type=Path, default=APK_ROOT)
    parser.add_argument("--sdk-root", default=None, help="Android SDK root for aapt2 (default: derived from --adb)")
    parser.add_argument("--install", action="store_true", help="Install the app and test APKs before the suites")
    parser.add_argument("--with-inrt", action="store_true", help="Also check / install / launch the inrt debug template")
    parser.add_argument("--inrt-apk", type=Path, help="A packaged inrt fixture (with assets/project) to use instead of the bare template")
    parser.add_argument("--reinstall-on-mismatch", action="store_true", help="Emulator only: uninstall on signature mismatch and retry")
    parser.add_argument("--suite", action="append", default=[], choices=("auto",) + ALL_SUITES, help="Repeat to select; default auto")
    parser.add_argument("--enable-a11y", action="store_true", help="Emulator only: enable the AutoJs6 accessibility service for the smoke")
    parser.add_argument("--soak-ms", type=int, default=5000, help="looper-full soak; the README uses 120000 for the final target 37 run")
    parser.add_argument("--timeout", type=int, default=300, help="Per-suite instrumentation timeout in seconds")
    parser.add_argument("--junit-results", type=Path, action="append", default=[], help="Gradle test-results directories to summarize")
    parser.add_argument("--skip-artifacts", action="store_true", help="Do not require built APKs (installed build is used as is)")
    args = parser.parse_args()
    requested = args.suite or ["auto"]
    if not 30 <= args.timeout <= 900:
        parser.error("--timeout must be between 30 and 900 seconds")
    args.output.mkdir(parents=True, exist_ok=False)
    device = Device(args.adb, args.serial)
    device.require_online()
    identity = device.identity()
    gate = {"started": utc_now(), "device": identity, "expectedTarget": args.expected_target, "requestedSuites": requested,
            "steps": [], "overall": "FAIL"}
    steps = gate["steps"]

    def step(name, status, detail="", **extra):
        steps.append({"name": name, "status": status, "detail": detail, **extra})
        print(f"[{status}] {name}: {detail}")
        return status

    if not identity["emulator"] and (args.reinstall_on_mismatch or args.enable_a11y or
                                     any(suite in EMULATOR_SUITES for suite in requested)):
        parser.error("Emulator-only options and suites cannot target a physical device")
    try:
        apks = {}
        if not args.skip_artifacts:
            aapt2 = find_aapt2(args.sdk_root or Path(args.adb).resolve().parents[1])
            groups = collect_apks(args.apk_root, args.with_inrt, args.inrt_apk)
            gate["artifacts"] = {group: [describe_apk(aapt2, path) for path in files] for group, files in groups.items()}
            for group, described in gate["artifacts"].items():
                expected_package = {"app": HOST, "test": HOST + ".test", "inrt": INRT}[group]
                for apk in described:
                    ok = apk["package"] == expected_package and apk["debuggable"]
                    if group != "test":
                        ok = ok and apk["targetSdk"] == args.expected_target and apk["localNetworkPermission"] == (args.expected_target >= 37)
                    step(f"badging {group} {Path(apk['path']).name}", "PASS" if ok else "FAIL",
                         f"{apk['package']} compile {apk['compileSdk']} target {apk['targetSdk']} LAN-permission {apk['localNetworkPermission']}")
            apks = {group: pick_for_abi(described, identity.get("abiList")) for group, described in gate["artifacts"].items()}
            if args.install:
                for group in ("app", "test") + (("inrt",) if args.with_inrt else ()):
                    result = install(device, apks[group], identity, group == "test", args.reinstall_on_mismatch)
                    step("install " + group, "PASS" if result["success"] else "FAIL", result["output"][-160:], **result)
                    if not result["success"]:
                        raise RuntimeError("Installation failed for " + group)
        elif args.install:
            parser.error("--install requires built artifacts")
        snapshot(device, [HOST] + ([INRT] if args.with_inrt else []), args.output / "before")
        host_dump = (args.output / "before" / (HOST + "-package.txt")).read_text(encoding="utf-8")
        target = package_target(host_dump)
        debuggable = bool(re.search(r"(?:pkgFlags|flags)=\[[^\]]*\bDEBUGGABLE\b", host_dump))
        step("installed host", "PASS" if target == args.expected_target and debuggable else "FAIL",
             f"targetSdk {target}, debuggable {debuggable}")
        if target != args.expected_target or not debuggable:
            raise RuntimeError("The installed host does not match the expected target or is not debuggable")
        if args.with_inrt:
            inrt_dump = (args.output / "before" / (INRT + "-package.txt")).read_text(encoding="utf-8")
            inrt_target = package_target(inrt_dump)
            step("installed inrt", "PASS" if inrt_target == args.expected_target else "FAIL", f"targetSdk {inrt_target}")
            if apks.get("inrt") and not apks["inrt"]["hasProject"]:
                step("inrt launch", "SKIP", "bare template without " + PROJECT_ASSET + "; pass --inrt-apk with a packaged fixture to launch")
            else:
                launched = launch_check(device, INRT)
                step("inrt launch", "PASS" if launched["success"] else "FAIL",
                     f"alive {launched.get('alive')}, crash logged {launched.get('crashLogged')}", **launched)
        runners = device.shell("pm", "list", "instrumentation")["stdout"]
        if f"instrumentation:{HOST}{RUNNER_SUFFIX} (target={HOST})" not in runners:
            raise RuntimeError("The matching instrumentation APK is not installed")
        if apks.get("test"):
            on_device = device.shell("pm", "path", HOST + ".test")["stdout"].strip().removeprefix("package:")
            digest = device.shell("sha256sum", on_device)["stdout"].split()
            same = bool(digest) and digest[0] == apks["test"]["sha256"]
            step("test APK identity", "PASS" if same else "FAIL", "installed test APK matches the built one" if same else "installed test APK differs from the built one")
        packages = installed_packages(device)
        compat_dump = (args.output / "before" / "platform-compat.txt").read_text(encoding="utf-8")
        suites = select_suites(identity, target, packages, requested)
        gate["selectedSuites"] = suites
        sdk = int(identity["sdk"])
        for suite in suites:
            out = args.output / suite
            try:
                if suite in ("cleartext", "dex", "install-state", "native"):
                    if suite == "native" and not all(p in packages for p in NATIVE_PLUGINS):
                        step(suite, "SKIP", "OpenCV / ImageQuant plugins not installed")
                        continue
                    record = instrument(device, HOST, SUITES[suite], {}, args.timeout, out)
                elif suite == "looper-platform":
                    expect = looper_expectations(sdk, target, compat_overrides(compat_dump, HOST, LOOPER_CHANGES))
                    record = instrument(device, HOST, SUITES[suite], {"sdk37.clearInterrupt": expect["clearInterrupt"],
                                                                      "sdk37.poolStarts": expect["poolStarts"]}, args.timeout, out)
                    record["expectations"] = expect
                elif suite == "media":
                    record = instrument(device, HOST, test_methods(MEDIA_CLASS), {}, args.timeout, out)
                elif suite == "parcel":
                    if sdk < 37:
                        step(suite, "SKIP", "PARCEL_HARDENING exists on API 37+ only")
                        continue
                    line = next((l for l in compat_dump.splitlines() if "name=PARCEL_HARDENING" in l), None)
                    if not line:
                        raise RuntimeError("PARCEL_HARDENING missing from the compat dump")
                    policy = effective_parcel_policy(line, HOST, target)
                    record = instrument(device, HOST, CONTROLS + WIRE, {"sdk37.parcelHardening": "true" if policy["enabled"] else "false",
                                                                        "sdk37.pluginPackages": ""}, args.timeout, out)
                    record["policy"] = policy
                elif suite == "looper-full":
                    if not (enable_a11y(device) if args.enable_a11y else A11Y_SERVICE in a11y_services(device)):
                        step(suite, "SKIP", "AutoJs6 accessibility is not enabled (use --enable-a11y on an emulator)")
                        continue
                    expect = looper_expectations(sdk, target, compat_overrides(compat_dump, HOST, LOOPER_CHANGES))
                    time.sleep(SETTLE_SECONDS)
                    record = run_looper_full(args.adb, args.serial, expect, args.soak_ms, out)
                    record["expectations"] = expect
                elif suite == "a11y-smoke":
                    # Instrumentation leaves the service unbound ("Crashed services"); stop any stuck engine and rebind first.
                    if not (enable_a11y(device, restart_host=True) if args.enable_a11y else a11y_bound(device)):
                        step(suite, "SKIP", "AutoJs6 accessibility is not enabled (use --enable-a11y on an emulator)")
                        continue
                    record = a11y_smoke(device, out, timeout=max(args.timeout, 240))
                    step(suite, "PASS" if record["success"] else "FAIL",
                         f"{record['passed']}/{record['total']} checks, {len(record['skipped'])} skipped" if record["total"] else record.get("error", "no summary"), **record)
                    continue
                else:
                    raise RuntimeError("Unknown suite " + suite)
                step(suite, "PASS" if record["success"] else "FAIL",
                     f"{len(record['finished'])}/{len(record['expected'])} completed" + (", adb timeout" if record.get("timeout") else ""),
                     **{k: v for k, v in record.items() if k not in ("command",)})
            except (ValueError, RuntimeError, OSError) as error:
                step(suite, "ERROR", str(error))
        if args.junit_results:
            summary = junit_summary(args.junit_results)
            step("jvm unit tests", "PASS" if summary["success"] else "FAIL",
                 f"{summary['tests']} tests, {summary['failures']} failures, {summary['errors']} errors, {summary['skipped']} skipped", **summary)
        snapshot(device, [HOST] + ([INRT] if args.with_inrt else []), args.output / "after", include_logs=True)
    except (ValueError, RuntimeError, OSError) as error:
        step("gate", "ERROR", str(error))
    gate["finished"] = utc_now()
    gate["overall"] = "PASS" if steps and all(s["status"] in ("PASS", "SKIP") for s in steps) else "FAIL"
    write_json(args.output / "gate.json", gate)
    (args.output / "gate.md").write_text(markdown(gate), encoding="utf-8")
    print(f"Overall {gate['overall']}: {args.output / 'gate.md'}")
    return 0 if gate["overall"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
