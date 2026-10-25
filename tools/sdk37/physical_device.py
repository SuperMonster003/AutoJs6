"""Physical-device inventory, read-only snapshots, checklist export and selected instrumentation.

Only instrument starts tests and consequently restarts the selected app. Coordinate
and Ace suites temporarily change rotation/preferences and restore them in cleanup.
The runner never installs APKs, reboots devices or clears application data.
"""
import argparse
import csv
import datetime
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
CHECKLIST = ROOT / "docs/dev/android-sdk-37-physical-device-tests.md"
PREFIX = "org.autojs.autojs."
SUITES = {
    "coordinate-api": [PREFIX + "runtime.api.modules.global.CoordinateScaleDeviceTest#" + method for method in (
        "publicFunctionsTrackRealRotationAndIndependentBaseConfigurations",
        "invalidPairDoesNotPartiallyConsumeBases",
        "scaleSettersKeepIntegerCoercionAndRejectTruncatedZeroBeforeMutation",
    )],
    "ace-api": [PREFIX + "core.plugin.editor.AceEditorPluginHostDeviceTest#defaultDeclarationsAndJsoxWorkThroughTheInstalledHostSession"],
    "cleartext": [PREFIX + "core.http.CleartextTrafficDeviceTest#" + method for method in (
        "installedPolicyAllowsScriptEndpointsAndKeepsGithubHttpsOnly",
        "httpToAnOrdinaryHostnameReceivesTheFixtureResponse",
        "githubHttpIsRejectedBeforeAnyRequestReachesTheServer",
        "rhinoHttpUsesTheInstalledPolicy",
    )],
    "dex": [PREFIX + "rhino.AndroidClassLoaderRawDexDeviceTest#rawDexAndReusedCacheRemainIntactAfterClassLoading"],
    "native": [
        PREFIX + "core.plugin.opencv.OpenCvPluginHostDeviceTest#installedOpenCvPluginLoadsAndRunsThroughHostJavaAbi",
        PREFIX + "core.plugin.opencv.OpenCvPluginHostDeviceTest#nativeOpenCvErrorsReachJavaWithoutKillingTheHostProcess",
        PREFIX + "core.plugin.imagequant.ImageQuantPluginHostDeviceTest#installedPluginLoadsAndProducesIndexedPng",
    ],
    "install-state": [
        PREFIX + "ui.main.scripts.PackageInstallStatusStoreDeviceTest#callbacksWithMismatchedSessionOrIntentIdentityAreIgnored",
        PREFIX + "ui.main.scripts.PackageInstallStatusStoreDeviceTest#separateSessionsSurviveReconstructionAndAcknowledgement",
    ],
    "looper-platform": [
        PREFIX + "core.looper.LooperPlatformCompatibilityDeviceTest#compatCancellationSurvivesPlatformInterruptClearing",
        PREFIX + "core.looper.LooperPlatformCompatibilityDeviceTest#threadPoolsRunWorkersWhenStartOverrideIsBypassed",
    ],
}


def utc_now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def run(command, timeout=30):
    try:
        completed = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
        return {"command": command, "exitCode": completed.returncode, "stdout": completed.stdout, "stderr": completed.stderr}
    except subprocess.TimeoutExpired as error:
        def decode(value):
            return value.decode("utf-8", errors="replace") if isinstance(value, bytes) else value or ""
        return {"command": command, "exitCode": None, "timeout": True,
                "stdout": decode(error.stdout), "stderr": decode(error.stderr)}


class Device:
    def __init__(self, adb, serial):
        if not re.fullmatch(r"[A-Za-z0-9_.:-]+", serial) or serial.startswith("-"):
            raise ValueError("Invalid adb serial")
        self.adb, self.serial = adb, serial

    def call(self, *args, timeout=30):
        return run([self.adb, "-s", self.serial, *args], timeout)

    def shell(self, *args, timeout=30):
        # adb shell reconstructs remote shell text; quote every argument for that shell.
        return self.call("shell", shlex.join(args), timeout=timeout)

    def require_online(self):
        state = self.call("get-state")
        if state["exitCode"] != 0 or state["stdout"].strip() != "device":
            raise RuntimeError("Selected device is not online: " + self.serial)

    def identity(self):
        result = self.shell("getprop")
        if result["exitCode"] != 0:
            raise RuntimeError("Cannot read device properties")
        props = dict(re.findall(r"^\[([^]]+)\]: \[(.*)\]$", result["stdout"], re.MULTILINE))
        getconf = self.shell("getconf", "PAGE_SIZE")
        smaps = self.shell("cat", "/proc/self/smaps")
        kernel = re.search(r"KernelPageSize:\s+(\d+)\s+kB", smaps["stdout"])
        return {"serial": self.serial, "model": props.get("ro.product.model"),
                "manufacturer": props.get("ro.product.manufacturer"), "sdk": props.get("ro.build.version.sdk"),
                "release": props.get("ro.build.version.release"), "fingerprint": props.get("ro.build.fingerprint"),
                "abiList": props.get("ro.product.cpu.abilist"), "buildType": props.get("ro.build.type"),
                "emulator": self.serial.startswith("emulator-") or props.get("ro.kernel.qemu") == "1",
                "getconfPageSize": int(getconf["stdout"].strip()) if getconf["exitCode"] == 0 and getconf["stdout"].strip().isdigit() else None,
                "kernelPageSize": int(kernel[1]) * 1024 if kernel else None,
                "pageSizeReadErrors": [r for r in (getconf, smaps) if r["exitCode"] != 0]}


