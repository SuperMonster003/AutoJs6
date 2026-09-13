"""Run the SDK 37 Looper probes on an explicitly selected, disposable emulator."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('serial', help='Serial of a disposable emulator, e.g. emulator-5560')
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--output', type=Path, required=True, help='A new evidence directory')
    parser.add_argument('--clear-interrupt', choices=['true', 'false'], required=True)
    parser.add_argument('--pool-starts', choices=['0', '1'], required=True)
    parser.add_argument('--soak-ms', type=int, default=5_000)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        parser.error('This helper rebinds accessibility and is restricted to disposable emulators')
    if not 1_000 <= args.soak_ms <= 300_000:
        parser.error('--soak-ms must be between 1000 and 300000')
    args.output.mkdir(parents=True, exist_ok=False)
    prefix = [args.adb, '-s', args.serial]
    package = 'org.autojs.autojs6'
    service = package + '/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher'

    def adb(*command, check=True):
        return subprocess.run(prefix + list(command), capture_output=True, text=True,
                              encoding='utf-8', errors='replace', timeout=60, check=check).stdout

    def set_services(value):
        # These are system component names. Validate before passing a remote shell argument.
        if value and not re.fullmatch(r'[A-Za-z0-9_.$/:]+', value):
            raise ValueError('Unexpected accessibility component name')
        if value:
            adb('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', value)
        else:
            adb('shell', 'settings', 'delete', 'secure', 'enabled_accessibility_services')

    original = adb('shell', 'settings', 'get', 'secure', 'enabled_accessibility_services').strip()
    services = '' if original == 'null' else original
    if service not in services.split(':'):
        raise SystemExit('Enable AutoJs6 accessibility on this emulator before running the probes')
    command = prefix + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                        'org.autojs.autojs.core.looper.LooperPlatformCompatibilityDeviceTest,'
                        'org.autojs.autojs.core.looper.ScriptLooperCompatibilityDeviceTest',
                        '-e', 'sdk37.clearInterrupt', args.clear_interrupt,
                        '-e', 'sdk37.poolStarts', args.pool_starts,
                        '-e', 'sdk37.soakMillis', str(args.soak_ms),
                        package + '.test/androidx.test.runner.AndroidJUnitRunner']
    (args.output / 'command.json').write_text(json.dumps(command, indent=2), encoding='utf-8')
    page_size = adb('shell', 'getconf', 'PAGE_SIZE', check=False).strip()
    if not page_size.isdigit():
        match = re.search(r'KernelPageSize:\s+(\d+)\s+kB', adb('shell', 'cat', '/proc/self/smaps'))
        page_size = str(int(match[1]) * 1024) if match else 'unknown'
    (args.output / 'device.txt').write_text(adb('shell', 'getprop', 'ro.build.fingerprint') +
                                           adb('shell', 'getprop', 'ro.build.version.sdk') +
                                           page_size + '\n', encoding='utf-8')
    (args.output / 'package.txt').write_text(adb('shell', 'dumpsys', 'package', package), encoding='utf-8')
    (args.output / 'platform-compat.txt').write_text(adb('shell', 'dumpsys', 'platform_compat'), encoding='utf-8')
    # Keep earlier device logs intact; collect from this point using the device's own clock.
    since = adb('shell', 'date', '"+%m-%d %H:%M:%S.000"').strip()
    log_path = args.output / 'instrumentation.log'
    with log_path.open('w', encoding='utf-8') as output:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 30
            while process.poll() is None and time.monotonic() < deadline:
                if 'INSTRUMENTATION_STATUS_CODE: 1' in log_path.read_text(encoding='utf-8'):
                    # Instrumentation restarts the app process. Rebind only AutoJs6 after
                    # it starts; retain every other enabled service and restore in finally.
                    try:
                        set_services(':'.join(item for item in services.split(':') if item != service))
                        time.sleep(0.2)
                    finally:
                        set_services(services)
                    break
                time.sleep(0.1)
            exit_code = process.wait(timeout=args.soak_ms / 1_000 + 180)
        finally:
            if process.poll() is None:
                adb('shell', 'am', 'force-stop', package, check=False)
                process.terminate()
                process.wait(timeout=10)
    log = log_path.read_text(encoding='utf-8')
    (args.output / 'logcat.txt').write_text(adb('logcat', '-d', '-v', 'threadtime', '-T', since), encoding='utf-8')
    codes = [int(code) for code in re.findall(r'^INSTRUMENTATION_STATUS_CODE: (-?\d+)', log, re.M)]
    success = exit_code == 0 and codes.count(0) == 14 and all(code in (0, 1) for code in codes)
    result = {'success': success, 'testsExpected': 14, 'statusCodes': codes, 'exitCode': exit_code,
              'clearInterrupt': args.clear_interrupt, 'poolStarts': args.pool_starts, 'soakMillis': args.soak_ms}
    (args.output / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result))
    return 0 if success else 1


if __name__ == '__main__':
    raise SystemExit(main())
