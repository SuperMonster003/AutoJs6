"""Run explicit TLS / Rhino cases on a disposable emulator; never changes trust or compat settings."""
import argparse
import json
from pathlib import Path
import re
import sys

from physical_device import Device, parse_instrumentation, snapshot, utc_now, write_json


TEST_CLASS = "org.autojs.autojs.core.http.NetworkSecurityCompatibilityDeviceTest"
SUITES = {
    "transport": ["defaultTrustRejectsThePrivateCa", "debugCaTrustIsLimitedToItsDeclaredDomains",
                  "explicitCtRejectsAnUnloggedButTrustedChain"],
    "policies": ["ctAndEchPolicyDefaultsMatchTheRecordedCompatState"],
    "rhino": ["insecureOptionDoesNotLeakToTheNextRhinoRequest", "insecureAliasDoesNotLeakToTheNextRhinoRequest"],
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--expected-target", type=int, required=True)
    parser.add_argument("--default-ct", choices=("true", "false"), required=True)
    parser.add_argument("--ech-mode", type=int, choices=(0, 1, 2, 3), required=True,
                        help="API 37.0: UNKNOWN=0, DISABLED=1, OPPORTUNISTIC=2, ENABLED=3; ignored before API 37")
    parser.add_argument("--suite", choices=("all", *SUITES), default="all")
    parser.add_argument("--wait-for-debugger", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("The loopback fixture is for a disposable emulator")
    device = Device(args.adb, args.serial)
    device.require_online()
    args.output.mkdir(parents=True, exist_ok=False)
    package = "org.autojs.autojs6"
    snapshot(device, [package], args.output / "before")
    package_state = (args.output / "before" / (package + "-package.txt")).read_text(encoding="utf-8")
    target = re.search(r"\btargetSdk=(\d+)\b", package_state)
    if not target or int(target[1]) != args.expected_target or "DEBUGGABLE" not in package_state:
        raise RuntimeError("Expected a debuggable APK with the selected targetSdk")
    identity = json.loads((args.output / "before" / "device.json").read_text(encoding="utf-8"))
    if int(identity["sdk"]) >= 37 and args.expected_target >= 37:
        permission = device.shell("pm", "check-permission", "android.permission.ACCESS_LOCAL_NETWORK", package)
        # pm check-permission is absent on some images; the package dump is the fallback.
        if "granted" not in permission["stdout"] and "android.permission.ACCESS_LOCAL_NETWORK: granted=true" not in package_state:
            raise RuntimeError("Grant the test APK local-network permission before using the emulator host alias")
    names = sum(SUITES.values(), []) if args.suite == "all" else SUITES[args.suite]
    expected = [TEST_CLASS + "#" + name for name in names]
    command = ["am", "instrument", "-w", "-r", "-e", "class", ",".join(expected),
               "-e", "sdk37.tlsHost", "10.0.2.2", "-e", "sdk37.defaultCt", args.default_ct,
               "-e", "sdk37.echMode", str(args.ech_mode)]
    if args.wait_for_debugger:
        command += ["-e", "debug", "true"]
    command += [package + ".test/androidx.test.runner.AndroidJUnitRunner"]
    started = utc_now()
    result = device.shell(*command, timeout=900 if args.wait_for_debugger else 300)
    log = result["stdout"] + result["stderr"]
    (args.output / "instrumentation.txt").write_text(log, encoding="utf-8")
    verdict = parse_instrumentation(log, expected, result["exitCode"])
    write_json(args.output / "result.json", {"started": started, "ended": utc_now(), "target": args.expected_target,
                                            "defaultCt": args.default_ct, "echMode": args.ech_mode,
                                            "exitCode": result["exitCode"], "timeout": result.get("timeout", False),
                                            "waitForDebugger": args.wait_for_debugger, "command": result["command"], **verdict})
    snapshot(device, [package], args.output / "after")
    logs = device.call("logcat", "-d", "-t", "2500", "-v", "threadtime", "SDK37-TLS:I", "AndroidRuntime:E", "*:S")
    (args.output / "logcat-tail.txt").write_text(logs["stdout"] + logs["stderr"], encoding="utf-8")
    print(json.dumps(verdict))
    return 0 if verdict["success"] else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, OSError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(2)
