"""Device checks of the page "Manage AutoJs6 settings" and its export wizard (docs/settings/ROADMAP.md P6.2 - P6.4, E3).

Usage: py -3.12 -I tools/settings/device_transfer_check.py -s SERIAL CHECK --out DIR [--install APK]
       [--locale TAG] [--settle SECONDS]

CHECK is one of
  page     P6.2: opens the page (not exported, so from inside the app through device/open_transfer.js) and saves
           DIR/page-<locale>.png.
  rotate   P6.2 / P6.3: opens the page with the export wizard, saves DIR/wizard-<locale>-portrait.png, turns the screen
           to landscape (DIR/...-landscape.png) and back (DIR/...-back.png), and checks that the page is still the
           resumed activity and the app process survived. With --remembered IDS (comma separated category ids) the
           remembered selection of the export wizard is set to IDS before the wizard opens and to the complement after
           it has opened: the wizard must still show IDS after the rotation (the selection comes from the saved wizard
           state, not from the remembered one). The remembered selection and the rotation are restored afterwards.
  wizard   P6.3 / P6.4: opens the page with the export wizard and saves DIR/wizard-<locale>.png; the maintainer then
           goes through the wizard on the device (dialogs and the system "save" page are not tapped by this script).
  collect  P6.4: saves DIR/collect-<locale>.png (the done page), pulls the newest /sdcard/Download/autojs6-settings-*.zip
           into DIR and checks it with archive_inspect.py (--expect-categories N, --expect-ids IDS).

--locale sets the language of the app (`cmd locale set-app-locales`, Android 13+) for the check and restores the
previous one. Screenshots are only saved; compare them by eye (no taps are sent, dialogs are left to the maintainer).
Output is one line per check (PASS / FAIL and key values); the exit status is 1 when a check fails.
"""
import argparse
import json
import pathlib
import re
import sys
import time
import urllib.parse

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import archive_inspect  # noqa: E402
import device_store_check as base  # noqa: E402

ACTIVITY = "org.autojs.autojs.ui.settings.transfer.SettingsTransferActivity"


def resumed_activity(serial):
    out = base.adb(serial, "shell", "dumpsys", "activity", "activities").stdout
    match = re.search(r"(?:topResumedActivity|mResumedActivity|ResumedActivity)[:=].*?\{[^}]*?\s([\w.]+/[\w.$]+)", out)
    return match.group(1) if match else "?"


def app_pid(serial):
    return base.adb(serial, "shell", "pidof", base.PACKAGE).stdout.strip()


def app_locales(serial):
    out = base.adb(serial, "shell", "cmd", "locale", "get-app-locales", base.PACKAGE).stdout
    match = re.search(r"are \[(.*)]", out)
    return match.group(1).replace(" ", "") if match else ""


def set_app_locales(serial, tags):
    base.adb(serial, "shell", "cmd", "locale", "set-app-locales", base.PACKAGE, "--locales", tags or "", check=True)


def open_page(args, wizard=None, remembered=None):
    """Runs device/open_transfer.js; returns the number of failures and the recorded original selection, if any."""
    script = (HERE / "device" / "open_transfer.js").read_text(encoding="utf-8")
    prefix = (f"var AJ6_ACTIVITY = {json.dumps(ACTIVITY)}; var AJ6_WIZARD = {json.dumps(wizard)};"
              f" var AJ6_REMEMBERED = {json.dumps(remembered)};")
    lines = base.run_script(args.serial, prefix + chr(10) + script, args.wait)
    original = [json.loads(urllib.parse.unquote(line.split(" ", 1)[1])) for line in lines if line.startswith("original ")]
    return report(lines), (original[0] if original else None)


def report(lines):
    failed = 0
    for line in lines:
        kind, _, rest = line.partition(" ")
        if kind in ("PASS", "FAIL"):
            print(f"{kind} {rest}")
            failed += kind == "FAIL"
        elif kind == "info":
            print(f"INFO {rest}")
    if "done" not in lines:
        print("FAIL the script did not finish (see logcat GlobalConsole)")
        failed += 1
    return failed


def rotate(serial, rotation):
    base.adb(serial, "shell", "settings", "put", "system", "accelerometer_rotation", "0", check=True)
    base.adb(serial, "shell", "settings", "put", "system", "user_rotation", str(rotation), check=True)


