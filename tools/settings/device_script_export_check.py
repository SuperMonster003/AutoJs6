"""Device check of the script API settings.export / settings.categories (docs/settings/ROADMAP.md P12.1, T15, E3).

Usage: py -3.12 -I tools/settings/device_script_export_check.py -s SERIAL --out DIR [--wait SECONDS]

device/script_export_check.js, run inside AutoJs6 as a script, lists the categories; exports with an explicit path and
credentials requested (they must be skipped), with the defaults (the backup directory of the working directory), to a
relative directory and over an existing file; and checks that bad arguments and calls on the UI thread are refused. The
explicit and the default archives are pulled to DIR and checked with archive_inspect.py: no problems, no credentials,
the categories and entries the script reported. Every file the check wrote on the device is removed afterwards (the
backup directories too when the check created them). Nothing has to be confirmed on the device. Output is one line per
check, ids, paths and counts only (R6); the exit status is 1 when a check fails.
"""
import argparse
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import archive_inspect  # noqa: E402
import device_store_check as base  # noqa: E402

REMOTE_DIR = "/sdcard/Download/aj6-script-export-check"


def inspect(local, reported, entries):
    inspector = archive_inspect.Inspector()
    result = inspector.inspect(local)
    for problem in inspector.problems:
        print(f"FAIL {local.name}: {problem}")
    print(f"{'FAIL' if inspector.problems else 'PASS'} {local.name} structure ({len(inspector.problems)} problems)")
    failed = len(inspector.problems)
    if not result:
        return failed + 1
    _, _, rows = result
    ids = [row[0] for row in rows]
    clean = "credentials" not in ids and not any(row[4] for row in rows)
    print(f"{'PASS' if clean else 'FAIL'} {local.name} no credentials ({len(rows)} categories)")
    same = sorted(ids) == sorted(filter(None, reported.split(",")))
    print(f"{'PASS' if same else 'FAIL'} {local.name} categories as reported ({len(ids)} in the archive)")
    listed = sum(row[1] or 0 for row in rows)
    counted = str(listed) == entries
    print(f"{'PASS' if counted else 'FAIL'} {local.name} entries as reported ({listed}/{entries})")
    return failed + (not clean) + (not same) + (not counted)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--out", type=pathlib.Path, required=True)
    parser.add_argument("--wait", type=int, default=90)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    serial = args.serial

    print(f"device {serial}: {base.adb(serial, 'shell', 'getprop', 'ro.product.model').stdout.strip()}, host versionCode {base.host_version(serial)}")
    base.adb(serial, "shell", "rm", "-rf", REMOTE_DIR)
    explicit = f"{REMOTE_DIR}/explicit.zip"
    script = (HERE / "device" / "script_export_check.js").read_text(encoding="utf-8")
    lines = base.run_script(serial, f"var AJ6_PATH = {json.dumps(explicit)};" + chr(10) + script, args.wait)

    failed = 0
    info = {}
    for line in lines:
        kind, _, rest = line.partition(" ")
        if kind in ("PASS", "FAIL"):
            print(f"{kind} {rest}")
            failed += kind == "FAIL"
        elif kind == "INFO":
            name, _, value = rest.partition(" ")
            info[name] = value
            print(f"INFO {name} {value}")
    if "done" not in lines:
        print("FAIL the script did not finish (see logcat GlobalConsole)")
        failed += 1

    default = info.get("default-path")
    for name, remote in (("explicit", explicit), ("default", default)):
        if not remote:
            print(f"FAIL no {name} archive reported")
            failed += 1
            continue
        local = args.out / f"{serial}-{name}.zip"
        pulled = base.adb(serial, "pull", remote, str(local))
        if pulled.returncode != 0:
            print(f"FAIL cannot pull the {name} archive")
            failed += 1
            continue
        failed += inspect(local, info.get(f"{name}-categories", ""), info.get(f"{name}-entries", ""))

    base.adb(serial, "shell", "rm", "-rf", REMOTE_DIR)
    if default:
        base.adb(serial, "shell", "rm", "-f", default)
    for directory in filter(lambda d: d and d != "-", info.get("default-created", "-").split(",")):
        base.adb(serial, "shell", "rmdir", directory)
    left = [path for path in (REMOTE_DIR, default) if path and base.adb(serial, "shell", "ls", path).returncode == 0]
    print(f"{'FAIL' if left else 'PASS'} cleanup ({len(left)} paths left)")
    failed += bool(left)
    print(f"script export: {'FAIL' if failed else 'PASS'} ({failed} failed)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
