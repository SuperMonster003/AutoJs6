#!/usr/bin/env python3
"""Run the Android XML attribute coverage test on one device and report the matrix.

Host roadmap docs/dev/android-xml-attributes-roadmap.md, A5. The device test
`AndroidAttributeCoverageDeviceTest` puts every attribute of the metadata table (`app/src/main/assets/ui/
android-attrs.json`) in one category (handler / setter / params / design / unsupported / api) the way the inflater
decides at runtime, writes the matrix to the app's files directory and compares it with the snapshot
`app/src/androidTest/assets/ui/android-attrs-coverage.json`. This script runs the test, pulls the matrix and prints
the totals and the differences from the snapshot; `--update` writes the matrix as the new snapshot (only from a
device on the snapshot's API level, the newest platform, unless `--any-api`). Rebuild the androidTest APK after an
update so the test compares with it.

Usage:
  python .python/android_attrs_coverage.py --serial emulator-5592 [--install] [--update [--any-api]]

`--install` installs the host debug APK of the device's ABI and the androidTest APK from app/build/outputs first.
Output: PASS / FAIL of the test, the device API, totals per category, skipped classes and changed categories.
"""
from __future__ import annotations

import argparse
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SNAPSHOT = ROOT / "app" / "src" / "androidTest" / "assets" / "ui" / "android-attrs-coverage.json"
PACKAGE = "org.autojs.autojs6"
RUNNER = "org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner"
TEST = "org.autojs.autojs.core.ui.inflater.AndroidAttributeCoverageDeviceTest"
OUTPUT = "files/android-attrs-coverage.json"
SNAPSHOT_API = 37
SECTIONS = ("views", "layouts")


def adb(serial: str, *args: str) -> bytes:
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, check=False).stdout


def install(serial: str) -> None:
    abi = adb(serial, "shell", "getprop", "ro.product.cpu.abi").decode().strip()
    outputs = ROOT / "app" / "build" / "outputs" / "apk"
    apks = sorted((outputs / "app" / "debug").glob(f"autojs6-v*-{abi}.apk")) or sorted((outputs / "app" / "debug").glob("autojs6-v*-universal.apk"))
    if not apks:
        sys.exit(f"no host debug APK for {abi}; build :app:assembleAppDebug first")
    for apk, flags in ((apks[-1], ["-r", "-d", "-t"]), (outputs / "androidTest" / "app" / "debug" / "app-app-debug-androidTest.apk", ["-r", "-t"])):
        result = adb(serial, "install", *flags, str(apk)).decode(errors="replace").strip().splitlines()
        print(f"install {apk.name}: {result[-1] if result else '?'}")


def categories(matrix: dict) -> dict[str, str]:
    result = {}
    for section in SECTIONS:
        for class_name, groups in matrix.get(section, {}).items():
            for category, keys in groups.items():
                for key in keys:
                    result[f"{section} {class_name} {key}"] = category
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--install", action="store_true")
    parser.add_argument("--update", action="store_true")
    parser.add_argument("--any-api", action="store_true")
    args = parser.parse_args()

    if args.install:
        install(args.serial)
    adb(args.serial, "shell", "run-as", PACKAGE, "rm", "-f", OUTPUT)
    out = adb(args.serial, "shell", "am", "instrument", "-w", "-e", "class", TEST, RUNNER).decode(errors="replace")
    passed = "OK (1 test)" in out
    print("test:", "PASS" if passed else "FAIL")
    if not passed:
        lines = [line.strip() for line in out.splitlines()]
        start = next((i for i, line in enumerate(lines) if "AssertionError" in line or "Exception" in line), None)
        for line in lines[start:start + 12] if start is not None else lines[-6:]:
            if line and not line.startswith("at "):
                print("  " + line)

    raw = adb(args.serial, "exec-out", "run-as", PACKAGE, "cat", OUTPUT)
    try:
        matrix = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        print("matrix: not written")
        return 1
    print("api:", matrix["api"])
    for section in SECTIONS:
        totals = matrix["totals"].get(section, {})
        print(f"{section}: " + ", ".join(f"{name} {totals[name]}" for name in sorted(totals)) + f" ({len(matrix[section])} classes)")
    skipped = matrix.get("skipped", {})
    print(f"skipped: {len(skipped)}" + (" (" + ", ".join(f"{k.rsplit('.', 1)[-1]}: {v}" for k, v in sorted(skipped.items())) + ")" if skipped else ""))

    if SNAPSHOT.is_file():
        before = categories(json.loads(SNAPSHOT.read_text(encoding="utf-8")))
        now = categories(matrix)
        changed = sorted(key for key in before.keys() & now.keys() if before[key] != now[key] and now[key] != "api")
        print(f"changed from snapshot (api {json.loads(SNAPSHOT.read_text(encoding='utf-8'))['api']}): {len(changed)}")
        for key in changed[:20]:
            print(f"  {key}: {before[key]} -> {now[key]}")
    else:
        print("snapshot: none")

    if args.update:
        if matrix["api"] != SNAPSHOT_API and not args.any_api:
            print(f"not updated: the snapshot is made on API {SNAPSHOT_API}")
            return 1
        SNAPSHOT.parent.mkdir(parents=True, exist_ok=True)
        SNAPSHOT.write_text(json.dumps(matrix, indent=1, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
        print(f"snapshot: written ({SNAPSHOT.stat().st_size} B)")
    return 0 if passed or args.update else 1


if __name__ == "__main__":
    sys.exit(main())
