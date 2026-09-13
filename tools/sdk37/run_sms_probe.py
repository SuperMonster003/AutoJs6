"""Observe SMS delivery on an exclusively owned API 37 emulator.

Injects three fictional incoming SMS messages with adb emu, interrupts the host,
and saves app-UID query / broadcast / notification receipts. Requires preinstalled
debuggable AutoJs6 with SMS and notification-listener access. Does not install,
grant access, change roles / compat / clocks, or delete SMS. Dispose of the AVD
overlay afterwards. A successful observation is not a three-hour expiry test.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import uuid

from physical_device import Device, snapshot, utc_now, write_json
from run_framework_probe import checked, close_host_tasks

HOST = "org.autojs.autojs6"
CHANGE = 437043173


def otp_compat(dump, target):
    lines = [line.strip() for line in dump.splitlines() if "name=FILTER_GENERIC_OTP;" in line]
    if len(lines) != 1 or not re.match(rf"ChangeId\({CHANGE}; name=FILTER_GENERIC_OTP; enableSinceTargetSdk=37(?:;|\))", lines[0]):
        raise ValueError("Unknown FILTER_GENERIC_OTP definition")
    overrides = re.search(r"; packageOverrides=\{([^}]*)\}", lines[0])
    pairs = dict(re.findall(r"([\w.]+)=(true|false)(?:,|$)", overrides[1])) if overrides else {}
    return {"enabled": pairs[HOST] == "true" if HOST in pairs else target >= 37,
            "source": "packageOverrides" if HOST in pairs else "targetSdk", "definition": lines[0]}


def read_receipt(device, case_id):
    result = device.shell("run-as", HOST, "cat", "files/sdk37-sms-" + case_id + ".json")
    if result["exitCode"] != 0:
        return {}
    try:
        return json.loads(result["stdout"])
    except json.JSONDecodeError:
        return {}  # The UI thread may be writing the next snapshot.


def provider_rows(output):
    pattern = r"(?:^|\n)Row: \d+ _id=(\d+), address=(\d+), body=(.*?), date=(\d+), contains_otp=(\d+)(?=\nRow: |\s*$)"
    return [{"id": row[0], "address": row[1], "body": row[2], "date": int(row[3]), "containsOtp": int(row[4])}
            for row in re.findall(pattern, output.strip(), re.DOTALL)]


def validate_observation(receipt, case_id, target, messages, stored, enabled):
    errors = []
    if not isinstance(receipt, dict):
        return ["Missing receipt"]
    if (receipt.get("caseId") != case_id or receipt.get("target") != target or receipt.get("sdk") != 37
            or receipt.get("ready") is not True or receipt.get("complete") is not True):
        errors.append("Receipt identity, target or completion mismatch")
    if receipt.get("errors"):
        errors.append("Script reported errors")
    plain = messages["plain"]
    if not any(row.get("body") == plain for query in receipt.get("queries", []) for row in query.get("rows", [])):
        errors.append("Plain SMS query positive control missing")
    if not any(row.get("body") == plain for row in receipt.get("broadcasts", [])):
        errors.append("Plain SMS broadcast positive control missing")
    if not any(plain in (row.get("text") or "") for row in receipt.get("notifications", [])):
        errors.append("Plain SMS notification positive control missing")
    expected_classification = {messages["plain"]: 0, messages["generic"]: 1, messages["webotp"]: 513}
    if len(stored) != 3 or {row["body"]: row["containsOtp"] for row in stored} != expected_classification:
        errors.append("Missing SMS or unexpected platform OTP classification; cannot infer filtering")
    expected = {plain} if enabled else {plain, messages["generic"]}
    queries = receipt.get("queries", [])
    if not queries or {row["body"] for row in queries[-1]["rows"]} != expected:
        errors.append("Final app-UID query visibility differs from the active OTP gate")
    if {row["body"] for row in receipt.get("broadcasts", [])} != expected:
        errors.append("SMS broadcast visibility differs from the active OTP gate")
    notifications = receipt.get("notifications", [])
    if sum(row.get("text") == "Sensitive notification content hidden" for row in notifications) < 2:
        errors.append("OTP notification redaction evidence missing (English AVD required)")
    if any(code in (row.get(field) or "") for row in notifications
           for field in ("title", "text", "extraText", "bigText") for code in ("482913", "736251")):
        errors.append("Listener received an unredacted fictional OTP")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--expected-target", type=int, choices=(36, 37), required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--observation-seconds", type=int, default=45, choices=range(30, 181), metavar="30..180")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("adb emu requires an explicit emulator console serial")
    args.output.mkdir(parents=True, exist_ok=False)
    case_id = uuid.uuid4().hex
    sender = "1555" + str(int(case_id[:10], 16)).zfill(13)[-10:]
    messages = {"plain": "SDK37 lunch is ready.",
                "generic": "Your verification code is 482913. Do not share this code.",
                "webotp": "Your verification code is 736251.\n\n@example.com #736251"}
    device = Device(args.adb, args.serial)
    result = {"caseId": case_id, "started": utc_now(), "target": args.expected_target,
              "sender": sender, "messages": messages, "success": False,
              "scope": "Immediate observation only; no clock changes or three-hour wait"}
    launched = False
    try:
        device.require_online()
        result["device"] = device.identity()
        if not result["device"]["emulator"] or int(result["device"]["sdk"]) != 37:
            raise RuntimeError("Requires an exclusively owned API 37 emulator")
        package = checked(device.shell("dumpsys", "package", HOST))
        if f"targetSdk={args.expected_target}" not in package or "DEBUGGABLE" not in package:
            raise RuntimeError("Installed host target / debuggability mismatch")
        result["compat"] = otp_compat(checked(device.shell("dumpsys", "platform_compat")), args.expected_target)
        snapshot(device, [HOST], args.output / "before")
        apk = checked(device.shell("pm", "path", HOST)).splitlines()[0].removeprefix("package:")
        result["installedApkSha256"] = checked(device.shell("sha256sum", apk)).split()[0]
        result["deviceLogStart"] = checked(device.shell("date", "+%m-%d %H:%M:%S.000"))
        result["initialSms"] = device.shell("content", "query", "--uri", "content://sms",
                                             "--projection", "_id:address:body:date:contains_otp", "--where", f"address='{sender}'")
        if checked(result["initialSms"]) != "No result found.":
            raise RuntimeError("Fixture sender already has SMS; refuse to mix observations")
        script = Path(__file__).with_name("smoke-sms.js").read_text(encoding="utf-8").replace(
            "__SDK37_SMS_CONFIG__", json.dumps({"caseId": case_id, "sender": sender}))
        (args.output / "script.js").write_text(script, encoding="utf-8")
        result["scriptSha256"] = hashlib.sha256(script.encode()).hexdigest()
        result["taskCleanup"] = close_host_tasks(device)
        checked(device.shell("am", "force-stop", HOST))
        launched = True
        result["launch"] = device.shell("am", "start", "-n", HOST + "/org.autojs.autojs.external.open.RunIntentActivity",
                                        "-f", "0x10008000", "--es", "script", script, timeout=45)
        checked(result["launch"])
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            receipt = read_receipt(device, case_id)
            if receipt.get("errors") or receipt.get("complete"):
                raise RuntimeError("Script failed to become ready: " + json.dumps(receipt))
            if receipt.get("ready") is True and receipt.get("caseId") == case_id:
                break
            time.sleep(1)
        else:
            raise RuntimeError("SMS observer did not become ready")
        focus = checked(device.shell("dumpsys", "window"))
        result["focusedWindow"] = next((line.strip() for line in focus.splitlines() if "mCurrentFocus=" in line), "")
        if "org.autojs.autojs.execution.ScriptExecuteActivity" not in result["focusedWindow"]:
            raise RuntimeError("SMS script is not the focused activity: " + result["focusedWindow"])
        result["injections"] = {}
        for name, body in messages.items():
            injection = device.call("emu", "sms", "send", sender, body.replace("\n", "\\n"))
            checked(injection)
            if "OK" not in injection["stdout"] or "KO:" in injection["stdout"]:
                raise RuntimeError("Emulator did not accept SMS")
            result["injections"][name] = {"time": utc_now(), **injection}
            time.sleep(12)  # Let each Messages notification reach the real listener.
        deadline = time.monotonic() + args.observation_seconds
        while time.monotonic() < deadline:
            receipt = read_receipt(device, case_id)
            if receipt.get("errors") or receipt.get("complete"):
                raise RuntimeError("SMS observer stopped early: " + json.dumps(receipt))
            time.sleep(2)
        result["providerSms"] = device.shell("content", "query", "--uri", "content://sms",
            "--projection", "_id:address:body:date:contains_otp", "--where", f"address='{sender}'")
        checked(result["providerSms"])
        result["providerRows"] = provider_rows(result["providerSms"]["stdout"])
        if any(row["address"] != sender for row in result["providerRows"]):
            raise RuntimeError("Provider returned a different fixture sender")
        capture = subprocess.run([args.adb, "-s", args.serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=20)
        if capture.returncode != 0 or not capture.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("SMS screenshot failed")
        (args.output / "screen.png").write_bytes(capture.stdout)
        result["notificationDump"] = device.shell("dumpsys", "notification", "--noredact")
        write_json(args.output / "notifications.json", result.pop("notificationDump"))
        checked(device.shell("run-as", HOST, "touch", "files/sdk37-sms-" + case_id + ".stop"))
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            receipt = read_receipt(device, case_id)
            if receipt.get("complete") is True:
                break
            time.sleep(0.2)
        result["receipt"] = receipt
        result["errors"] = validate_observation(receipt, case_id, args.expected_target, messages,
                                                result["providerRows"], result["compat"]["enabled"])
        result["compatAfter"] = otp_compat(checked(device.shell("dumpsys", "platform_compat")), args.expected_target)
        if result["compat"] != result["compatAfter"]:
            result["errors"].append("Compatibility state changed during observation")
        result["success"] = not result["errors"]
    except Exception as error:
        result["error"] = f"{type(error).__name__}: {error}"
        result["receipt"] = read_receipt(device, case_id)
    finally:
        if launched:
            result["stop"] = device.shell("am", "force-stop", HOST)
        snapshot(device, [HOST], args.output / "after")
        write_json(args.output / "radio-log.json", device.call("logcat", "-b", "radio", "-d", "-T", result.get("deviceLogStart", "1")))
        result["ended"] = utc_now()
        write_json(args.output / "result.json", result)
    print(json.dumps({k: v for k, v in result.items() if k in ("caseId", "success", "errors", "error")}))
    return 0 if result["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
