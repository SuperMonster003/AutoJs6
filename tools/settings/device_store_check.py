"""Device checks of the settings store layer (docs/settings/ROADMAP.md P1.2 / P1.4 / P2, evidence level E3).

Usage: py -3.12 -I tools/settings/device_store_check.py -s SERIAL CHECK [--install APK] [--state FILE]
       [--domains NAMES] [--activity CLASS --screenshot PNG] [--engines native,ace]

CHECK is one of
  interop       P1.2: SharedPreferencesStore and the old Pref / SharedPreferences calls read and write the same file
                (default and named store, all value types, change events); runs device/store_interop.js.
  lists-before  P1.4, on the old build: records the raw text and Gson-decoded items of key_working_directory_history,
                key_pc_server_address_history and key_ignored_updates (seeding absent ones) and a probe list into
                --state; runs device/legacy_lists.js.
  lists-after   P1.4, on the new build: compares the same values read and rewritten by the new Pref with --state and
                removes the seeded and probe keys.
  domains       P2: for the domain objects in --domains (comma separated, e.g. AppearanceKeys,GeneralKeys), each
                declared key reads what the old code read (default and stored value, device resources), and the
                callers moved to the keys still work; written keys are restored; runs device/domain_keys.js.
  page          P2: starts the activity --activity (a settings page, not exported) from inside the app and saves a
                screenshot to --screenshot after --settle seconds; runs device/open_page.js.
  editor        P2.3: with no stored text size, a new native code editor starts with the settings page default, and a
                demo script opens in the code editor with each engine of --engines; one screenshot per engine
                (--screenshot is the prefix), then the engine, selection mode and text size are restored; runs
                device/editor_engines.js.

--install installs APK with "adb install -r -d" first (debug builds of the same signature keep their data). The
script is pushed to /sdcard/Download/aj6-settings-check, started through RunIntentActivity and removed afterwards;
its "AJ6SET" console lines are read from logcat. Output is one line per check (PASS / FAIL and counts); the exit
status is 1 when a check fails or the script does not finish.
"""
import argparse
import json
import pathlib
import subprocess
import sys
import time
import urllib.parse

HERE = pathlib.Path(__file__).resolve().parent
PACKAGE = "org.autojs.autojs6"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
DEVICE_DIR = "/sdcard/Download/aj6-settings-check"
MARK = "AJ6SET"
SCRIPTS = {"interop": "store_interop.js", "lists-before": "legacy_lists.js", "lists-after": "legacy_lists.js",
           "domains": "domain_keys.js", "page": "open_page.js", "editor": "editor_engines.js"}


def adb(serial, *args, check=False):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check)


def device_epoch(serial):
    return adb(serial, "shell", "date", "+%s", check=True).stdout.strip()


def marked_lines(serial, epoch):
    out = adb(serial, "logcat", "-d", "-v", "epoch", "-T", f"{epoch}.000", "-s", "GlobalConsole").stdout
    lines = []
    for line in out.splitlines():
        start = line.find(MARK + " ")
        if start >= 0:
            lines.append(line[start + len(MARK) + 1:].rstrip())
    return lines


def run_script(serial, text, wait):
    local = HERE / "__pycache__" / "aj6_settings_check.js"
    local.parent.mkdir(exist_ok=True)
    local.write_text(text, encoding="utf-8")
    remote = f"{DEVICE_DIR}/aj6_settings_check.js"
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    adb(serial, "push", str(local), remote, check=True)
    epoch = device_epoch(serial)
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)
    deadline = time.time() + wait
    lines = []
    while time.time() < deadline:
        time.sleep(1)
        lines = marked_lines(serial, epoch)
        if "done" in lines:
            break
    adb(serial, "shell", "rm", "-rf", DEVICE_DIR)
    return lines


def host_version(serial):
    out = adb(serial, "shell", "dumpsys", "package", PACKAGE).stdout
    for token in out.split():
        if token.startswith("versionCode="):
            return token.split("=", 1)[1]
    return "?"


def screenshot(serial, path):
    png = subprocess.run(["adb", "-s", serial, "exec-out", "screencap", "-p"], capture_output=True).stdout
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"INFO screenshot {path} ({len(png)} bytes)")


