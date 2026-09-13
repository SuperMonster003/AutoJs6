"""Run one BAL probe on a disposable emulator or an explicitly selected lab phone; never changes compat flags."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--physical-device", action="store_true",
                        help="Use the selected lab phone; the probe and fixture are force-stopped, interrupting their work")
    parser.add_argument("--package", choices=("org.autojs.autojs6", "org.autojs.autojs6.inrt"), default="org.autojs.autojs6")
    parser.add_argument("--route", choices=("result", "sender", "sender-legacy", "pending", "broadcast", "script-launch", "script-start", "asm", "install"), required=True)
    parser.add_argument("--sender-mode", choices=("none", "visible", "denied"), default="visible")
    parser.add_argument("--background", action="store_true")
    parser.add_argument("--allow-below", choices=("true", "false"))
    parser.add_argument("--expect", choices=("activity", "result", "broadcast", "blocked", "landing", "no-landing", "install"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=120, help="Probe completion deadline in seconds (30-300)")
    parser.add_argument("--reuse-process", action="store_true", help="Remove only probe tasks and retain a warm application process")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial) and not args.physical_device:
        parser.error("Use a disposable emulator or explicitly pass --physical-device for the selected lab phone")
    if not 30 <= args.timeout <= 300:
        parser.error("--timeout must be between 30 and 300 seconds")
    if args.route == "sender-legacy" and args.sender_mode != "none":
        parser.error("sender-legacy has no options argument; pass --sender-mode none")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    case = f"bal_{time.time_ns()}"
    prefix = [args.adb, "-s", args.serial]

    def adb(*command, check=True):
        try:
            completed = subprocess.run(prefix + list(command), capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30)
        except subprocess.TimeoutExpired as error:
            if check:
                raise RuntimeError(f"adb timed out: {command}") from error
            partial = error.stdout or b""
            return (partial.decode("utf-8", errors="replace") if isinstance(partial, bytes) else partial) + "\n[adb snapshot timed out]\n"
        if check and completed.returncode:
            raise RuntimeError(f"adb {command}: {completed.stdout}\n{completed.stderr}")
        return completed.stdout + completed.stderr

    package = adb("shell", "dumpsys", "package", args.package)
    (output / "package-before.txt").write_text(package, encoding="utf-8")
    if not re.search(r"(?:pkgFlags|flags)=\[[^\]]*\bDEBUGGABLE\b", package):
        raise RuntimeError("The installed probe package must be debuggable")
    (output / "compat.txt").write_text(adb("shell", "dumpsys", "platform_compat"), encoding="utf-8")
    (output / "device.txt").write_text(adb("shell", "getprop", "ro.build.fingerprint") + adb("shell", "getconf", "PAGE_SIZE"), encoding="utf-8")
    (output / "appops.txt").write_text(adb("shell", "appops", "get", args.package), encoding="utf-8")
    if args.route == "asm":
        (output / "fixture-package-before.txt").write_text(
            adb("shell", "dumpsys", "package", "org.autojs.sdk37.balfixture"), encoding="utf-8")
    removed_tasks = []
    if args.reuse_process:
        stacks = adb("shell", "am", "stack", "list")
        (output / "tasks-before-cleanup.txt").write_text(stacks, encoding="utf-8")
        for block in re.split(r"(?=^RootTask id=)", stacks, flags=re.MULTILINE):
            root = re.match(r"RootTask id=(\d+)", block)
            tasks = re.findall(r"taskId=\d+: (\S+)", block)
            if root and tasks and all(name.startswith((args.package + "/", "org.autojs.sdk37.balfixture/")) for name in tasks):
                adb("shell", "am", "stack", "remove", root.group(1))
                removed_tasks.append(int(root.group(1)))
    # A previous direct send or ASM case may leave a different UID's Activity visible.
    # Stop the disposable fixture too, so background tests cannot reveal that old Activity.
    adb("shell", "am", "force-stop", "org.autojs.sdk37.balfixture")
    if not args.reuse_process:
        adb("shell", "am", "force-stop", args.package)
    launch = ["shell", "am", "start", "-n", f"{args.package}/org.autojs.autojs.compat.ActivityLaunchCompatibilityProbeActivity",
              "-f", "0x18000000", "--es", "case", case, "--es", "route", args.route,
              "--es", "senderMode", args.sender_mode, "--ez", "background", str(args.background).lower()]
    if args.allow_below is not None:
        launch += ["--ez", "allowBelow", args.allow_below]
    record = {"case": case, "command": prefix + launch, "expected": args.expect, "fixtureForceStopped": True,
              "reuseProcess": args.reuse_process, "removedRootTasks": removed_tasks}
    logs = (output / "logcat.txt").open("w", encoding="utf-8")
    logcat = subprocess.Popen(prefix + ["logcat", "-T", "1", "-v", "threadtime", "ActivityTaskManager:V", "StrictMode:V", "SDK37-BAL-Fixture:V", "AndroidRuntime:E", "PackageInstallStatus:V", "*:S"], stdout=logs, stderr=subprocess.STDOUT)
    try:
        (output / "launch.txt").write_text(adb(*launch), encoding="utf-8")
        deadline = time.monotonic() + args.timeout
        result = None
        while time.monotonic() < deadline:
            raw = adb("shell", "run-as", args.package, "cat", f"files/sdk37-bal-{case}.json", check=False)
            try:
                result = json.loads(raw)
            except json.JSONDecodeError:
                time.sleep(1)
                continue
            if args.route == "asm" and result.get("probeStoppedAt") and not result.get("belowAttempted") \
                    and "taskBeforeBelow" not in record:
                activities = adb("shell", "dumpsys", "activity", "activities")
                (output / "activities-before-below.txt").write_text(activities, encoding="utf-8")
                tops = re.findall(r"(?:topResumedActivity|mResumedActivity)\s*[:=]\s*ActivityRecord\{[^\n]*?\s"
                                  r"org\.autojs\.sdk37\.balfixture/\.TargetActivity\s+t(\d+)\b", activities)
                record["taskBeforeBelow"] = [int(task) for task in tops]
            if result.get("completed"):
                break
            time.sleep(1)
        record["result"] = result
        if not result or not result.get("completed"):
            raise RuntimeError(f"Probe did not complete: {result}")
        if result.get("case") != case or result.get("route") != args.route:
            raise RuntimeError("Probe result identity mismatch")
        record["result"] = result
        event = result.get("fixtureEvent", {})
        fresh = event.get("time", 0) >= result["startedAt"]
        activity = fresh and event.get("kind") == "activity-created"
        checks = {
            "activity": activity,
            "result": activity and result.get("resultCode") == -1 and result.get("resultCase") == case,
            "broadcast": fresh and event.get("kind") == "broadcast-received",
            "blocked": not fresh and (result.get("sendReturned") or result.get("scriptCompleted")),
            "landing": result.get("belowAttempted") and result.get("landing", {}).get("case") == case,
            "no-landing": activity and result.get("belowAttempted") and not result.get("landing"),
            "install": isinstance(result.get("installSession"), int),
        }
        # An exception or failed fixture read is not proof that BAL blocked an Activity.
        errors = [k for k in ("error", "scriptError", "belowError", "installError", "fixtureReadError") if k in result]
        if args.route == "asm":
            record["asmScenarioValid"] = bool(activity and event.get("uid") != result["uid"]
                and event.get("taskId") == result["taskId"]
                and result["taskId"] in record.get("taskBeforeBelow", [])
                and result.get("belowAttemptedAt", 0) - result.get("probeStoppedAt", 0) >= 10_000)
            platform_log = (output / "logcat.txt").read_text(encoding="utf-8", errors="replace")
            # The fixture event has this run's unique case ID. Use it as the boundary
            # even if logcat attached after the initial shell Activity launch.
            events = list(re.finditer(r"^.*SDK37-BAL-Fixture: .*\"case\":\"" + re.escape(case)
                                     + r"\".*$", platform_log, re.MULTILINE))
            current_log = platform_log[events[-1].end():] if events else ""
            record["asmBlockEvidence"] = [line for line in current_log.splitlines()
                if "[ASM] Abort Launching" in line and args.package in line
                and "ActivityLaunchProbeLandingActivity" in line
                and re.search(r"\bt" + str(result["taskId"]) + r"\b", line)]
            checks["landing"] = checks["landing"] and record["asmScenarioValid"] \
                and result.get("landing", {}).get("taskId") == result["taskId"] \
                and not record["asmBlockEvidence"]
            checks["no-landing"] = checks["no-landing"] and record["asmScenarioValid"] \
                and bool(record["asmBlockEvidence"])
        if args.expect == "blocked":
            # -T 1 can include the final line from a preceding case. Only accept a
            # diagnostic after this probe's launch, attributed to its actual PID.
            platform_log = (output / "logcat.txt").read_text(encoding="utf-8", errors="replace")
            launches = list(re.finditer(r"^.*START u\d+ .*cmp=" + re.escape(args.package)
                + r"/org\.autojs\.autojs\.compat\.ActivityLaunchCompatibilityProbeActivity.*$", platform_log, re.MULTILINE))
            current_log = platform_log[launches[-1].end():] if launches else ""
            pid_pattern = re.compile(r"(?:realCallingPid|callingPid): " + str(result["pid"]) + r"[;\]]")
            record["platformBlockEvidence"] = [line for line in current_log.splitlines()
                if "Background activity launch blocked!" in line and pid_pattern.search(line)]
            checks["blocked"] = checks["blocked"] and ("BackgroundActivityLaunchViolation" in result.get("strictMode", "")
                or bool(record["platformBlockEvidence"]))
        passed = bool(checks[args.expect]) and not errors
        record.update(assertionPassed=passed, errors=errors)
        print(json.dumps(record, ensure_ascii=False))
        if not passed:
            raise AssertionError("Probe expectation failed; inspect the saved platform evidence")
    except Exception as error:
        record["failure"] = repr(error)
        raise
    finally:
        logcat.terminate()
        try:
            logcat.wait(timeout=5)
        except subprocess.TimeoutExpired:
            logcat.kill()
            logcat.wait(timeout=5)
        logs.close()
        (output / "result.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
        for name, command in (("activities", ("dumpsys", "activity", "activities")),
                              ("processes", ("dumpsys", "activity", "processes"))):
            (output / f"{name}.txt").write_text(adb("shell", *command, check=False), encoding="utf-8")


if __name__ == "__main__":
    main()
