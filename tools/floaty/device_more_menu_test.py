"""Device checks for the floating button "More" menu: the dialog shows its final subtitles in the first frame, also on the
first opening after the host process starts, and the top activity asked through the Shizuku server matches the user
service.

Usage: python tools/floaty/device_more_menu_test.py SERIAL [--no-install] [--repeat N]
Builds are not made here: run ./gradlew :app:assembleAppDebug :app:assembleAppDebugAndroidTest first. am instrument
force-stops the host, so its accessibility service, if enabled, is bound again at the end. Prints PASS / FAIL per test
class and the "first opening" / "second opening" timings that CircularMenuMoreDialogInstrumentationTest logs.
"""
import argparse
import re
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
APP_DIR = REPO / 'app/build/outputs/apk/app/debug'
TEST_APK = REPO / 'app/build/outputs/apk/androidTest/app/debug/app-app-debug-androidTest.apk'
PKG = 'org.autojs.autojs6'
RUNNER = 'org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner'
CLASSES = [
    'org.autojs.autojs.ui.floating.CircularMenuMoreDialogInstrumentationTest',
    'org.autojs.autojs.app.CircularMenuOperationDialogInstrumentationTest',
    'org.autojs.autojs.runtime.api.WrappedShizukuTopActivityInstrumentationTest',
]


def adb(serial, *args, check=True):
    result = subprocess.run(['adb', '-s', serial, *args], capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=600)
    if check and result.returncode != 0:
        raise SystemExit(f"adb {' '.join(args[:3])} failed: {(result.stdout + result.stderr).strip()[-400:]}")
    return result.stdout.replace('\r', '')


def install(serial):
    abi = adb(serial, 'shell', 'getprop', 'ro.product.cpu.abi').strip()
    apks = sorted(APP_DIR.glob(f'autojs6-v*-{abi}.apk')) or sorted(APP_DIR.glob('autojs6-v*-universal.apk'))
    if not apks:
        raise SystemExit(f'No host APK in {APP_DIR}')
    for label, apk in (('host', apks[-1]), ('test', TEST_APK)):
        out = adb(serial, 'install', '-r', '-t', '-d', str(apk), check=False)
        print(f"install {label} ({apk.name}): {'PASS' if 'Success' in out else 'FAIL ' + out.strip()[-200:]}")


def rebind_accessibility(serial, enabled):
    if PKG + '/' not in enabled:
        return
    without = ':'.join(item for item in enabled.split(':') if not item.startswith(PKG + '/'))
    adb(serial, 'shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', without or 'null', check=False)
    time.sleep(1)
    adb(serial, 'shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', enabled, check=False)
    restored = adb(serial, 'shell', 'settings', 'get', 'secure', 'enabled_accessibility_services').strip() == enabled
    print(f"accessibility service bound again: {'PASS' if restored else 'FAIL'}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('serial')
    parser.add_argument('--no-install', action='store_true')
    parser.add_argument('--repeat', type=int, default=1)
    args = parser.parse_args()
    serial = args.serial

    if not args.no_install:
        install(serial)
    sdk = adb(serial, 'shell', 'getprop', 'ro.build.version.sdk').strip()
    shizuku = 'shizuku_server' in adb(serial, 'shell', 'ps -A', check=False)
    print(f'API {sdk}, Shizuku server running: {shizuku}')

    enabled = adb(serial, 'shell', 'settings', 'get', 'secure', 'enabled_accessibility_services').strip()
    failures = 0
    try:
        for round_index in range(args.repeat):
            for cls in CLASSES:
                name = cls.rsplit('.', 1)[-1]
                adb(serial, 'logcat', '-c', check=False)
                out = adb(serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', cls, RUNNER, check=False)
                ok = re.search(r'OK \((\d+) tests?\)', out)
                skipped = len(re.findall(r'INSTRUMENTATION_STATUS_CODE: -4', out))
                if ok:
                    print(f'[{round_index + 1}] {name}: PASS {ok.group(1)} tests' + (f', {skipped} skipped' if skipped else ''))
                else:
                    failures += 1
                    stack = re.findall(r'INSTRUMENTATION_STATUS: stack=([^\n]+)', out)
                    print(f'[{round_index + 1}] {name}: FAIL {(stack[0] if stack else out.strip())[-300:]}')
                for line in adb(serial, 'logcat', '-d', '-s', 'CircularMenuMoreDialogInstrumentationTest:I', check=False).splitlines():
                    match = re.search(r'(first|second) opening: .*', line)
                    if match:
                        print('    ' + match.group(0))
    finally:
        rebind_accessibility(serial, enabled)
    sys.exit(1 if failures else 0)


if __name__ == '__main__':
    main()