def editor_check(args, script):
    failed = 0
    snapshot = None
    for index, engine in enumerate(e.strip() for e in args.engines.split(",") if e.strip()):
        prefix = f"var AJ6_ENGINE = {json.dumps(engine)}; var AJ6_SNAPSHOT = {json.dumps(index == 0)};"
        lines = run_script(args.serial, prefix + chr(10) + script, args.wait)
        for line in lines:
            kind, _, rest = line.partition(" ")
            if kind in ("PASS", "FAIL"):
                print(f"{kind} {rest}")
                failed += kind == "FAIL"
            elif kind == "snapshot":
                snapshot = json.loads(urllib.parse.unquote(rest))
        if "done" not in lines:
            print(f"FAIL the {engine} run did not finish (see logcat GlobalConsole)")
            failed += 1
        time.sleep(args.settle)
        screenshot(args.serial, args.screenshot.with_name(f"{args.screenshot.stem}-{engine}{args.screenshot.suffix or '.png'}"))
    if snapshot is None:
        print("FAIL no snapshot to restore")
        failed += 1
    else:
        lines = run_script(args.serial, f"var AJ6_RESTORE = {json.dumps(snapshot)};" + chr(10) + script, args.wait)
        for line in lines:
            kind, _, rest = line.partition(" ")
            if kind in ("PASS", "FAIL"):
                print(f"{kind} {rest}")
                failed += kind == "FAIL"
    print(f"editor: {'FAIL' if failed else 'PASS'} ({failed} failed)")
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("check", choices=sorted(SCRIPTS))
    parser.add_argument("--install", type=pathlib.Path, help="APK to install with adb install -r -d before the check")
    parser.add_argument("--state", type=pathlib.Path, help="state file written by lists-before and read by lists-after")
    parser.add_argument("--wait", type=int, default=60, help="seconds to wait for the script (default 60)")
    parser.add_argument("--domains", help="domain objects for the domains check, comma separated")
    parser.add_argument("--activity", help="activity class for the page check")
    parser.add_argument("--screenshot", type=pathlib.Path, help="PNG written by the page check")
    parser.add_argument("--settle", type=float, default=3.0, help="seconds between starting the page and the screenshot")
    parser.add_argument("--engines", default="native,ace", help="editor engines for the editor check")
    args = parser.parse_args()

    if args.check.startswith("lists-") and args.state is None:
        parser.error("--state is required for lists-before / lists-after")
    if args.check == "domains" and not args.domains:
        parser.error("--domains is required for domains")
    if args.check == "page" and not (args.activity and args.screenshot):
        parser.error("--activity and --screenshot are required for page")
    if args.check == "editor" and not args.screenshot:
        parser.error("--screenshot (a prefix) is required for editor")
    if args.install:
        result = adb(args.serial, "install", "-r", "-d", str(args.install))
        if "Success" not in result.stdout:
            sys.exit(f"install failed: {(result.stdout + result.stderr).strip()[-300:]}")
        print(f"installed {args.install.name}")
    model = adb(args.serial, "shell", "getprop", "ro.product.model").stdout.strip()
    sdk = adb(args.serial, "shell", "getprop", "ro.build.version.sdk").stdout.strip()
    print(f"device {args.serial}: {model}, API {sdk}, host versionCode {host_version(args.serial)}")

    script = (HERE / "device" / SCRIPTS[args.check]).read_text(encoding="utf-8")
    if args.check == "editor":
        return editor_check(args, script)
    if args.check == "lists-before":
        script = "var AJ6_PHASE = 'before'; var AJ6_STATE = null;\n" + script
    elif args.check == "lists-after":
        state = json.loads(args.state.read_text(encoding="utf-8"))
        script = f"var AJ6_PHASE = 'after'; var AJ6_STATE = {json.dumps(state['values'])};\n" + script
    elif args.check == "domains":
        names = [name.strip() for name in args.domains.split(",") if name.strip()]
        script = f"var AJ6_DOMAINS = {json.dumps(names)};\n" + script
    elif args.check == "page":
        script = f"var AJ6_ACTIVITY = {json.dumps(args.activity)};\n" + script
    lines = run_script(args.serial, script, args.wait)

    failed = 0
    values = {}
    for line in lines:
        kind, _, rest = line.partition(" ")
        if kind in ("PASS", "FAIL"):
            print(f"{kind} {rest}")
            failed += kind == "FAIL"
        elif kind == "state":
            key, _, encoded = rest.partition(" ")
            values[key] = json.loads(urllib.parse.unquote(encoded))
            v = values[key]
            print(f"STATE {key}: {'seeded' if v['seeded'] else 'existing'}, {len(v['decoded'])} items")
        elif kind == "info":
            print(f"INFO {rest}")
    if args.check == "lists-before":
        args.state.parent.mkdir(parents=True, exist_ok=True)
        args.state.write_text(json.dumps({"serial": args.serial, "model": model, "sdk": sdk, "values": values},
                                         ensure_ascii=True, indent=1), encoding="utf-8")
    if args.check == "page" and not failed:
        time.sleep(args.settle)
        screenshot(args.serial, args.screenshot)
    if "done" not in lines:
        print("FAIL the script did not finish (see logcat GlobalConsole)")
        failed += 1
    print(f"{args.check}: {'FAIL' if failed else 'PASS'} ({failed} failed)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
