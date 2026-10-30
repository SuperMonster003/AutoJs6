"""Device check of the export with every category ticked and a password (docs/settings/ROADMAP.md P6.4, P7.7, P10.2, E3).

Usage: py -3.12 -I tools/settings/device_export_check.py -s SERIAL [-s SERIAL] [--install APK] [--wait SECONDS]

On every device, device/export_check.js (run inside AutoJs6) calls SettingsExporter.export as the wizard does, without
its pages: every category of SettingCategory.ALL, a random password, the archive in the cache of the app. It reads the
archive back and deletes it: a data category without entries must be left out instead of failing the export, every
category must read with the device key and the credentials with the password alone. Nothing is tapped and the settings
are not changed. Output is one line per check, counts only (R6); the exit status is 1 when a check fails.
"""
import argparse
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import device_store_check as base  # noqa: E402


def run(args, serial):
    script = (HERE / "device" / "export_check.js").read_text(encoding="utf-8")
    lines = base.run_script(serial, script, args.wait)
    failed = 0
    for line in lines:
        kind, _, rest = line.partition(" ")
        if kind in ("PASS", "FAIL"):
            print(f"{kind} {serial} {rest}")
            failed += kind == "FAIL"
    if "done" not in lines:
        print(f"FAIL {serial} the script did not finish (see logcat GlobalConsole)")
        failed += 1
    return failed


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", action="append", required=True)
    parser.add_argument("--install", type=pathlib.Path, help="APK to install with adb install -r -d before the check")
    parser.add_argument("--wait", type=int, default=120, help="seconds to wait for the in-app script (default 120)")
    args = parser.parse_args()

    failed = 0
    for serial in args.serial:
        if args.install:
            result = base.adb(serial, "install", "-r", "-d", str(args.install))
            if "Success" not in result.stdout:
                sys.exit(f"install failed: {(result.stdout + result.stderr).strip()[-300:]}")
            print(f"installed {args.install.name} on {serial}")
        print(f"device {serial}: {base.adb(serial, 'shell', 'getprop', 'ro.product.model').stdout.strip()}, host versionCode {base.host_version(serial)}")
        failed += run(args, serial)
    print(f"export: {'FAIL' if failed else 'PASS'} ({failed} failed)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
