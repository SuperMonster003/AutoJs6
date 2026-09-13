"""Submit/inspect an install through the app UID on a disposable emulator, preserving pending sessions.

This helper never treats session submission as installation success. Inspect the recorded callback,
system confirmation UI and installed package separately. It never force-stops the app or fixture.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--package', choices=('org.autojs.autojs6', 'org.autojs.autojs6.inrt'), default='org.autojs.autojs6')
    parser.add_argument('--route', choices=('install', 'install-ui', 'install-recovery'), required=True)
    parser.add_argument('--apk-name', default='sdk37-bal-install.apk', help='Existing APK filename in the app files directory')
    parser.add_argument('--background', action='store_true', help='Return to Home before the delayed submission')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--timeout', type=int, default=90)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        parser.error('Use a disposable emulator')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+\.apk', args.apk_name):
        parser.error('--apk-name must be an APK filename without directories')
    if args.background and args.route != 'install':
        parser.error('--background is supported for install submission only')
    if not 30 <= args.timeout <= 300:
        parser.error('--timeout must be between 30 and 300 seconds')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    prefix = [args.adb, '-s', args.serial]

    def adb(*command, check=True):
        result = subprocess.run(prefix + list(command), capture_output=True, text=True,
                                encoding='utf-8', errors='replace', timeout=30)
        if check and result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        return result.stdout + result.stderr

    package = adb('shell', 'dumpsys', 'package', args.package)
    if not re.search(r'(?:pkgFlags|flags)=\[[^\]]*\bDEBUGGABLE\b', package):
        raise RuntimeError('The installed app must be debuggable')
    (output / 'package-before.txt').write_text(package, encoding='utf-8')
    case = f'recovery_{time.time_ns()}'
    command = ['shell', 'am', 'start', '-n',
               f'{args.package}/org.autojs.autojs.compat.ActivityLaunchCompatibilityProbeActivity',
               '-f', '0x18000000', '--es', 'case', case, '--es', 'route', args.route,
               '--es', 'apkName', args.apk_name, '--ez', 'background', str(args.background).lower()]
    report = {'case': case, 'command': prefix + command, 'startedAt': time.time()}
    try:
        (output / 'launch.txt').write_text(adb(*command), encoding='utf-8')
        deadline = time.monotonic() + args.timeout
        sent_home = False
        while time.monotonic() < deadline:
            raw = adb('shell', 'run-as', args.package, 'cat', f'files/sdk37-bal-{case}.json', check=False)
            try:
                result = json.loads(raw)
            except ValueError:
                time.sleep(1)
                continue
            if args.background and not sent_home:
                # moveTaskToBack() can reveal another app task from an earlier recovery.
                adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
                sent_home = True
            report['probe'] = result
            if result.get('completed'):
                if result.get('case') != case or result.get('route') != args.route:
                    raise RuntimeError('Probe identity mismatch')
                if any(key in result for key in ('error', 'installError', 'fixtureReadError')):
                    raise RuntimeError('Probe failed; inspect its result')
                if args.route == 'install' and not isinstance(result.get('installSession'), int):
                    raise RuntimeError('No submitted installation session')
                if args.route == 'install-ui' and not result.get('installUiStarted'):
                    raise RuntimeError('Installation UI did not start')
                if args.route == 'install-recovery' and not isinstance(result.get('installations'), list):
                    raise RuntimeError('No installation state snapshot')
                if args.background and (result.get('windowFocusAtSend') or result.get('sendImportance', 0) < 200):
                    raise RuntimeError('The app was not in the background at submission')
                report['probeCompleted'] = True
                break
            time.sleep(1)
        else:
            raise TimeoutError('Probe did not complete')
    except Exception as error:
        report['failure'] = repr(error)
        raise
    finally:
        report['ledger'] = adb('shell', 'run-as', args.package, 'cat', 'no_backup/package-install-status.json', check=False)
        (output / 'result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        for name, command in [('activities', ('activity', 'activities')), ('intents', ('activity', 'intents')),
                              ('notifications', ('notification', '--noredact')), ('compat', ('platform_compat',))]:
            (output / f'{name}.txt').write_text(adb('shell', 'dumpsys', *command, check=False), encoding='utf-8')
        (output / 'logcat.txt').write_text(adb('logcat', '-d', '-v', 'threadtime',
            'PackageInstallStatus:V', 'ActivityTaskManager:I', 'AndroidRuntime:E', '*:S', check=False), encoding='utf-8')
    print(json.dumps({'case': case, 'probeCompleted': True, 'installationSuccess': 'not asserted'}))


if __name__ == '__main__':
    main()
