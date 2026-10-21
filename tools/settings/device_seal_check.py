"""Device check of the sealed credentials (docs/settings/ROADMAP.md P6.5, D23 / T21, E3).

Usage: py -3.12 -I tools/settings/device_seal_check.py -s SERIAL [-s SERIAL] --out DIR [--wait SECONDS]

On every device, device/seal_check.js (run inside AutoJs6) captures the credentials and appearance categories, writes
them as the export wizard does (SettingsExporter with the device key of the app) and reads the archive back with that
key: both categories must read, with the entries the manifest lists. The archive is pulled to DIR/<serial>.zip and
checked with archive_inspect.py (credentials sealed, structure). With two devices, each archive is then read on the
other device: its credentials must be LOCKED (another ANDROID_ID, another key) while its appearance category reads.
The archives on the devices are removed afterwards. Output is one line per check, counts only (R6).
"""
import argparse
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import archive_inspect  # noqa: E402
import device_store_check as base  # noqa: E402

REMOTE_DIR = "/sdcard/Download/aj6-seal-check"


def run(args, serial, mode, path):
    script = (HERE / "device" / "seal_check.js").read_text(encoding="utf-8")
    prefix = f"var AJ6_MODE = '{mode}'; var AJ6_PATH = '{path}';"
    lines = base.run_script(serial, prefix + chr(10) + script, args.wait)
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


def inspect(local):
    inspector = archive_inspect.Inspector()
    result = inspector.inspect(local)
    if result:
        _, _, rows = result
        sealed = [row[0] for row in rows if row[3] is None]
        print(f"INFO {local.name}: {len(rows)} categories, sealed {sealed}")
    for problem in inspector.problems:
        print(f"FAIL {local.name}: {problem}")
    print(f"{'FAIL' if inspector.problems else 'PASS'} {local.name} structure ({len(inspector.problems)} problems)")
    return len(inspector.problems)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", action="append", required=True)
    parser.add_argument("--out", type=pathlib.Path, required=True)
    parser.add_argument("--wait", type=int, default=60)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    failed = 0
    for serial in args.serial:
        print(f"device {serial}: {base.adb(serial, 'shell', 'getprop', 'ro.product.model').stdout.strip()}, host versionCode {base.host_version(serial)}")
        remote = f"{REMOTE_DIR}/own.zip"
        failed += run(args, serial, "own", remote)
        local = args.out / f"{serial}.zip"
        base.adb(serial, "pull", remote, str(local), check=True)
        failed += inspect(local)
    if len(args.serial) >= 2:
        for index, serial in enumerate(args.serial):
            source = args.serial[index - 1]
            remote = f"{REMOTE_DIR}/from-{source}.zip"
            base.adb(serial, "push", str(args.out / f"{source}.zip"), remote, check=True)
            print(f"INFO reading the archive of {source} on {serial}")
            failed += run(args, serial, "other", remote)
    for serial in args.serial:
        base.adb(serial, "shell", "rm", "-rf", REMOTE_DIR)
    print(f"seal: {'FAIL' if failed else 'PASS'} ({failed} failed)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
