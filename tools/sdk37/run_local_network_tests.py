"""Run local-network instrumentation with an explicitly selected debug app and permission state.

Does not install APKs, grant/revoke permissions, launch fixtures or modify adb routing.
The permission UI suites deliberately operate dialogs and are limited to emulators.
See README.md for fixture setup and the target 36/37 installation matrix.
"""
import argparse
import datetime
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess


TRANSPORTS = [
    "runtimePermissionNameUsesTheExistingApiAndSkipsLegacyTargets",
    "rawTcpAndUdpFollowTheSystemPermission",
    "inboundAcceptIsProtectedEvenWhenBindSucceeds",
    "ipv4AndIpv6LoopbackRemainUsableWithoutLocalNetworkPermission",
    "scriptHttpSyncAndAsyncFailuresKeepTheCauseAndExplainRecovery",
    "webSocketUpgradeUsesTheSamePermissionDiagnostics",
    "rawPahoMqttFollowsTheSystemPermissionWithoutAnAutomaticPrompt",
    "devServerReportsMissingPermissionBeforeAdvertisingAListener",
    "devServerAcceptsTheVsCodeHandshakeAfterGrant",
    "devClientCompletesTheVsCodeHandshakeAfterGrant",
    "nodeControlledFetchPreservesItsErrorCodeAndAddsThePermissionHint",
]
UI_SUITES = {
    "retry": ["permissionRequestCanBeDeniedRetriedAndGranted"],
    "denial": ["repeatedDenialOffersSettingsWithoutOpeningItAutomatically"],
    "nearby": ["existingNearbyDevicesGrantAvoidsAnotherSystemPrompt"],
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="org.autojs.autojs6", choices=["org.autojs.autojs6", "org.autojs.autojs6.inrt"])
    parser.add_argument("--expect", required=True, choices=["granted", "denied"])
    parser.add_argument("--suite", default="transports", choices=["transports", *UI_SUITES])
    parser.add_argument("--host", default="10.0.2.2", type=ipaddress.IPv4Address,
                        help="Fixture IPv4 address; physical devices require the PC's actual LAN address")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.suite in UI_SUITES and not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Permission UI suites require a disposable emulator.")
    if not re.fullmatch(r"emulator-\d+", args.serial) and str(args.host) == "10.0.2.2":
        parser.error("Physical transports require --host PC_LAN_IP and the fixture's --bind / --device mode")
    args.output.mkdir(parents=True, exist_ok=False)
    base = [args.adb, "-s", args.serial]

    def adb(*command, timeout=30):
        result = subprocess.run(base + list(command), capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
        if result.returncode:
            raise RuntimeError(result.stderr or result.stdout)
        return result.stdout

    if adb("get-state").strip() != "device":
        raise RuntimeError("Selected device is not online")
    package = adb("shell", "dumpsys", "package", args.package)
    if not re.search(r"(?:pkgFlags|flags)=\[[^\]]*DEBUGGABLE", package):
        raise RuntimeError("Install the matching debuggable target and instrumentation APKs first")
    methods = TRANSPORTS if args.suite == "transports" else UI_SUITES[args.suite]
    classes = ",".join("org.autojs.autojs.permission.LocalNetworkPermissionDeviceTest#" + method for method in methods)
    command = ["shell", "am", "instrument", "-w", "-r", "-e", "class", classes,
               "-e", "sdk37.lanAllowed", str(args.expect == "granted").lower(),
               "-e", "sdk37.lanHost", str(args.host),
               args.package + ".test/androidx.test.runner.AndroidJUnitRunner"]
    (args.output / "package-before.txt").write_text(package, encoding="utf-8")
    try:
        page_size = adb("shell", "getconf", "PAGE_SIZE").strip()
    except RuntimeError:
        # Android 7 images do not necessarily include getconf.
        smaps = adb("shell", "cat", "/proc/self/smaps")
        page_match = re.search(r"KernelPageSize:\s+(\d+) kB", smaps)
        page_size = str(int(page_match[1]) * 1024) if page_match else "unavailable"
    metadata = {
        "time": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "serial": args.serial, "package": args.package, "suite": args.suite,
        "expectedAccess": args.expect, "host": str(args.host),
        "fingerprint": adb("shell", "getprop", "ro.build.fingerprint").strip(),
        "sdk": adb("shell", "getprop", "ro.build.version.sdk").strip(),
        "pageSize": page_size,
        "command": base + command,
    }
    (args.output / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    result = adb(*command, timeout=180)
    (args.output / "instrumentation.txt").write_text(result, encoding="utf-8")
    (args.output / "package-after.txt").write_text(adb("shell", "dumpsys", "package", args.package), encoding="utf-8")
    (args.output / "logcat.txt").write_text(adb("logcat", "-d", "-v", "threadtime", "-s", "Sdk37LocalNetwork"), encoding="utf-8")
    skipped = sum(result.count(f"INSTRUMENTATION_STATUS_CODE: {code}") for code in (-3, -4))
    passed = result.count("INSTRUMENTATION_STATUS_CODE: 0")
    expected_skips = 2 if args.suite == "transports" and args.expect == "denied" else 0
    success = (f"OK ({len(methods)} test" in result and "FAILURES!!!" not in result
               and skipped == expected_skips and passed == len(methods) - expected_skips)
    metadata.update(passed=passed, skipped=skipped, success=success)
    (args.output / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(f"{args.suite}: passed={passed}, skipped={skipped}, success={success}; evidence={args.output}")
    if not success:
        print(result[-4000:])
        raise SystemExit(1)


if __name__ == "__main__":
    main()
