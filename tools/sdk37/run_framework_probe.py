"""Run real Rhino framework probes on a disposable API 37 emulator.

Requires an installed debuggable host on an exclusively owned emulator. Stops its
current work and creates disposable fixtures. The allfiles suite observes the
explicitly expected permission state and opens Settings. Never installs APKs,
grants permissions, changes compatibility flags or deletes preexisting app data.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

from physical_device import Device, snapshot, utc_now, write_json

HOST = "org.autojs.autojs6"
ROWS = {
    "storage": {"files", "explorer", "volumes", "history", "trash", "media", "mediaTrash", "contacts", "permissionEntry"},
    "widgets": {"widgetMappings", "widgetFirstClick", "widgetUpdateClick", "widgetLargeIcon", "widgetLargeBitmap"},
    "notifications": {"standard", "foreground", "custom", "oversized"},
    "pickers": {"documentSelect", "documentCancel", "photoSelect", "photoCancel"},
    "allfiles": {"allFilesState", "permissionEntry"},
}
CHANGES = {
    "EXCLUDE_UNRELIABLE_STORAGE_VOLUMES": 391360514,
    "REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES": 157720069,
    "TRASH_BEHAVIOR_CHANGE": 461429441,
    "CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS": 270553691,
}


def compat_state(dump, package, target):
    states = {}
    for name, change_id in CHANGES.items():
        matches = [line.strip() for line in dump.splitlines() if f"name={name};" in line]
        if len(matches) != 1 or not re.match(rf"ChangeId\({change_id}; name={name}; enableSinceTargetSdk=37(?:;|\))", matches[0]):
            raise ValueError("Unrecognized compatibility definition: " + name)
        overrides = re.search(r"; packageOverrides=\{([^}]*)\}", matches[0])
        pairs = dict(re.findall(r"([\w.]+)=(true|false)(?:,|$)", overrides[1])) if overrides else {}
        states[name] = {"enabled": pairs[package] == "true" if package in pairs else target >= 37,
                        "source": "packageOverrides" if package in pairs else "targetSdk", "definition": matches[0]}
    return states


def validate_receipt(receipt, case_id, suite, target):
    errors = []
    if not isinstance(receipt, dict):
        return ["Missing receipt"]
    if receipt.get("caseId") != case_id or receipt.get("suite") != suite or receipt.get("target") != target or receipt.get("complete") is not True:
        errors.append("Receipt identity, target or completion mismatch")
    rows = receipt.get("rows", [])
    if not isinstance(rows, list) or any(not isinstance(row, dict) or not isinstance(row.get("name"), str) for row in rows):
        return errors + ["Malformed result rows"]
    names = [r.get("name") for r in rows]
    if set(names) != ROWS[suite] or len(names) != len(ROWS[suite]):
        errors.append("Missing, extra or duplicate result rows")
    if any(r.get("ok") is not True for r in rows) or receipt.get("error"):
        errors.append("Script assertion failed")
    return errors


def checked(result):
    if result["exitCode"] != 0:
        raise RuntimeError(json.dumps(result))
    return result["stdout"].strip()


def validate_allfiles_page(xml, expected):
    nodes = list(ET.fromstring(xml).iter("node"))
    labels = {n.get("text") for n in nodes if n.get("package") == "com.android.settings"}
    switches = [n for n in nodes if n.get("package") == "com.android.settings" and n.get("checkable") == "true"]
    return ({"All files access", "AutoJs6", "Allow access to manage all files"} <= labels
            and len(switches) == 1 and switches[0].get("checked") == str(expected).lower())


def drive_picker(device, pending, case_id, output):
    """Select the named fixture document or newest photo; the script checks its unique pixels."""
    name = pending["name"]
    expected_packages = ("documentsui",) if name.startswith("document") else ("photopicker", "providers.media")
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        windows = checked(device.shell("dumpsys", "window"))
        focus = next((line for line in windows.splitlines() if "mCurrentFocus=" in line), "")
        if any(part in focus for part in expected_packages):
            break
        time.sleep(0.2)
    else:
        raise RuntimeError("System picker did not become foreground: " + focus)
    time.sleep(1)
    path = "/sdcard/sdk37-picker-ui-" + case_id + ".xml"
    for attempt in range(5):
        dump = device.shell("uiautomator", "dump", "--compressed", path, timeout=15)
        write_json(output / (name + f"-dump-{attempt}.json"), dump)
        checked(dump)
        if "UI hierchary dumped to:" in dump["stdout"]:
            xml = checked(device.shell("cat", path))
            checked(device.shell("rm", path))
            nodes = list(ET.fromstring(xml).iter("node"))
            ready = name.endswith("Cancel") or any(
                node.get("text") == pending["documentName"] if name == "documentSelect" else
                node.get("resource-id", "").endswith("/icon_thumbnail") or node.get("content-desc", "").startswith("Photo taken on ")
                for node in nodes)
            if ready:
                break
            (output / (name + f"-loading-{attempt}.xml")).write_text(xml, encoding="utf-8")
        time.sleep(0.7)
    else:
        raise RuntimeError("System picker did not expose the required item: " + name)
    (output / (name + ".xml")).write_text(xml, encoding="utf-8")
    nodes = list(ET.fromstring(xml).iter("node"))
    packages = {node.get("package", "") for node in nodes}
    if not any(part in package for part in expected_packages for package in packages):
        raise RuntimeError("The expected system picker is not foreground: " + repr(packages))
    current = json.loads(checked(device.shell("run-as", HOST, "cat", "files/sdk37-framework-" + case_id + ".json")))
    if current.get("pending", {}).get("name") != name:
        raise RuntimeError("Picker request ended before the UI action: " + name)
    if name.endswith("Cancel"):
        for _ in range(5):
            windows = checked(device.shell("dumpsys", "window"))
            focus = next((line for line in windows.splitlines() if "mCurrentFocus=" in line), "")
            if not any(part in focus for part in expected_packages):
                raise RuntimeError("Refusing to send Back outside the expected picker")
            checked(device.shell("input", "keyevent", "4"))
            # Result callbacks can lag behind input dispatch. Do not queue Back into the host.
            for _ in range(20):
                time.sleep(0.2)
                current = json.loads(checked(device.shell("run-as", HOST, "cat", "files/sdk37-framework-" + case_id + ".json")))
                if current.get("pending", {}).get("name") != name:
                    return
        raise RuntimeError("The picker did not cancel after navigating back")
    if name == "documentSelect":
        matches = [node for node in nodes if node.get("text") == pending["documentName"]]
    else:
        matches = [node for node in nodes if node.get("resource-id", "").endswith("/icon_thumbnail")]
        if not matches:
            matches = [node for node in nodes if node.get("content-desc", "").startswith("Photo taken on ")]
        # This English AVD lists the newly indexed photo first. No other profile is supported.
        matches = matches[:1]
    if len(matches) != 1:
        raise RuntimeError(f"Expected exactly one identifiable test item for {name}, found {len(matches)}")
    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", matches[0].get("bounds", ""))
    if not bounds:
        raise RuntimeError("Picker item has no bounds")
    x1, y1, x2, y2 = map(int, bounds.groups())
    checked(device.shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)))
    if name == "photoSelect":
        for attempt in range(3):
            time.sleep(0.5)
            current = json.loads(checked(device.shell("run-as", HOST, "cat", "files/sdk37-framework-" + case_id + ".json")))
            if current.get("pending", {}).get("name") != name:
                return
            checked(device.shell("uiautomator", "dump", "--compressed", path, timeout=15))
            xml = checked(device.shell("cat", path))
            (output / f"photoSelect-confirm-{attempt}.xml").write_text(xml, encoding="utf-8")
            checked(device.shell("rm", path))
            current = json.loads(checked(device.shell("run-as", HOST, "cat", "files/sdk37-framework-" + case_id + ".json")))
            if current.get("pending", {}).get("name") != name:
                return
            nodes = list(ET.fromstring(xml).iter("node"))
            if not any(part in node.get("package", "") for part in expected_packages for node in nodes):
                raise RuntimeError("Photo Picker left the foreground before confirmation")
            done = [node for node in nodes if node.get("text") == "Done"]
            # A delayed informational banner can shift the grid after the initial dump.
            # Locate the same first photo again from the new hierarchy, never reuse old coordinates.
            photos = [node for node in nodes if node.get("resource-id", "").endswith("/icon_thumbnail")]
            if not photos:
                photos = [node for node in nodes if node.get("content-desc", "").startswith("Photo taken on ")]
            target = done[0] if len(done) == 1 else photos[0] if photos and attempt < 2 else None
            if target is None:
                raise RuntimeError("Expected the Photo Picker's explicit Done confirmation")
            bounds = list(map(int, re.findall(r"\d+", target.get("bounds", ""))))
            if len(bounds) != 4:
                raise RuntimeError("Photo Picker item has no bounds")
            checked(device.shell("input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2)))
            if done:
                return


def close_host_tasks(device):
    """Discard only simple tasks rooted in the test host, including its stale external pickers."""
    tasks = checked(device.shell("am", "stack", "list"))
    removed = []
    for block in re.split(r"(?=^RootTask id=)", tasks, flags=re.MULTILINE):
        root = re.match(r"RootTask id=(\d+)\b", block)
        children = re.findall(r"^\s+taskId=(\d+): (\S+)", block, re.MULTILINE)
        if root and "userId=0" in block.splitlines()[0] and len(children) == 1 and children[0][0] == root[1] and children[0][1].startswith(HOST + "/"):
            checked(device.shell("am", "stack", "remove", root[1]))
            removed.append(int(root[1]))
    return removed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--expected-target", type=int, required=True)
    parser.add_argument("--suite", choices=tuple(ROWS), required=True)
    parser.add_argument("--expect-trash-directory", choices=("true", "false"))
    parser.add_argument("--expect-all-files", choices=("true", "false"))
    parser.add_argument("--expect-widget-icon-rejected", choices=("true", "false"))
    parser.add_argument("--expect-compat", action="append", default=[], metavar="NAME=true|false",
                        help="Assert a host compatibility state without changing it; repeat as needed")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.suite == "storage" and args.expect_trash_directory is None:
        parser.error("Storage probes require an explicit --expect-trash-directory")
    if args.suite == "allfiles" and args.expect_all_files is None:
        parser.error("Permission probes require an explicit --expect-all-files")
    if args.suite == "widgets" and args.expect_widget_icon_rejected is None:
        parser.error("Widget probes require an explicit --expect-widget-icon-rejected for the tested image")
    expected_compat = {}
    for value in args.expect_compat:
        name, separator, enabled = value.partition("=")
        if name not in CHANGES or separator != "=" or enabled not in ("true", "false") or name in expected_compat:
            parser.error("Invalid or duplicate --expect-compat: " + value)
        expected_compat[name] = enabled == "true"
    if not re.fullmatch(r"emulator-\d+|127\.0\.0\.1:\d+", args.serial):
        parser.error("Select a disposable emulator explicitly")
    args.output.mkdir(parents=True, exist_ok=False)
    device = Device(args.adb, args.serial)
    device.require_online()
    identity = device.identity()
    if not identity["emulator"] or int(identity["sdk"]) < 37:
        parser.error("This probe requires an API 37 or newer emulator")
    case_id = uuid.uuid4().hex
    config = {"caseId": case_id, "suite": args.suite}
    config["ackPath"] = "/data/user/0/" + HOST + "/files/sdk37-framework-ack-" + case_id
    if args.expect_trash_directory is not None:
        config["expectTrashDirectory"] = args.expect_trash_directory == "true"
    if args.expect_all_files is not None:
        config["expectAllFiles"] = args.expect_all_files == "true"
    if args.expect_widget_icon_rejected is not None:
        config["expectWidgetIconRejected"] = args.expect_widget_icon_rejected == "true"
    result = {"started": utc_now(), "caseId": case_id, "suite": args.suite,
              "expectedTarget": args.expected_target, "config": config, "device": identity, "success": False}
    try:
        snapshot(device, [HOST], args.output / "before")
        state = (args.output / "before" / (HOST + "-package.txt")).read_text(encoding="utf-8")
        actual = re.search(r"\btargetSdk=(\d+)\b", state)
        if not actual or int(actual[1]) != args.expected_target or "DEBUGGABLE" not in state:
            raise RuntimeError("Installed APK must be debuggable and have the expected target")
        result["hostCompat"] = compat_state(checked(device.shell("dumpsys", "platform_compat")), HOST, args.expected_target)
        if any(result["hostCompat"][name]["enabled"] != enabled for name, enabled in expected_compat.items()):
            raise RuntimeError("Host compatibility state does not match --expect-compat")
        result["apkSha256"] = [checked(device.shell("sha256sum", p.removeprefix("package:"))).split()[0]
                               for p in checked(device.shell("pm", "path", HOST)).splitlines()]
        common = Path(__file__).with_name("framework-probe-common.js").read_text(encoding="utf-8")
        body = Path(__file__).with_name("smoke-" + args.suite + ".js").read_text(encoding="utf-8")
        script = common.replace("__SDK37_FRAMEWORK_CONFIG__", json.dumps(config)) + "\n" + body
        script = '"ui";\n' + script
        # Keep the command-line transport ASCII across Windows adb installations.
        script = re.sub(r"[^\x00-\x7f]", lambda m: json.dumps(m[0], ensure_ascii=True)[1:-1], script)
        (args.output / "launched-script.js").write_text(script, encoding="utf-8")
        result["scriptSha256"] = hashlib.sha256(script.encode()).hexdigest()
        result["deviceLogStart"] = checked(device.shell("date", "+%m-%d %H:%M:%S.000"))
        result["removedHostTasks"] = close_host_tasks(device)
        if result["removedHostTasks"]:
            time.sleep(0.5)
        checked(device.shell("am", "force-stop", HOST))
        launch = device.shell("am", "start", "-n", HOST + "/org.autojs.autojs.external.open.RunIntentActivity",
                              "-f", "0x10008000", "--es", "script", script)
        write_json(args.output / "launch.json", launch)
        checked(launch)
        deadline = time.monotonic() + 120
        receipt = None
        driven = set()
        while time.monotonic() < deadline:
            raw = device.shell("run-as", HOST, "cat", "files/sdk37-framework-" + case_id + ".json")
            if raw["exitCode"] == 0:
                try:
                    receipt = json.loads(raw["stdout"])
                except json.JSONDecodeError:
                    time.sleep(0.2)
                    continue
                result["receipt"] = receipt
                if receipt.get("complete"):
                    break
                pending = receipt.get("pending")
                if args.suite == "pickers" and pending and pending["name"] not in driven:
                    drive_picker(device, pending, case_id, args.output)
                    driven.add(pending["name"])
            elif "No such file or directory" not in raw["stderr"]:
                checked(raw)
            time.sleep(0.5)
        result["receipt"] = receipt
        result["errors"] = validate_receipt(receipt, case_id, args.suite, args.expected_target)
        after_compat = compat_state(checked(device.shell("dumpsys", "platform_compat")), HOST, args.expected_target)
        if after_compat != result["hostCompat"]:
            result["errors"].append("Compatibility state changed during the probe")
        if args.suite == "notifications":
            checked(device.shell("cmd", "statusbar", "expand-notifications"))
            time.sleep(1)
            (args.output / "notifications.txt").write_text(checked(device.shell("dumpsys", "notification", "--noredact")), encoding="utf-8")
            ui_path = "/sdcard/sdk37-notifications-" + case_id + ".xml"
            checked(device.shell("uiautomator", "dump", "--compressed", ui_path, timeout=15))
            xml = checked(device.shell("cat", ui_path))
            (args.output / "notification-shade.xml").write_text(xml, encoding="utf-8")
            tree = ET.fromstring(xml)
            groups = []
            for node in tree.iter("node"):
                descendants = list(node.iter("node"))
                buttons = [child for child in descendants if child.get("resource-id", "").endswith("/expand_button")]
                if buttons and any(case_id in child.get("text", "") for child in descendants):
                    groups.append((len(descendants), buttons[0]))
            if groups:
                node = min(groups, key=lambda pair: pair[0])[1]
                bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
                if len(bounds) == 4:
                    checked(device.shell("input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2)))
                    time.sleep(0.7)
                    checked(device.shell("uiautomator", "dump", "--compressed", ui_path, timeout=15))
                    (args.output / "notification-shade-expanded.xml").write_text(checked(device.shell("cat", ui_path)), encoding="utf-8")
            checked(device.shell("rm", ui_path))
        windows = checked(device.shell("dumpsys", "window"))
        if args.suite == "allfiles":
            for _ in range(50):
                if re.search(r"mCurrentFocus=.*com\.android\.settings/", windows):
                    break
                time.sleep(0.2)
                windows = checked(device.shell("dumpsys", "window"))
            else:
                result["errors"].append("The app-specific all-files Settings page is not in the foreground")
            # The Settings entry redirects to SpaActivity on this image; inspect the page itself.
            ui_path = "/sdcard/sdk37-permission-" + case_id + ".xml"
            checked(device.shell("uiautomator", "dump", "--compressed", ui_path, timeout=15))
            xml = checked(device.shell("cat", ui_path))
            checked(device.shell("rm", ui_path))
            (args.output / "permission-page.xml").write_text(xml, encoding="utf-8")
            if not validate_allfiles_page(xml, config["expectAllFiles"]):
                result["errors"].append("Settings page identity or all-files switch state is incorrect")
        (args.output / "windows.txt").write_text(windows, encoding="utf-8")
        if args.suite in ("storage", "widgets") and not re.search(r"mCurrentFocus=.*org\.autojs\.autojs6/.*ScriptExecuteActivity", windows):
            result["errors"].append("Probe host Activity is not in the foreground")
        capture = subprocess.run([args.adb, "-s", args.serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=20)
        if capture.returncode or not capture.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Screenshot capture failed")
        (args.output / "screen.png").write_bytes(capture.stdout)
        if args.suite == "notifications":
            checked(device.shell("run-as", HOST, "touch", config["ackPath"]))
            checked(device.shell("cmd", "statusbar", "collapse"))
            for _ in range(30):
                raw = device.shell("run-as", HOST, "cat", "files/sdk37-framework-" + case_id + ".json")
                try:
                    cleaned = json.loads(raw["stdout"])
                    if cleaned.get("cleaned") is True:
                        result["receipt"] = cleaned
                        break
                except json.JSONDecodeError:
                    pass
                time.sleep(0.1)
            else:
                result["errors"].append("Notification cleanup receipt missing")
        result["success"] = not result["errors"]
    except Exception as error:
        result["error"] = f"{type(error).__name__}: {error}"
    finally:
        try:
            device.require_online()
            snapshot(device, [HOST], args.output / "after")
            logs = device.call("logcat", "-d", "-T", result.get("deviceLogStart", "1"), "-v", "threadtime",
                               "NotificationService:V", "AppWidgetServiceImpl:V", "AndroidRuntime:E", "ActivityManager:I", "*:S")
            (args.output / "logcat-tail.txt").write_text(logs["stdout"] + logs["stderr"], encoding="utf-8")
            checked(logs)
            if result.get("success") and args.suite == "notifications":
                number = result["receipt"]["notificationIds"]["oversized"]
                lines = [line for line in logs["stdout"].splitlines() if f"id: {number}" in line and HOST in line]
                rejected = result["hostCompat"]["CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS"]["enabled"]
                expected = "Dropped notification due to too large RemoteViews" if rejected else "this WILL notification WILL be dropped"
                result["notificationMemoryLog"] = lines
                if not any(expected in line for line in lines):
                    result["errors"].append("Missing SystemUI memory-limit evidence for this notification ID")
                    result["success"] = False
            if result.get("success") and args.suite == "widgets" and not config["expectWidgetIconRejected"]:
                if "This will throw an exception for targetSdk > 37" not in logs["stdout"]:
                    result["errors"].append("Missing AppWidgetService warning for the accepted oversized Icon")
                    result["success"] = False
        except (OSError, RuntimeError, ValueError) as error:
            result["evidenceCaptureError"] = str(error)
            result["success"] = False
        result["ended"] = utc_now()
        write_json(args.output / "result.json", result)
    print(json.dumps({k: v for k, v in result.items() if k in ("caseId", "suite", "success", "error", "errors")}))
    return 0 if result["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