def package_name(value):
    if not re.fullmatch(r"[A-Za-z]\w*(?:\.[A-Za-z]\w*)+", value):
        raise argparse.ArgumentTypeError("Expected an Android applicationId")
    return value


def parse_instrumentation(text, expected_methods, exit_code):
    """Require each selected test's completion identity, with no skips or missing results."""
    bundle, finished, codes = {}, [], []
    for line in text.splitlines():
        field = re.match(r"INSTRUMENTATION_STATUS: (class|test)=(.*)", line)
        if field:
            bundle[field[1]] = field[2].strip()
        code = re.fullmatch(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line.strip())
        if code:
            number = int(code[1])
            codes.append(number)
            if number == 0:
                finished.append(bundle.get("class", "") + "#" + bundle.get("test", ""))
            bundle = {}
    failed = any(number not in (0, 1) for number in codes)
    terminal = re.findall(r"^INSTRUMENTATION_CODE: (-?\d+)\s*$", text, re.MULTILINE) == ["-1"]
    ok = (exit_code == 0 and terminal and not failed and sorted(finished) == sorted(expected_methods)
          and not any(marker in text for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED:", "Process crashed.")))
    return {"success": ok, "expected": expected_methods, "finished": finished, "statusCodes": codes}


def snapshot(device, packages, output, include_logs=False):
    output.mkdir(parents=True, exist_ok=False)
    identity = device.identity()
    write_json(output / "device.json", identity)
    commands = {"platform-compat": ("dumpsys", "platform_compat"), "audio": ("dumpsys", "audio"),
                "display-size": ("wm", "size"), "display-density": ("wm", "density"),
                "accessibility-enabled": ("settings", "get", "secure", "enabled_accessibility_services"),
                "memory-limiter": ("am", "memory-limiter", "status")}
    for package in packages:
        commands[package + "-package"] = ("dumpsys", "package", package)
        commands[package + "-appops"] = ("appops", "get", package)
        commands[package + "-services"] = ("dumpsys", "activity", "services", package)
    results = {}
    for name, args in commands.items():
        result = device.shell(*args)
        (output / (name + ".txt")).write_text(result["stdout"] + result["stderr"], encoding="utf-8")
        results[name] = {k: v for k, v in result.items() if k not in ("stdout", "stderr")}
    if include_logs:
        result = device.call("logcat", "-d", "-t", "2000", "-v", "threadtime", "ActivityTaskManager:I",
                             "AndroidRuntime:E", "Sdk37LocalNetwork:I", "AudioHardening:V", "PackageInstallStatus:V", "*:S")
        (output / "logcat-tail.txt").write_text(result["stdout"] + result["stderr"], encoding="utf-8")
        results["logcat-tail"] = {k: v for k, v in result.items() if k not in ("stdout", "stderr")}
    write_json(output / "snapshot.json", {"time": utc_now(), "readOnly": True, "commands": results,
                                         "functionalResult": "NOT_RUN", "logsMayContainEarlierCases": include_logs})
    return identity


def export_checklist(output):
    rows = []
    for line in CHECKLIST.read_text(encoding="utf-8").splitlines():
        if re.match(r"^\| \[ \] [A-Z]+\d+ \|", line):
            fields = [field.strip() for field in line.strip().strip("|").split("|")]
            if len(fields) != 6:
                raise ValueError("Checklist row must have six columns: " + line)
            rows.append([fields[0][4:], *fields[1:], "NOT_RUN", "", "", "", ""])
    if not rows or len({row[0] for row in rows}) != len(rows):
        raise ValueError("Missing or duplicate checklist IDs")
    output.mkdir(parents=True, exist_ok=False)
    with (output / "cases.csv").open("w", newline="", encoding="utf-8-sig") as stream:
        writer = csv.writer(stream)
        writer.writerow(["id", "case", "devices", "method", "expected", "roadmap", "status",
                         "actual_device_and_apks", "observed", "evidence", "reason_if_blocked_or_na"])
        writer.writerows(rows)
    write_json(output / "checklist-source.json", {"source": str(CHECKLIST), "sha256": hashlib.sha256(CHECKLIST.read_bytes()).hexdigest(),
                                                "count": len(rows), "statuses": ["NOT_RUN", "PASS", "FAIL", "BLOCKED", "N/A"]})
    print(f"Exported {len(rows)} NOT_RUN cases: {output / 'cases.csv'}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    for name in ("inventory", "snapshot", "instrument", "checklist"):
        command = sub.add_parser(name)
        command.add_argument("--output", type=Path, required=True, help="New evidence directory")
        if name != "checklist":
            command.add_argument("--adb", default="adb")
        if name in ("snapshot", "instrument"):
            command.add_argument("--serial", required=True)
            command.add_argument("--package", type=package_name, default="org.autojs.autojs6")
        if name == "snapshot":
            command.add_argument("--include-logs", action="store_true", help="Save a bounded diagnostic log tail; not per-case pass evidence")
        if name == "instrument":
            command.add_argument("--suite", choices=SUITES, required=True)
            command.add_argument("--expected-target", type=int, required=True)
            command.add_argument("--timeout", type=int, default=300)
            command.add_argument("--clear-interrupt", choices=("true", "false"))
            command.add_argument("--pool-starts", choices=("0", "1"))
    args = parser.parse_args()
    if args.action == "checklist":
        export_checklist(args.output)
        return 0
    if args.action == "inventory":
        args.output.mkdir(parents=True, exist_ok=False)
        found = run([args.adb, "devices", "-l"])
        (args.output / "adb-devices.txt").write_text(found["stdout"] + found["stderr"], encoding="utf-8")
        if found["exitCode"] != 0:
            raise RuntimeError("adb devices failed")
        devices = []
        for line in found["stdout"].splitlines()[1:]:
            fields = line.split()
            if len(fields) < 2:
                continue
            if fields[1] != "device":
                devices.append({"serial": fields[0], "state": fields[1], "queried": False})
                continue
            try:
                devices.append({"state": "device", "queried": True, **Device(args.adb, fields[0]).identity()})
            except (ValueError, RuntimeError) as error:
                devices.append({"serial": fields[0], "state": "device", "error": str(error)})
        write_json(args.output / "inventory.json", {"time": utc_now(), "readOnly": True, "functionalResult": "NOT_RUN", "devices": devices})
        for device in devices:
            print(f"{device['serial']}: {device.get('model', device['state'])}, API {device.get('sdk', '?')}, kernel page {device.get('kernelPageSize', '?')}")
        return 0
    device = Device(args.adb, args.serial)
    device.require_online()
    if args.action == "snapshot":
        snapshot(device, [args.package], args.output, args.include_logs)
        print("Read-only snapshot; functional result NOT_RUN: " + str(args.output))
        return 0
    if not 30 <= args.timeout <= 900:
        parser.error("--timeout must be between 30 and 900 seconds")
    if args.suite == "looper-platform" and (args.clear_interrupt is None or args.pool_starts is None):
        parser.error("looper-platform requires explicit --clear-interrupt and --pool-starts expectations")
    if args.suite != "looper-platform" and (args.clear_interrupt is not None or args.pool_starts is not None):
        parser.error("Looper expectations only apply to looper-platform")
    args.output.mkdir(parents=True, exist_ok=False)
    snapshot(device, [args.package], args.output / "before")
    package = (args.output / "before" / (args.package + "-package.txt")).read_text(encoding="utf-8")
    target = re.search(r"\btargetSdk=(\d+)\b", package)
    if not target or int(target[1]) != args.expected_target:
        raise RuntimeError("Installed targetSdk does not match --expected-target; see before snapshot")
    if not re.search(r"(?:pkgFlags|flags)=\[[^\]]*\bDEBUGGABLE\b", package):
        raise RuntimeError("The installed app must be debuggable")
    runner = args.package + ".test/androidx.test.runner.AndroidJUnitRunner"
    runners = device.shell("pm", "list", "instrumentation")
    if f"instrumentation:{runner} (target={args.package})" not in runners["stdout"]:
        raise RuntimeError("Install the matching instrumentation APK before running this suite")
    command = ["am", "instrument", "-w", "-r", "-e", "class", ",".join(SUITES[args.suite])]
    if args.suite == "looper-platform":
        command += ["-e", "sdk37.clearInterrupt", args.clear_interrupt, "-e", "sdk37.poolStarts", args.pool_starts]
    command.append(runner)
    started = utc_now()
    result = device.shell(*command, timeout=args.timeout)
    log = result["stdout"] + result["stderr"]
    (args.output / "instrumentation.txt").write_text(log, encoding="utf-8")
    verdict = parse_instrumentation(log, SUITES[args.suite], result["exitCode"])
    write_json(args.output / "result.json", {"started": started, "finished": utc_now(), "suite": args.suite,
                                            "serial": args.serial, "package": args.package,
                                            "expectedTarget": args.expected_target,
                                            "command": result["command"], "exitCode": result["exitCode"],
                                            "timeout": result.get("timeout", False), **verdict})
    snapshot(device, [args.package], args.output / "after", include_logs=True)
    print(json.dumps(verdict))
    if result.get("timeout"):
        print("adb timed out; the selected app may still be running instrumentation. Inspect it before starting another suite.", file=sys.stderr)
    return 0 if verdict["success"] else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, OSError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(2)
