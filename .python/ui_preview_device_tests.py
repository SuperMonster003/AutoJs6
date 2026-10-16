#!/usr/bin/env python3
"""Run the ui-mode preview service device tests on one device and report each test.

3-Block Compose roadmap P11.5 (preview contract v4). Runs `UiPreviewServiceTest` (or the methods given with
`--method`) through `am instrument -r` and prints one line per test: PASS / FAIL / SKIP with its time, and for a
failure the first lines of its stack. `--install` installs the host debug APK of the device's ABI and the
androidTest APK from app/build/outputs first.

Usage:
  python .python/ui_preview_device_tests.py --serial emulator-5584 [--install] [--method name ...]

Exit code: 0 when no test failed.
"""
from __future__ import annotations

import argparse
import pathlib
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
RUNNER = "org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner"
TEST = "org.autojs.autojs.core.ui.preview.UiPreviewServiceTest"
# am instrument status codes: 1 start, 0 ok, -1 error, -2 failure, -3 ignored, -4 assumption failure.
RESULTS = {"0": "PASS", "-1": "FAIL", "-2": "FAIL", "-3": "SKIP", "-4": "SKIP"}


def adb(serial: str, *args: str, timeout: float | None = None) -> str:
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, check=False, timeout=timeout).stdout.decode("utf-8", "replace")


def install(serial: str) -> None:
    abi = adb(serial, "shell", "getprop", "ro.product.cpu.abi").strip()
    outputs = ROOT / "app" / "build" / "outputs" / "apk"
    apks = sorted((outputs / "app" / "debug").glob(f"autojs6-v*-{abi}.apk")) or sorted((outputs / "app" / "debug").glob("autojs6-v*-universal.apk"))
    if not apks:
        sys.exit(f"no host debug APK for {abi}; build :app:assembleAppDebug first")
    for apk, flags in ((apks[-1], ["-r", "-d", "-t"]), (outputs / "androidTest" / "app" / "debug" / "app-app-debug-androidTest.apk", ["-r", "-t"])):
        lines = adb(serial, "install", *flags, str(apk)).strip().splitlines()
        print(f"install {apk.name}: {lines[-1] if lines else '?'}")


def run(serial: str, methods: list[str]) -> int:
    target = ",".join(f"{TEST}#{m}" for m in methods) if methods else TEST
    api = adb(serial, "shell", "getprop", "ro.build.version.sdk").strip()
    print(f"device {serial} API {api}: {target.replace(TEST, 'UiPreviewServiceTest')}")
    process = subprocess.Popen(
        ["adb", "-s", serial, "shell", "am", "instrument", "-w", "-r", "-e", "class", target, RUNNER],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    )
    status: dict[str, str] = {}
    stack: list[str] = []
    in_stack = False
    started = time.monotonic()
    counts = {"PASS": 0, "FAIL": 0, "SKIP": 0}
    tail: list[str] = []
    assert process.stdout is not None
    for raw in process.stdout:
        line = raw.decode("utf-8", "replace").rstrip("\r\n")
        tail = (tail + [line])[-5:]
        if line.startswith("INSTRUMENTATION_STATUS: "):
            in_stack = False
            key, _, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            status[key] = value
            if key == "stack":
                stack = [value]
                in_stack = True
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            in_stack = False
            code = line.split(": ", 1)[1].strip()
            if code == "1":
                started = time.monotonic()
            elif code in RESULTS:
                result = RESULTS[code]
                counts[result] += 1
                print(f"{result} {status.get('test', '?')} ({time.monotonic() - started:.1f} s)")
                if result == "FAIL" or (result == "SKIP" and code == "-4" and stack):
                    for text in [s for s in stack if s.strip()][: 6 if result == "FAIL" else 1]:
                        print("    " + text.strip()[:240])
            status = {}
            stack = []
        elif in_stack:
            stack.append(line)
    process.wait()
    print(f"total: {counts['PASS']} passed, {counts['FAIL']} failed, {counts['SKIP']} skipped")
    if not any(counts.values()):
        print("no test ran:", *tail, sep="\n    ")
        return 1
    return 1 if counts["FAIL"] else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--install", action="store_true")
    parser.add_argument("--method", nargs="*", default=[])
    args = parser.parse_args()
    if args.install:
        install(args.serial)
    return run(args.serial, args.method)


if __name__ == "__main__":
    sys.exit(main())
