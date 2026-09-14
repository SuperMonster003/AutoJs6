"""Observe production activity state, IME and resize on an exclusively owned API 37 AVD.

Requires the installed debuggable host, overlay / all-files test access, and an
English unlocked emulator with software IME and notifications enabled. Stops host work, changes
display size/density/rotation temporarily, and opens Settings as a split partner.
Does not install packages or grant permissions. Never use on a shared emulator.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import time
import uuid

from physical_device import Device, snapshot, utc_now, write_json
from run_framework_probe import checked, close_host_tasks
from configuration_evidence import ACTIVITIES, retained, validate_result

HOST = "org.autojs.autojs6"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--expected-target", type=int, choices=(36, 37), required=True)
    parser.add_argument("--form", choices=("phone", "tablet"), required=True)
    parser.add_argument("--route", choices=ACTIVITIES, required=True)
    parser.add_argument("--editor-state", choices=("dirty", "clean"), default="dirty")
    parser.add_argument("--editor-range", action="store_true", help="Select offsets 12..32 instead of an end-of-file caret")
    parser.add_argument("--editor-repeat", type=int, default=1, help="Repeat the unique line up to 8192 times; 8192 exercises file-backed draft restoration")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--pause-before-split", type=Path, help="Wait up to 120 seconds for this marker after writing debug-ready.json; debugger diagnostics only")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Requires an explicit emulator serial")
    if not 1 <= args.editor_repeat <= 8192:
        parser.error("Editor repetition count must be 1..8192")
    if not args.route.startswith("edit-") and (args.editor_range or args.editor_state != "dirty" or args.editor_repeat != 1):
        parser.error("Editor state / range options require an editor route")
    args.output.mkdir(parents=True, exist_ok=False)
    device = Device(args.adb, args.serial)
    case = uuid.uuid4().hex
    seed = "// sdk37-unsaved-" + case
    result = {"caseId": case, "started": utc_now(), "route": args.route, "form": args.form,
              "target": args.expected_target, "success": False, "stages": {},
              "editorState": args.editor_state, "editorRange": args.editor_range, "editorRepeat": args.editor_repeat}
    launched = False
    display_changed = False
    split_task = None
    command_id = 0

    def state_retained(state):
        return retained(state, args.route, seed, dirty=args.editor_state == "dirty", repeat=args.editor_repeat)

    def receipt():
        raw = device.shell("run-as", HOST, "cat", "files/sdk37-config-" + case + ".json")
        try:
            return json.loads(raw["stdout"])
        except json.JSONDecodeError:
            return {}

    def wait_for(predicate, label, seconds=20):
        deadline = time.monotonic() + seconds
        latest = {}
        while time.monotonic() < deadline:
            latest = receipt()
            if latest.get("errors"):
                raise RuntimeError(json.dumps(latest["errors"]))
            if predicate(latest):
                return latest
            time.sleep(0.3)
        raise RuntimeError("Timed out: " + label + " " + json.dumps(latest.get("current")))

    def command(action, **values):
        nonlocal command_id
        command_id += 1
        value = json.dumps({"id": command_id, "action": action, **values})
        # Quote both the remote shell command and its JSON payload.
        path = "files/sdk37-config-" + case + ".command"
        checked(device.shell("run-as", HOST, "sh", "-c", "printf %s " + shlex.quote(value)
                             + " > " + path + ".tmp && mv " + path + ".tmp " + path))
        return wait_for(lambda r: any(c["id"] == command_id for c in r.get("commands", [])), action)

    def capture(label):
        deadline = time.monotonic() + 10
        while True:
            window = device.shell("dumpsys", "window")
            focused = next((line.strip() for line in checked(window).splitlines() if "mCurrentFocus=" in line), "")
            if ACTIVITIES[args.route] in focused or "Application Error" in focused or "Application Not Responding" in focused:
                break
            if time.monotonic() >= deadline:
                break
            time.sleep(0.3)
        if ACTIVITIES[args.route] not in focused:
            raise RuntimeError("Expected the production activity in focus: " + focused)
        if "Application Error" in focused or "Application Not Responding" in focused:
            raise RuntimeError("Application error dialog is in focus")
        state = command("snapshot").get("current")
        if state:
            state["focusedWindow"] = focused
        result["stages"][label] = state
        capture = subprocess.run([args.adb, "-s", args.serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=20)
        if capture.returncode != 0 or not capture.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Screenshot failed")
        (args.output / (label + ".png")).write_bytes(capture.stdout)
        write_json(args.output / (label + "-window.json"), window)
        return state

    try:
        device.require_online()
        result["device"] = device.identity()
        if not result["device"]["emulator"] or result["device"]["sdk"] != "37":
            raise RuntimeError("Requires an exclusively owned API 37 AVD")
        package = checked(device.shell("dumpsys", "package", HOST))
        if f"targetSdk={args.expected_target} " not in package and f"targetSdk={args.expected_target}\n" not in package:
            raise RuntimeError("Host target mismatch")
        if "DEBUGGABLE" not in package:
            raise RuntimeError("Host must be debuggable")
        snapshot(device, [HOST], args.output / "before")
        result["displayBefore"] = {key: checked(device.shell("wm", key)) for key in ("size", "density", "user-rotation")}
        result["compat"] = [line.strip() for line in checked(device.shell("dumpsys", "platform_compat")).splitlines()
                            if "DISABLE_OPT_OUT_UNIVERSAL_RESIZABLE_BY_DEFAULT" in line or "OVERRIDABLE_COMPONENT_CALLBACKS" in line]
        result["memoryLimiter"] = device.shell("am", "memory-limiter", "status")
        apk = checked(device.shell("pm", "path", HOST)).splitlines()[0].removeprefix("package:")
        result["installedApkSha256"] = checked(device.shell("sha256sum", apk)).split()[0]
        result["taskCleanup"] = close_host_tasks(device)
        checked(device.shell("am", "force-stop", HOST))
        launched = True
        display_changed = True
        checked(device.shell("wm", "size", "1200x1920" if args.form == "tablet" else "1080x2424"))
        checked(device.shell("wm", "density", "240" if args.form == "tablet" else "420"))
        checked(device.shell("wm", "user-rotation", "lock", "0"))
        time.sleep(1)
        # Keep the non-UI observer's process foreground while its engine starts.
        result["warmup"] = device.shell("am", "start", "-W", "-n", HOST + "/" + ACTIVITIES["main"])
        checked(result["warmup"])
        # Size/density and rotation settings propagate asynchronously. Reassert portrait
        # after warmup so a stale rotation from the previous tablet run cannot seed the case.
        result["portraitRotation"] = device.shell("wm", "user-rotation", "lock", "0")
        checked(result["portraitRotation"])
        time.sleep(0.5)
        script = Path(__file__).with_name("smoke-configuration.js").read_text(encoding="utf-8").replace(
            "__SDK37_CONFIGURATION_CONFIG__", json.dumps({"caseId": case, "seed": seed, "route": args.route,
                "activityClass": ACTIVITIES[args.route], "editorState": args.editor_state, "editorRange": args.editor_range,
                "editorRepeat": args.editor_repeat}))
        (args.output / "script.js").write_text(script, encoding="utf-8")
        result["scriptSha256"] = hashlib.sha256(script.encode()).hexdigest()
        result["launch"] = device.shell("am", "start", "-n", HOST + "/org.autojs.autojs.external.open.RunIntentActivity", "-f", "0x10000000", "--es", "script", script, timeout=45)
        checked(result["launch"])
        wait_for(lambda r: r.get("caseId") == case and r.get("ready") is True, "observer readiness", 120)
        command("launch")
        wait_for(lambda r: (r.get("current") or {}).get("activity") == ACTIVITIES[args.route], "activity", 90)
        wait_for(lambda r: (r.get("current") or {}).get("configuration", {}).get("orientation") == 1,
                 "initial portrait configuration")
        if args.route.startswith("edit-"):
            wait_for(lambda r: (r.get("current") or {}).get("editor", {}).get("ready") is True, "editor readiness", 60)
            if args.editor_state == "clean":
                wait_for(lambda r: state_retained(r.get("current")), "clean file loaded", 60)
        command("seed")
        wait_for(lambda r: state_retained(r.get("current")), "seeded text")
        command("ime")
        wait_for(lambda r: (r.get("current") or {}).get("imeVisible") is True, "visible keyboard")
        portrait = capture("portrait")
        if portrait["configuration"]["orientation"] != 1 or (portrait["configuration"]["smallestWidthDp"] >= 600) != (args.form == "tablet"):
            raise RuntimeError("Unexpected form factor / initial orientation")
        if args.route.startswith("edit-") and portrait["editor"]["type"] != ("ACE" if args.route == "edit-ace" else "NATIVE"):
            raise RuntimeError("Requested editor engine is unavailable")
        if args.route == "script":
            command("orientation", value=0)
            time.sleep(1)
            expected = 1 if args.form == "tablet" else 2
            wait_for(lambda r: (r.get("current") or {}).get("configuration", {}).get("orientation") == expected, "requested orientation")
            capture("requested-landscape")
            command("orientation", value=-1)
            wait_for(lambda r: (r.get("current") or {}).get("configuration", {}).get("orientation") == 1, "restore portrait")
        checked(device.shell("wm", "user-rotation", "lock", "1"))
        wait_for(lambda r: r.get("current") and r["current"]["configuration"]["orientation"] == 2, "landscape")
        wait_for(lambda r: state_retained(r.get("current")), "state after rotation")
        time.sleep(1)
        landscape = capture("landscape")
        if not state_retained(landscape):
            raise RuntimeError("Text / unsaved state lost after rotation")
        if landscape["imeVisible"]:
            # Main's search action deliberately collapses on Back. Hide only the IME here
            # so the following recreation still has an active query to preserve.
            if args.route == "main":
                command("hide-ime")
            else:
                checked(device.shell("input", "keyevent", "KEYCODE_BACK"))
            wait_for(lambda r: r.get("current") and r["current"]["imeVisible"] is False, "keyboard hidden")
        time.sleep(1)
        capture("keyboard-hidden")
        if args.form == "tablet":
            split_task = landscape["taskId"]
            if args.pause_before_split:
                if args.pause_before_split.exists():
                    raise RuntimeError("Debugger continuation marker already exists")
                write_json(args.output / "debug-ready.json", {"pid": checked(device.shell("pidof", HOST)), "taskId": split_task})
                deadline = time.monotonic() + 120
                while not args.pause_before_split.exists() and time.monotonic() < deadline:
                    time.sleep(0.5)
                if not args.pause_before_split.exists():
                    raise RuntimeError("Debugger continuation timed out")
            checked(device.shell("am", "start", "-n", "com.android.settings/.Settings"))
            time.sleep(1)
            result["splitStart"] = device.shell("wm", "shell", "splitscreen", "moveToSideStage", str(split_task), "0")
            checked(result["splitStart"])
            wait_for(lambda r: r.get("current") and r["current"].get("multiWindow") is True, "split screen", 30)
            wait_for(lambda r: state_retained(r.get("current")), "state after entering split", 30)
            time.sleep(1)
            split = capture("split")
            if not state_retained(split):
                raise RuntimeError("Text / unsaved state lost in split screen")
            result["splitExit"] = device.shell("wm", "shell", "splitscreen", "exitSplitScreen", str(split_task))
            checked(result["splitExit"])
            split_task = None
            wait_for(lambda r: r.get("current") and r["current"].get("multiWindow") is False, "exit split")
            wait_for(lambda r: state_retained(r.get("current")), "state after leaving split", 30)
            time.sleep(1)
            restored = capture("restored")
            if not state_retained(restored):
                raise RuntimeError("Text / unsaved state lost when leaving split")
        command("finish")
        result["receipt"] = receipt()
        result["success"] = result["receipt"].get("complete") is True
    except Exception as error:
        result["error"] = f"{type(error).__name__}: {error}"
        result["receipt"] = receipt()
        if launched:
            write_json(args.output / "failure-window.json", device.shell("dumpsys", "window"))
            write_json(args.output / "failure-log.json", device.call("logcat", "-d", "-t", "1800"))
            failure_image = subprocess.run([args.adb, "-s", args.serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=20)
            if failure_image.returncode == 0 and failure_image.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
                (args.output / "failure.png").write_bytes(failure_image.stdout)
    finally:
        if split_task is not None:
            result["splitCleanup"] = device.shell("wm", "shell", "splitscreen", "exitSplitScreen", str(split_task))
        if launched:
            result["stop"] = device.shell("am", "force-stop", HOST)
        if display_changed:
            cleanup = []
            for key in ("size", "density"):
                match = re.search(r"Override (?:size|density): (\S+)", result["displayBefore"][key])
                cleanup.append(device.shell("wm", key, match[1] if match else "reset"))
            cleanup.append(device.shell("wm", "user-rotation", *result["displayBefore"]["user-rotation"].split()))
            result["displayCleanup"] = cleanup
            if any(r["exitCode"] != 0 for r in cleanup):
                result["success"] = False
        if launched:
            snapshot(device, [HOST], args.output / "after")
        result["validationErrors"] = validate_result(result)
        result["success"] = result["success"] and not result["validationErrors"]
        result["ended"] = utc_now()
        write_json(args.output / "result.json", result)
    print(json.dumps({k: result[k] for k in ("caseId", "route", "form", "success", "error", "validationErrors") if k in result}))
    return 0 if result["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
