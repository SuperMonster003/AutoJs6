"""Package the three SDK 37 example projects through the installed APK Builder plugin, then install and drive them.

Roadmap P3.4 (the packaging chain on a target 37 Runtime Kit) and P3.3 rows 30-37 (packaged-app
behaviour after the 29 -> 37 jump). The selected device needs the target 37 debug host with its
test APK (Sdk37PackagedExamplesProducerDeviceTest) and an APK Builder Template plugin built from
a target 37 Runtime Kit. The runner records the plugin's Runtime Kit identity, runs the opt-in
producer, pulls the three signed user APKs, checks their badging, installs each one, launches it
and reads the JSON receipt its script writes. The a11y example needs its own accessibility
service, which the runner enables and restores afterwards. Nothing else on the device changes;
the example apps are uninstalled at the end unless --keep-installed. Output: examples.json and
examples.md; exit status 1 when any step fails.
"""
import argparse
import json
from pathlib import Path
import re
import sys
import time
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from physical_device import Device, utc_now, write_json  # noqa: E402
from run_gate import HOST, RUNNER_SUFFIX, a11y_services, describe_apk, find_aapt2, set_a11y_services  # noqa: E402

PRODUCER_CLASS = "org.autojs.autojs.apkbuilder.template.Sdk37PackagedExamplesProducerDeviceTest"
PRODUCER_ARGUMENT = "autojs.sdk37.produceExamples"
PLUGIN = "org.autojs.plugin.apkbuilder.template"
PACKAGE_PREFIX = "org.autojs.autojs6.sdk37"
A11Y_CLASS = "org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
EXAMPLES = ("a11y", "ui", "timer")
RECEIPT = "sdk37-example.json"
TIMED_RECEIPT = "sdk37-timed.json"
# Script-side checks every example must report as passed (common.js) plus the example's own.
EXPECTED_CHECKS = {
    "common": ("target-37", "visibility-settings-app", "local-network-declared", "post-notifications-runtime",
               "exact-alarm-capability", "load-dex", "broadcast-roundtrip"),
    "a11y": ("a11y-service-bound", "a11y-own-window-front", "a11y-root-window", "a11y-window-event-after-transition"),
    "ui": ("ui-layout-rendered", "ui-edge-to-edge"),
    "timer": ("countdown-task-scheduled", "tts-done"),
}


def package_of(example):
    return PACKAGE_PREFIX + "." + example


def external_files(package):
    return "/sdcard/Android/data/" + package + "/files"


def runtime_kit_of_plugin(apk_path):
    """The Runtime Kit metadata the plugin was built from, read out of its assets."""
    with zipfile.ZipFile(apk_path) as archive:
        kit = json.loads(archive.read("assets/runtime-kit/runtime-kit.json"))
        contract = json.loads(archive.read("assets/runtime-kit/build-contract.json")) if "assets/runtime-kit/build-contract.json" in archive.namelist() else None
    return {"runtimeKitId": kit.get("runtimeKitId"), "host": kit.get("host"), "template": kit.get("template"),
            "compatibility": kit.get("compatibility"), "contract": contract}


def evaluate(example, receipt, runner_checks, crash_lines):
    """Combine the script receipt with the runner's own observations into one verdict."""
    expected = EXPECTED_CHECKS["common"] + EXPECTED_CHECKS[example]
    reported = {row["name"]: row for row in (receipt or {}).get("checks", [])}
    failures = []
    if not receipt:
        failures.append("no receipt")
    else:
        if not receipt.get("done"):
            failures.append("receipt not done")
        if receipt.get("package") != package_of(example):
            failures.append("receipt package " + str(receipt.get("package")))
        if receipt.get("target") != 37:
            failures.append("receipt target " + str(receipt.get("target")))
        for name in expected:
            row = reported.get(name)
            if row is None:
                failures.append(name + ": missing")
            elif not row.get("passed"):
                failures.append(name + ": " + (row.get("detail") or "failed"))
    for name, passed, detail in runner_checks:
        if not passed:
            failures.append(name + ": " + detail)
    if crash_lines:
        failures.append("AndroidRuntime crash logged")
    return {"success": not failures, "failures": failures, "scriptChecks": len(reported), "expectedChecks": len(expected)}