def rotate_check(args, tag):
    failed = 0
    saved = {name: base.adb(args.serial, "shell", "settings", "get", "system", name).stdout.strip()
             for name in ("accelerometer_rotation", "user_rotation")}
    remembered = [i.strip() for i in args.remembered.split(",") if i.strip()] if args.remembered else None
    original = None
    try:
        rotate(args.serial, 0)
        time.sleep(1)
        base.adb(args.serial, "shell", "am", "force-stop", base.PACKAGE, check=True)
        count, original = open_page(args, wizard="export", remembered=remembered and {"before": remembered})
        failed += count
        time.sleep(args.settle)
        pid = app_pid(args.serial)
        if remembered:
            failed += open_page(args, remembered={"after": remembered})[0]
            time.sleep(args.settle)
        base.screenshot(args.serial, args.out / f"wizard-{tag}-portrait.png")
        for rotation, name in ((1, "landscape"), (0, "back")):
            rotate(args.serial, rotation)
            time.sleep(args.settle)
            base.screenshot(args.serial, args.out / f"wizard-{tag}-{name}.png")
            top = resumed_activity(args.serial)
            same = app_pid(args.serial) == pid and pid != ""
            ok = top.endswith(ACTIVITY.rsplit(".", 1)[1]) and same
            print(f"{'PASS' if ok else 'FAIL'} {name}: resumed {top}, process {'kept' if same else 'changed'} (pid {pid})")
            failed += not ok
    finally:
        if remembered:
            failed += open_page(args, remembered={"restore": original})[0]
        for name, value in saved.items():
            if value and value != "null":
                base.adb(args.serial, "shell", "settings", "put", "system", name, value)
    return failed


def collect_check(args, tag):
    base.screenshot(args.serial, args.out / f"collect-{tag}.png")
    listing = base.adb(args.serial, "shell", "ls", "-t", "/sdcard/Download/").stdout.split()
    names = [n for n in listing if n.startswith("autojs6-settings-") and n.endswith(".zip")]
    if not names:
        print("FAIL no autojs6-settings-*.zip in /sdcard/Download")
        return 1
    local = args.out / f"{args.serial}-{names[0]}"
    local.parent.mkdir(parents=True, exist_ok=True)
    base.adb(args.serial, "pull", f"/sdcard/Download/{names[0]}", str(local), check=True)
    print(f"INFO pulled {names[0]} ({local.stat().st_size} bytes)")
    ids = [i.strip() for i in args.expect_ids.split(",") if i.strip()] if args.expect_ids else None
    inspector = archive_inspect.Inspector()
    result = inspector.inspect(local, args.expect_categories, ids)
    if result:
        size, manifest, rows = result
        host, device = manifest.get("host") or {}, manifest.get("device") or {}
        settings = [row for row in rows if not isinstance(row[3], str)]
        print(f"INFO manifest v{manifest.get('formatVersion')}, {manifest.get('exportedAt')}, host {host.get('versionCode')}, "
              f"device {device.get('model')} API {device.get('sdk')}")
        print(f"INFO categories {len(settings)}, entries {sum(row[1] or 0 for row in settings)}, "
              f"sensitive {[row[0] for row in settings if row[4]]}, data {len(rows) - len(settings)}")
    for problem in inspector.problems:
        print(f"FAIL {problem}")
    print(f"{'FAIL' if inspector.problems else 'PASS'} archive structure ({len(inspector.problems)} problems)")
    return len(inspector.problems)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("check", choices=("page", "rotate", "wizard", "collect"))
    parser.add_argument("--out", type=pathlib.Path, required=True, help="directory of the screenshots")
    parser.add_argument("--install", type=pathlib.Path, help="APK to install with adb install -r -d before the check")
    parser.add_argument("--locale", help="language tag of the app during the check, e.g. en-US or zh-CN")
    parser.add_argument("--remembered", help="rotate: category ids of the remembered selection (P6.3)")
    parser.add_argument("--expect-categories", type=int, help="collect: number of categories the manifest must list")
    parser.add_argument("--expect-ids", help="collect: category ids the manifest must list, comma separated, in order")
    parser.add_argument("--wait", type=int, default=60, help="seconds to wait for the in-app script (default 60)")
    parser.add_argument("--settle", type=float, default=3.0, help="seconds before each screenshot (default 3)")
    args = parser.parse_args()

    if args.install:
        result = base.adb(args.serial, "install", "-r", "-d", str(args.install))
        if "Success" not in result.stdout:
            sys.exit(f"install failed: {(result.stdout + result.stderr).strip()[-300:]}")
        print(f"installed {args.install.name}")
    model = base.adb(args.serial, "shell", "getprop", "ro.product.model").stdout.strip()
    sdk = base.adb(args.serial, "shell", "getprop", "ro.build.version.sdk").stdout.strip()
    print(f"device {args.serial}: {model}, API {sdk}, host versionCode {base.host_version(args.serial)}")

    previous = app_locales(args.serial)
    if args.locale:
        set_app_locales(args.serial, args.locale)
        time.sleep(1)
    tag = args.locale or previous or "system"
    failed = 0
    try:
        if args.check == "page":
            base.adb(args.serial, "shell", "am", "force-stop", base.PACKAGE, check=True)
            failed += open_page(args)[0]
            time.sleep(args.settle)
            base.screenshot(args.serial, args.out / f"page-{tag}.png")
        elif args.check == "wizard":
            base.adb(args.serial, "shell", "am", "force-stop", base.PACKAGE, check=True)
            failed += open_page(args, wizard="export")[0]
            time.sleep(args.settle)
            base.screenshot(args.serial, args.out / f"wizard-{tag}.png")
        elif args.check == "collect":
            failed += collect_check(args, tag)
        else:
            failed += rotate_check(args, tag)
    finally:
        if args.locale:
            set_app_locales(args.serial, previous)
    print(f"{args.check}: {'FAIL' if failed else 'PASS'} ({failed} failed)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
