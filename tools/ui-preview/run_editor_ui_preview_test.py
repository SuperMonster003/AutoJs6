"""Run EditorUiPreviewDeviceTest (the layout preview panel of "ui" scripts, 3-Block Compose P11.10) on one device.

Installs the given APKs first when asked, runs the instrumented test class, pulls the screenshots it took into
--output, and prints one line per test plus the step markers the test logged; nothing else of the device logs.

Usage (the APK options are optional when both are installed already):
  python tools/ui-preview/run_editor_ui_preview_test.py emulator-5554
      --app-apk app/build/outputs/apk/app/debug/<app>.apk
      --test-apk app/build/outputs/apk/androidTest/app/debug/<test>.apk
      --output <new evidence dir>
"""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

TEST_CLASS = 'org.autojs.autojs.ui.edit.editor.EditorUiPreviewDeviceTest'
LOG_TAG = 'EditorUiPreviewTest'
SCREENSHOT_DIR = 'editor-ui-preview-test'


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('serial', help='adb serial of the device')
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--package', default='org.autojs.autojs6')
    parser.add_argument('--app-apk', type=Path, help='install this app APK first')
    parser.add_argument('--test-apk', type=Path, help='install this test APK first')
    parser.add_argument('--output', type=Path, help='a new directory for the screenshots and result.json')
    parser.add_argument('--timeout', type=int, default=600, help='seconds the test run may take')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9._:-]+', args.package):
        parser.error('unexpected package name')
    if args.output and args.output.exists():
        parser.error(f'{args.output} exists already')
    prefix = [args.adb, '-s', args.serial]

    def adb(*command, check=True, timeout=120):
        return subprocess.run(prefix + list(command), capture_output=True, text=True, encoding='utf-8',
                              errors='replace', timeout=timeout, check=check).stdout

    # The screenshots are in the files dir of the (debuggable) app, which only run-as can reach.
    def run_as(*command):
        return adb('shell', 'run-as', args.package, *command, check=False)

    for apk in (args.app_apk, args.test_apk):
        if apk:
            result = subprocess.run(prefix + ['install', '-r', '-t', str(apk)], capture_output=True, text=True,
                                    encoding='utf-8', errors='replace', timeout=300)
            output = result.stdout + result.stderr
            failure = re.search(r'Failure \[[^\]]*\]', output)
            print(f'install {apk.name}: ' + ('ok' if 'Success' in output else failure[0] if failure else 'FAILED'))
            if 'Success' not in output:
                return 1

    if args.output:
        args.output.mkdir(parents=True)
    sdk = adb('shell', 'getprop', 'ro.build.version.sdk').strip()
    device_dir = f'files/{SCREENSHOT_DIR}'
    run_as('rm', '-rf', device_dir)
    adb('logcat', '-c', check=False)
    runner = f'{args.package}.test/androidx.test.runner.AndroidJUnitRunner'
    log = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', TEST_CLASS, runner,
              check=False, timeout=args.timeout)

    # Each status block ends with its code: 1 when a test starts, 0 when it passed, negative when it failed.
    parts = re.split(r'^INSTRUMENTATION_STATUS_CODE: (-?\d+)[ \t]*$', log, flags=re.M)
    results = []
    for block, code in zip(parts[0::2], parts[1::2]):
        name = re.findall(r'INSTRUMENTATION_STATUS: test=(\S+)', block)
        if not name or int(code) == 1:
            continue
        stack = re.search(r'INSTRUMENTATION_STATUS: stack=(.*)', block)
        failure = stack[1].strip()[:300] if stack and int(code) != 0 else ''
        results.append({'test': name[-1], 'passed': int(code) == 0, 'failure': failure})
    finished = re.search(r'^OK \((\d+) tests?\)', log, re.M)

    steps = [line.split(f'{LOG_TAG}: ', 1)[1].strip()
             for line in adb('logcat', '-d', '-s', f'{LOG_TAG}:I', check=False).splitlines() if f'{LOG_TAG}: ' in line]

    screenshots = []
    if args.output:
        for name in re.findall(r'^([\w-]+\.png)$', run_as('ls', device_dir), re.M):
            target = args.output / f'api{sdk}-{name}'
            png = subprocess.run(prefix + ['exec-out', 'run-as', args.package, 'cat', f'{device_dir}/{name}'],
                                 capture_output=True, timeout=120).stdout
            if png.startswith(b'\x89PNG'):
                target.write_bytes(png)
                screenshots.append(str(target))
    run_as('rm', '-rf', device_dir)

    passed = bool(finished and results and all(item['passed'] for item in results))
    for item in results:
        print(f"{'PASS' if item['passed'] else 'FAIL'} {item['test']}" + (f": {item['failure']}" if item['failure'] else ''))
    for step in steps:
        print(f'  step {step}')
    print(f'api {sdk}: {sum(item["passed"] for item in results)}/{len(results)} passed, '
          f'{len(screenshots)} screenshots -> {"PASS" if passed else "FAIL"}')
    if args.output:
        result = {'serial': args.serial, 'sdk': sdk, 'passed': passed, 'tests': results, 'steps': steps,
                  'screenshots': screenshots}
        (args.output / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    return 0 if passed else 1


if __name__ == '__main__':
    sys.exit(main())
