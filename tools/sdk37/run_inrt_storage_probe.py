"""Exercise real inrt activities and file I/O on an exclusively owned emulator.

Requires English system UI, no installed inrt, and fixtures produced by
build_inrt_storage_fixtures.py. Installs four disposable APKs, changes only their
storage grants, and leaves the last one installed for diagnosis. The owner must
discard the emulator afterward. Never use this on a physical device.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'org.autojs.autojs6.inrt'
SPLASH = 'org.autojs.autojs.inrt.SplashActivity'
LOG = 'org.autojs.autojs.inrt.LogActivity'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--owned-avd', required=True)
    parser.add_argument('--fixtures', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--target', type=int, choices=(29, 37), required=True)
    parser.add_argument('--permanent-denial', action='store_true', help='Exercise legacy Do not ask again and app settings recovery')
    parser.add_argument('--shared-file', required=True)
    args = parser.parse_args()
    assert re.fullmatch(r'emulator-\d+', args.serial)
    assert re.fullmatch(r'/sdcard/Download/autojs6-sdk37-storage-[\w-]+\.txt', args.shared_file)
    args.output.mkdir(parents=True, exist_ok=False)
    report = {'target': args.target, 'serial': args.serial, 'checks': [], 'success': False}
    transcript = []

    def adb(*command, check=True, timeout=60):
        result = subprocess.run([args.adb, '-s', args.serial, *map(str, command)],
                                capture_output=True, text=True, encoding='utf-8', timeout=timeout)
        transcript.append({'command': list(map(str, command)), 'code': result.returncode, 'stdout': result.stdout, 'stderr': result.stderr})
        if check and result.returncode:
            raise RuntimeError(transcript[-1])
        return result.stdout.strip()

    def shell(*command, **kw):
        return adb('shell', *command, **kw)

    def save():
        (args.output / 'results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        (args.output / 'commands.json').write_text(json.dumps(transcript, ensure_ascii=False, indent=2), encoding='utf-8')

    def record(name, **details):
        report['checks'].append({'name': name, **details})
        save()
        print(name, flush=True)

    def receipts():
        raw = shell('run-as', PACKAGE, 'cat', 'files/storage-result.jsonl', check=False)
        return [json.loads(line) for line in raw.splitlines() if line.startswith('{')]

    def wait_receipt(count):
        # Fresh emulator boots also initialize the script engine on first use.
        deadline = time.monotonic() + 60
        while True:
            rows = receipts()
            if len(rows) >= count:
                assert len(rows) == count, rows
                row = rows[-1]
                assert row['private'] and not row.get('error') and row['target'] == args.target, row
                if row['case'] != 'private':
                    assert row['shared'], row
                return row
            if time.monotonic() >= deadline:
                raise AssertionError(f'Expected {count} script receipts, got {rows}')
            time.sleep(.3)

    def ui(label):
        shell('uiautomator', 'dump', '/data/local/tmp/sdk37-storage-ui.xml')
        raw = shell('cat', '/data/local/tmp/sdk37-storage-ui.xml')
        (args.output / (label + '.xml')).write_text(raw, encoding='utf-8')
        return ET.fromstring(raw)

    def tap(node):
        bounds = list(map(int, re.findall(r'\d+', node.attrib['bounds'])))
        shell('input', 'tap', (bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2)
        time.sleep(.3)

    def wait_ui(label, predicate):
        deadline = time.monotonic() + 20
        while True:
            root = ui(label)
            if predicate(root):
                return root
            if time.monotonic() >= deadline:
                raise AssertionError('UI did not reach ' + label)
            time.sleep(.3)

    def by_id(root, name):
        return next(n for n in root.iter('node') if n.get('resource-id') == name)

    def prompt(label, requestable=True):
        expected = 'needs access to shared files' if requestable else 'does not declare'
        root = wait_ui(label, lambda r: any(expected in n.get('text', '') for n in r.iter('node')))
        message = by_id(root, 'android:id/message').get('text', '')
        assert ('needs access to shared files' if requestable else 'does not declare') in message, message
        assert len([n for n in root.iter('node') if n.get('resource-id') == 'android:id/button1']) == int(requestable)
        return root

    def start(activity=SPLASH, *extras):
        if activity == SPLASH:
            deadline = time.monotonic() + 15
            while SPLASH not in launcher() and time.monotonic() < deadline:
                time.sleep(.2)
            assert SPLASH in launcher(), 'Update broadcast has not restored the launcher'
        result = shell('am', 'start', '-W', '-n', PACKAGE + '/' + activity, *extras)
        assert 'Error' not in result, result

    def install(case, update=False):
        out = adb('install', *(['-r'] if update else []), args.fixtures / (case + '.apk'), timeout=120)
        assert 'Success' in out, out

    def launcher():
        return shell('cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN',
                     '-c', 'android.intent.category.LAUNCHER', PACKAGE)

    def deny():
        if api >= 30:
            shell('appops', 'set', '--uid', PACKAGE, 'MANAGE_EXTERNAL_STORAGE', 'ignore')
        else:
            for name in ('READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE'):
                shell('pm', 'revoke', PACKAGE, 'android.permission.' + name, check=False)

    try:
        assert args.owned_avd in adb('emu', 'avd', 'name').splitlines()
        assert '1' in (shell('getprop', 'ro.boot.qemu'), shell('getprop', 'ro.kernel.qemu'))
        assert not shell('pm', 'list', 'packages', PACKAGE)
        assert not shell('ls', args.shared_file, check=False)
        # The uniquely named fixture is confined to this disposable emulator.
        local = args.output / 'shared.txt'
        local.write_text('legacy-fixture', encoding='utf-8')
        adb('push', local, args.shared_file)
        api = int(shell('getprop', 'ro.build.version.sdk'))
        report.update(api=api, fingerprint=shell('getprop', 'ro.build.fingerprint'))
        install('private')
        start()
        row = wait_receipt(1)
        assert row['manager'] is not True
        record('private-without-storage-grant', receipt=row)

        install('shared', True)
        start()
        root = prompt('shared-prompt')
        assert len(receipts()) == 1
        tap(by_id(root, 'android:id/button2'))
        assert len(receipts()) == 1
        record('cancel-does-not-run')
        start()
        tap(by_id(prompt('shared-request'), 'android:id/button1'))
        system_ui = lambda r: any(n.get('package') in ('com.android.settings', 'com.google.android.packageinstaller', 'com.android.permissioncontroller') for n in r.iter('node'))
        root = wait_ui('system-permission-first', system_ui)
        if api >= 30:
            assert any(n.get('package') == 'com.android.settings' for n in root.iter('node'))
            shell('input', 'keyevent', '4')
        else:
            deny_button = next(n for n in root.iter('node') if n.get('resource-id', '').endswith('permission_deny_button'))
            tap(deny_button)
        root = prompt('returned-without-grant')
        assert len(receipts()) == 1
        record('permission-return-without-grant-does-not-run')

        tap(by_id(root, 'android:id/button1'))
        root = wait_ui('system-permission-second', system_ui)
        if api >= 30:
            switch = next(n for n in root.iter('node') if n.get('checkable') == 'true')
            assert switch.get('checked') == 'false'
            tap(switch)
            # Recreation while away must restore the pending result and launch once.
            shell('am', 'kill', PACKAGE)
            killed = not shell('pidof', PACKAGE, check=False)
            shell('input', 'keyevent', '4')
        elif args.permanent_denial:
            checkbox = next(n for n in root.iter('node') if n.get('class') == 'android.widget.CheckBox')
            tap(checkbox)
            tap(next(n for n in root.iter('node') if n.get('resource-id', '').endswith('permission_deny_button')))
            root = prompt('permanently-denied')
            assert len(receipts()) == 1
            tap(by_id(root, 'android:id/button1'))
            root = wait_ui('permanent-denial-app-settings', lambda r: any(n.get('package') == 'com.android.settings' for n in r.iter('node')))
            assert any(n.get('package') == 'com.android.settings' for n in root.iter('node'))
            for name in ('READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE'):
                shell('pm', 'grant', PACKAGE, 'android.permission.' + name)
            shell('input', 'keyevent', '4')
            killed = False
            record('permanent-denial-opens-app-settings')
        else:
            grant = next(n for n in root.iter('node') if n.get('resource-id', '').endswith('permission_allow_button'))
            tap(grant)
            killed = False
        row = wait_receipt(2)
        record('grant-return-runs-shared-file-read-write', receipt=row, processKilledWhileAway=killed)

        deny()
        install('hidden', True)
        start()
        root = prompt('hidden-needs-access')
        assert SPLASH in launcher()
        assert len(receipts()) == 2
        tap(by_id(root, 'android:id/button2'))
        record('hidden-launcher-retained-for-recovery')
        if api >= 30:
            shell('appops', 'set', '--uid', PACKAGE, 'MANAGE_EXTERNAL_STORAGE', 'allow')
        else:
            for name in ('READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE'):
                shell('pm', 'grant', PACKAGE, 'android.permission.' + name)
        start(LOG, '--ez', 'launch_script', 'true')
        row = wait_receipt(3)
        assert SPLASH not in launcher()
        record('log-entry-checks-grant-and-hides-launcher', receipt=row)

        start('org.autojs.autojs.inrt.SettingsActivity')
        root = wait_ui('settings-run-entry', lambda r: any(n.get('text') == 'Run script' for n in r.iter('node')))
        run = next(n for n in root.iter('node') if n.get('text') == 'Run script')
        tap(run)
        record('settings-run-script-entry', receipt=wait_receipt(4))

        deny()
        install('missing', True)
        start()
        root = prompt('missing-declarations', False)
        assert len(receipts()) == 4
        tap(by_id(root, 'android:id/button2'))
        record('missing-declarations-stop-before-script')
        report['success'] = True
    finally:
        save()


if __name__ == '__main__':
    main()
