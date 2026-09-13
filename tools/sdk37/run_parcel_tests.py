"""Run Parcel negative controls and selected real plugin paths on an explicitly selected test device.

Does not install, clear app data, change permissions, or change compatibility overrides.
Instrumentation restarts the host; plugin cases execute the installed providers.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

from physical_device import Device, package_name, parse_instrumentation, snapshot, utc_now, write_json


HOST = "org.autojs.autojs6"
PREFIX = "org.autojs.autojs."
CONTROL_CLASS = PREFIX + "compat.ParcelCompatibilityDeviceTest"
PLUGIN_CLASS = PREFIX + "compat.ParcelPluginRoundTripDeviceTest"
CONTROLS = [CONTROL_CLASS + "#" + name for name in (
    "underReadingParcelableMatchesTheLocalCompatPolicy", "underReadingParcelableMatchesTheRemoteCompatPolicy")]
PLUGIN_PREFIX = "io.github.supermonster003.autojs6.plugin."
PLUGINS = {
    "opencv": ("opencv", PREFIX + "core.plugin.opencv.OpenCvPluginHostDeviceTest#installedOpenCvPluginLoadsAndRunsThroughHostJavaAbi"),
    "imagequant": ("imagequant", PREFIX + "core.plugin.imagequant.ImageQuantPluginHostDeviceTest#installedPluginLoadsAndProducesIndexedPng"),
    "barcode": ("mlkit.barcode", PLUGIN_CLASS + "#barcodeDecodesImagePfdAndReturnsStructuredResults"),
    "node": ("nodejs", PREFIX + "engine.NodeHostPluginIntegrationInstrumentationTest#pluginExecutionRoundTripRuns"),
    "lua": ("lua.runtime", PREFIX + "core.plugin.lua.LuaOfficialRuntimeSmokeTest#officialProviderExecutesAndReleasesSequentialSessions"),
    "quickjs": ("quickjs.runtime", PLUGIN_CLASS + "#quickJsExecutesSourcePfdAndReturnsConsoleCallback"),
    "python": ("python.runtime", PLUGIN_CLASS + "#pythonExecutesThroughTheCurrentHostProtocolAndReturnsOutput"),
    "yolo": ("yolo.ncnn", PREFIX + "core.plugin.yolo.YoloRealProviderR2AndroidTest#productionHostUsesRemoteBinderOwnsPfdAndReopensAfterIdempotentClose"),
    "mcp": ("mcp.server", PREFIX + "core.plugin.mcp.McpServerPluginRoundTripTest#openServerReachesRunningWithTheHostAttachedAndStopsCleanly"),
}
WIRE = [CONTROL_CLASS + "#" + name for name in (
    "executionConfigPreservesItsExistingWireFieldsAndBoundary",
    "pluginInfoPreservesNullableArraysLongsAndNestedCapabilities",
    "taskerJsonAndShortcutExtrasSurviveRemoteUnparcelling")]


def effective_parcel_policy(line, package, target):
    """Read the effective override, never a pending/raw override, from the tested API 37 format."""
    if not re.search(r"^\s*ChangeId\(416031865; name=PARCEL_HARDENING; enableSinceTargetSdk=37(?:;|\))", line):
        raise ValueError("Unrecognized PARCEL_HARDENING definition")
    overrides = re.search(r"(?:; )packageOverrides=\{([^}]*)\}", line)
    if overrides:
        pairs = dict(re.findall(r"([\w.]+)=(true|false)(?:,|$)", overrides[1]))
        if package in pairs:
            return {"enabled": pairs[package] == "true", "source": "packageOverrides", "target": target}
    return {"enabled": target >= 37, "source": "enableSinceTargetSdk=37", "target": target}


def installed_artifact(device, package):
    paths = device.shell("pm", "path", package)
    files = [line.removeprefix("package:") for line in paths["stdout"].splitlines() if line.startswith("package:")]
    if paths["exitCode"] != 0 or not files:
        raise RuntimeError("Required package is not installed: " + package)
    artifacts = []
    for path in files:
        result = device.shell("sha256sum", path)
        digest = result["stdout"].split()[0] if result["stdout"].split() else ""
        if result["exitCode"] != 0 or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise RuntimeError("Cannot fingerprint installed APK: " + package)
        artifacts.append({"devicePath": path, "sha256": digest})
    return {"package": package, "artifacts": artifacts}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--expected-target", type=int, required=True)
    parser.add_argument("--hardening", choices=("true", "false"), required=True,
                        help="Expected semantic result; this tool does not toggle the change")
    parser.add_argument("--suite", choices=("controls", "wire", "metadata", *PLUGINS), required=True)
    parser.add_argument("--plugin-package", type=package_name, action="append", default=[],
                        help="Required packages for the metadata suite; repeat for each plugin")
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not 30 <= args.timeout <= 900:
        parser.error("--timeout must be between 30 and 900 seconds")
    if bool(args.plugin_package) != (args.suite == "metadata"):
        parser.error("--plugin-package is required only for the metadata suite")
    packages = sorted(set(args.plugin_package))
    expected = list(CONTROLS)
    if args.suite == "wire":
        expected += WIRE
    elif args.suite == "metadata":
        expected += [CONTROL_CLASS + "#installedPluginMetadataIsReadThroughTheProductionRepository"]
    elif args.suite in PLUGINS:
        suffix, method = PLUGINS[args.suite]
        packages.append(PLUGIN_PREFIX + suffix)
        expected.append(method)
    device = Device(args.adb, args.serial)
    device.require_online()
    args.output.mkdir(parents=True, exist_ok=False)
    receipt = {"started": utc_now(), "suite": args.suite, "expectedTarget": args.expected_target,
               "expectedHardening": args.hardening, "success": False, "expected": expected,
               "runnerSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    try:
        identity = snapshot(device, [HOST, *packages], args.output / "before")
        receipt["device"] = identity
        if int(identity["sdk"]) < 37:
            raise RuntimeError("Use Android 17 for this compatibility test matrix")
        state = (args.output / "before" / (HOST + "-package.txt")).read_text(encoding="utf-8")
        target = re.search(r"\btargetSdk=(\d+)\b", state)
        if not target or int(target[1]) != args.expected_target or "DEBUGGABLE" not in state:
            raise RuntimeError("Expected a debuggable host APK with the selected targetSdk")
        receipt["installed"] = [installed_artifact(device, p) for p in [HOST, HOST + ".test", *packages]]
        compat = (args.output / "before/platform-compat.txt").read_text(encoding="utf-8")
        receipt["parcelCompatLine"] = next((line for line in compat.splitlines() if "name=PARCEL_HARDENING" in line), None)
        if not receipt["parcelCompatLine"] or "416031865" not in receipt["parcelCompatLine"]:
            raise RuntimeError("PARCEL_HARDENING is missing from the device compatibility dump")
        receipt["effectivePolicies"] = {}
        for package in [HOST, *packages]:
            state = (args.output / "before" / (package + "-package.txt")).read_text(encoding="utf-8")
            target = re.search(r"\btargetSdk=(\d+)\b", state)
            if not target:
                raise RuntimeError("Missing targetSdk for " + package)
            policy = effective_parcel_policy(receipt["parcelCompatLine"], package, int(target[1]))
            receipt["effectivePolicies"][package] = policy
            if policy["enabled"] != (args.hardening == "true"):
                raise RuntimeError("Unexpected effective Parcel policy for " + package + ": " + str(policy))
        command = ["am", "instrument", "-w", "-r", "-e", "class", ",".join(expected),
                   "-e", "sdk37.parcelHardening", args.hardening,
                   "-e", "sdk37.pluginPackages", ",".join(packages),
                   "-e", "autojs.luaRuntime.officialProvider.enabled", "true",
                   "-e", "yoloR2Enabled", "true",
                   HOST + ".test/androidx.test.runner.AndroidJUnitRunner"]
        device_time = device.shell("date", "+%m-%d %H:%M:%S.000")
        if device_time["exitCode"] != 0:
            raise RuntimeError("Cannot establish the device log start time")
        receipt["logcatSince"] = device_time["stdout"].strip()
        result = device.shell(*command, timeout=args.timeout)
        log = result["stdout"] + result["stderr"]
        (args.output / "instrumentation.txt").write_text(log, encoding="utf-8")
        receipt.update({"command": result["command"], "exitCode": result["exitCode"], "timeout": result.get("timeout", False),
                        **parse_instrumentation(log, expected, result["exitCode"])})
    except (ValueError, RuntimeError, OSError) as error:
        receipt["error"] = str(error)
    finally:
        receipt["ended"] = utc_now()
        write_json(args.output / "result.json", receipt)
        if "device" in receipt:
            snapshot(device, [HOST, *packages], args.output / "after")
            limit = ["-T", receipt["logcatSince"]] if "logcatSince" in receipt else ["-t", "3000"]
            logs = device.shell("logcat", "-d", *limit, "-v", "threadtime",
                                "SDK37-Parcel:I", "Parcel:F", "AndroidRuntime:E", "GlobalConsole:D", "*:S")
            (args.output / "logcat-tail.txt").write_text(logs["stdout"] + logs["stderr"], encoding="utf-8")
            receipt["logcatExitCode"] = logs["exitCode"]
            receipt["logcatTimeout"] = logs.get("timeout", False)
            write_json(args.output / "result.json", receipt)
    print(json.dumps({key: receipt[key] for key in ("suite", "success", "error", "statusCodes") if key in receipt}))
    return 0 if receipt["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