def markdown(report):
    device = report["device"]
    lines = ["# SDK 37 packaged examples: " + str(device.get("model")) + " (" + device["serial"] + ")", "",
             f"- Started {report['started']}, finished {report['finished']}",
             f"- API {device.get('sdk')}, fingerprint `{device.get('fingerprint')}`; overall **{report['overall']}**"]
    kit = report.get("runtimeKit") or {}
    if kit:
        template = kit.get("template") or {}
        lines.append(f"- Plugin {PLUGIN} {report.get('pluginVersion')} built from Runtime Kit `{kit.get('runtimeKitId')}` "
                     f"(template `{str(template.get('sha256'))[:16]}...`, host {(kit.get('host') or {}).get('versionCode')})")
    lines += ["", "| Step | Result | Detail |", "| --- | --- | --- |"]
    for step in report["steps"]:
        lines.append(f"| {step['name']} | {step['status']} | {step['detail']} |")
    for example, data in report["examples"].items():
        lines += ["", f"## {example} (`{package_of(example)}`)", ""]
        apk = data.get("apk")
        if apk:
            lines.append(f"- APK compile / target / min {apk['compileSdk']} / {apk['targetSdk']} / {apk['minSdk']}, "
                         f"LAN permission {apk['localNetworkPermission']}, SHA-256 `{apk['sha256'][:16]}...`")
        verdict = data.get("verdict") or {}
        lines.append(f"- Verdict: **{'PASS' if verdict.get('success') else 'FAIL'}**" +
                     ("" if verdict.get("success") else " (" + "; ".join(verdict.get("failures", [])) + ")"))
        receipt = data.get("receipt") or {}
        if receipt.get("checks"):
            lines += ["", "| Script check | Passed | Detail |", "| --- | --- | --- |"]
            for row in receipt["checks"]:
                lines.append(f"| {row['name']} | {row['passed']} | {row.get('detail', '')} |")
        if data.get("runnerChecks"):
            lines += ["", "| Runner check | Passed | Detail |", "| --- | --- | --- |"]
            for name, passed, detail in data["runnerChecks"]:
                lines.append(f"| {name} | {passed} | {detail} |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True, help="New directory for APKs, receipts and the report")
    parser.add_argument("--sdk-root", type=Path, help="Android SDK root for aapt2 (defaults to ANDROID_HOME / ANDROID_SDK_ROOT)")
    parser.add_argument("--examples", nargs="+", choices=EXAMPLES, default=list(EXAMPLES))
    parser.add_argument("--skip-produce", action="store_true", help="Reuse sdk37-example-*.apk already present in --output")
    parser.add_argument("--keep-installed", action="store_true", help="Leave the example apps installed for inspection")
    parser.add_argument("--timeout", type=int, default=150, help="Seconds to wait for one example's receipt")
    args = parser.parse_args()

    device = Device(args.adb, args.serial)
    device.require_online()
    args.output.mkdir(parents=True, exist_ok=args.skip_produce)
    aapt2 = find_aapt2(args.sdk_root)
    report = {"started": utc_now(), "serial": args.serial, "device": device.identity(), "steps": [], "examples": {}}
    original_services = a11y_services(device)

    def step(name, status, detail=""):
        report["steps"].append({"name": name, "status": status, "detail": detail})
        print(f"[{status}] {name}: {detail}", flush=True)

    def read_json(path):
        result = device.shell("cat", path)
        if result["exitCode"] != 0:
            return None
        try:
            return json.loads(result["stdout"])
        except ValueError:
            return None

    def wait_for(predicate, timeout, interval=1.0):
        deadline = time.monotonic() + timeout
        while True:
            value = predicate()
            if value:
                return value
            if time.monotonic() >= deadline:
                return None
            time.sleep(interval)

    try:
        # 1. Host, test runner and plugin.
        host_dump = device.shell("dumpsys", "package", HOST)["stdout"]
        host_target = re.search(r"\btargetSdk=(\d+)\b", host_dump)
        host_code = re.search(r"\bversionCode=(\d+)\b", host_dump)
        if not host_target or host_target[1] != "37":
            step("host", "FAIL", "installed host targetSdk " + (host_target[1] if host_target else "?"))
            raise SystemExit(1)
        test_present = device.shell("pm", "path", HOST + ".test")["stdout"].strip()
        plugin_path = device.shell("pm", "path", PLUGIN)["stdout"].strip().removeprefix("package:")
        if not test_present or not plugin_path:
            step("host", "FAIL", "test APK present " + str(bool(test_present)) + ", plugin present " + str(bool(plugin_path)))
            raise SystemExit(1)
        plugin_dump = device.shell("dumpsys", "package", PLUGIN)["stdout"]
        version = re.search(r"versionName=(\S+)", plugin_dump)
        report["pluginVersion"] = version[1] if version else "?"
        local_plugin = args.output / "plugin.apk"
        device.call("pull", plugin_path, str(local_plugin), timeout=300)
        report["runtimeKit"] = runtime_kit_of_plugin(local_plugin)
        kit_host = (report["runtimeKit"].get("host") or {}).get("versionCode")
        compatible = host_code and kit_host == int(host_code[1])
        step("host", "PASS" if compatible else "FAIL",
             f"host targetSdk 37 versionCode {host_code[1] if host_code else '?'}; plugin {report['pluginVersion']} kit "
             f"{report['runtimeKit'].get('runtimeKitId')} for host {kit_host}")
        if not compatible:
            raise SystemExit(1)

        # 2. Produce the APKs through the real builder.
        apks = {}
        if args.skip_produce:
            step("produce", "SKIP", "reusing APKs in " + str(args.output))
        else:
            result = device.shell("am", "instrument", "-w", "-r", "-e", "class", PRODUCER_CLASS, "-e", PRODUCER_ARGUMENT, "true",
                                  HOST + RUNNER_SUFFIX, timeout=900)
            (args.output / "producer.log").write_text(result["stdout"] + result["stderr"], encoding="utf-8")
            ok = "OK (1 test)" in result["stdout"] and "INSTRUMENTATION_STATUS_CODE: -3" not in result["stdout"]
            step("produce", "PASS" if ok else "FAIL", "producer instrumentation " + ("passed" if ok else "failed, see producer.log"))
            if not ok:
                raise SystemExit(1)
        for example in args.examples:
            local = args.output / f"sdk37-example-{example}.apk"
            if not args.skip_produce:
                pulled = device.call("pull", external_files(HOST) + f"/sdk37-example-{example}.apk", str(local), timeout=300)
                if pulled["exitCode"] != 0:
                    step("pull " + example, "FAIL", pulled["stderr"].strip()[-200:])
                    raise SystemExit(1)
            apks[example] = describe_apk(aapt2, local)

        # 3. Install, launch and read back each example.
        for example in args.examples:
            package = package_of(example)
            apk = apks[example]
            data = report["examples"][example] = {"apk": apk, "runnerChecks": []}
            checks = data["runnerChecks"]
            checks.append(("badging", apk["package"] == package and apk["targetSdk"] == 37 and apk["localNetworkPermission"],
                           f"package {apk['package']}, target {apk['targetSdk']}, LAN permission {apk['localNetworkPermission']}"))
            device.shell("pm", "uninstall", package, timeout=120)
            installed = device.call("install", "-r", str(apk["path"]), timeout=600)
            install_ok = "Success" in installed["stdout"] + installed["stderr"]
            checks.append(("install", install_ok, (installed["stdout"] + installed["stderr"]).strip()[-200:]))
            if not install_ok:
                data["verdict"] = evaluate(example, None, checks, [])
                step("example " + example, "FAIL", "install failed")
                continue
            if example == "a11y":
                # Right after a fresh install the system may not have registered the service yet and then ignores
                # the setting; rewriting the list after a pause makes it re-read (observed once on API 37.1).
                device.shell("settings", "put", "secure", "accessibility_enabled", "1")
                bound = None
                for attempt in range(1, 4):
                    set_a11y_services(device, original_services + [package + "/" + A11Y_CLASS])
                    bound = wait_for(lambda: re.search(r"Bound services:\{[^\n]*label=SDK37 a11y\b", device.shell("dumpsys", "accessibility")["stdout"]), 10)
                    if bound:
                        break
                    set_a11y_services(device, original_services)
                    time.sleep(3)
                checks.append(("a11y-service-enabled-by-runner", bool(bound), f"bound on attempt {attempt}" if bound else "not bound after 3 attempts"))
            resolved = device.shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
                                    "-c", "android.intent.category.LAUNCHER", package)["stdout"]
            component = next((line.strip() for line in resolved.splitlines() if line.strip().startswith(package + "/")), None)
            checks.append(("launcher-activity", bool(component), component or resolved.strip()[:200]))
            if not component:
                data["verdict"] = evaluate(example, None, checks, [])
                step("example " + example, "FAIL", "no launcher activity")
                continue
            device.shell("rm", "-f", external_files(package) + "/" + RECEIPT, external_files(package) + "/" + TIMED_RECEIPT)
            since = device.shell("date", "+%m-%d %H:%M:%S.000")["stdout"].strip()
            started = device.shell("am", "start", "-W", "-n", component)
            checks.append(("launch", "Error" not in started["stdout"], started["stdout"].strip()[-200:]))
            receipt_path = external_files(package) + "/" + RECEIPT
            receipt = wait_for(lambda: (lambda r: r if r and r.get("done") else None)(read_json(receipt_path)), args.timeout)
            if example == "ui" and receipt:
                # Predictive back (row 36): BACK must reach the script's callback and then finish the activity.
                device.shell("input", "keyevent", "4")
                after_back = wait_for(lambda: (lambda r: r if r and r.get("backPressed") and r.get("exited") else None)(read_json(receipt_path)), 15)
                gone = wait_for(lambda: package + "/" not in device.shell("dumpsys", "activity", "activities")["stdout"], 15)
                checks.append(("ui-predictive-back-finishes-activity", bool(after_back) and bool(gone),
                               f"backPressed {bool(after_back and after_back.get('backPressed'))}, exited {bool(after_back and after_back.get('exited'))}, activity gone {bool(gone)}"))
                receipt = after_back or receipt
            if example == "timer" and receipt:
                scheduled = (receipt.get("timedTask") or {}).get("countdownMillis", 20000) / 1000
                timed = wait_for(lambda: read_json(external_files(package) + "/" + TIMED_RECEIPT), scheduled + 45)
                checks.append(("timed-task-fired", bool(timed and timed.get("fired") and timed.get("target") == 37),
                               json.dumps(timed) if timed else f"no timed receipt within {scheduled + 45:.0f} s"))
                data["timedReceipt"] = timed
            data["receipt"] = receipt
            crash = device.call("logcat", "-d", "-t", since, "-s", "AndroidRuntime:E")["stdout"] if since else ""
            crash_lines = [line for line in crash.splitlines() if package in line]
            data["crashLines"] = crash_lines[:6]
            data["verdict"] = evaluate(example, receipt, checks, crash_lines)
            step("example " + example, "PASS" if data["verdict"]["success"] else "FAIL",
                 f"{data['verdict']['scriptChecks']} script checks" + ("" if data["verdict"]["success"] else "; " + "; ".join(data["verdict"]["failures"])))
            device.shell("am", "force-stop", package)
            if example == "a11y":
                set_a11y_services(device, original_services)
    finally:
        set_a11y_services(device, original_services)
        if not args.keep_installed:
            for example in args.examples:
                device.shell("pm", "uninstall", package_of(example), timeout=120)
        report["finished"] = utc_now()
        report["overall"] = "PASS" if report["steps"] and all(s["status"] in ("PASS", "SKIP") for s in report["steps"]) else "FAIL"
        write_json(args.output / "examples.json", report)
        (args.output / "examples.md").write_text(markdown(report), encoding="utf-8")
        print("Overall " + report["overall"] + ": " + str(args.output / "examples.md"), flush=True)
    raise SystemExit(0 if report["overall"] == "PASS" else 1)


if __name__ == "__main__":
    main()
