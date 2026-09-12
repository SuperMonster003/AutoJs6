"""Exercise the public OCR API on a test device and restore Plugin Center enablement.

Install trusted OCR plugins before running. --require-all checks ML Kit/Paddle/Rapid.
The optional hot-uninstall probe is only for a disposable test device: it removes
and reinstalls the explicitly named Paddle fixture while the same script runs.
"""
import argparse
import json
import shlex
import subprocess
import sys
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('serial')
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--require-all', action='store_true')
    parser.add_argument('--hot-package')
    parser.add_argument('--hot-apk', type=Path)
    parser.add_argument('--out', type=Path, default=Path('build/tmp/ocr-auto'))
    args = parser.parse_args()
    if bool(args.hot_package) != bool(args.hot_apk):
        parser.error('--hot-package and --hot-apk must be supplied together')
    if args.hot_apk and not args.hot_apk.is_file():
        parser.error('--hot-apk must name an existing fixture APK')
    receipt = f'ocr-auto-{args.serial}-{time.time_ns()}.json'
    remote = f'/sdcard/Android/data/org.autojs.autojs6/files/{receipt}'
    script = Path(__file__).with_suffix('.js').read_text(encoding='utf-8')
    script = script.replace('__RECEIPT__', receipt).replace('__REQUIRE_ALL__', str(args.require_all).lower())
    script = script.replace('__HOT_SWAP__', str(bool(args.hot_apk)).lower())

    def adb(*command, check=True, timeout=60):
        result = subprocess.run([args.adb, '-s', args.serial, *command], capture_output=True,
                                text=True, encoding='utf-8', errors='replace', timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        return result.stdout

    print(adb('shell', shlex.join(['am', 'start', '-n',
          'org.autojs.autojs6/org.autojs.autojs.external.open.RunIntentActivity', '--es', 'script', script])), flush=True)
    removed = False
    reinstalled = False
    result = None
    try:
        deadline = time.monotonic() + 300
        while time.monotonic() < deadline:
            raw = adb('shell', 'cat', remote, check=False)
            try:
                result = json.loads(raw)
            except json.JSONDecodeError:
                time.sleep(1)
                continue
            if args.hot_apk and result.get('phase') == 'hot-swap-ready' and not removed:
                adb('uninstall', args.hot_package)
                removed = True
            if removed and not reinstalled and result.get('phase') == 'rapid-after-uninstall':
                adb('install', '-r', str(args.hot_apk))
                reinstalled = True
            if result.get('complete'):
                break
            time.sleep(1)
        else:
            raise TimeoutError('OCR auto mode smoke did not complete')
    finally:
        if removed and not reinstalled:
            adb('install', '-r', str(args.hot_apk))
        args.out.mkdir(parents=True, exist_ok=True)
        if result:
            (args.out / receipt).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=True, indent=2), flush=True)
    if not result or not result.get('ok'):
        raise RuntimeError('OCR automatic mode smoke failed')
    print(f'PASS: {len(result["checks"])} OCR automatic-mode checks', flush=True)


if __name__ == '__main__':
    main()
