"""Exercise real inrt activities and file I/O on a selected test device.

Requires English system UI, no installed inrt, and fixtures produced by
build_inrt_storage_fixtures.py. Installs four disposable APKs, changes only their
storage grants, and leaves the last one installed on an owned emulator for
diagnosis. Physical mode additionally requires an exact fingerprint, a single
owner user, no AutoJs6 packages, and boot-disabled fixtures. It removes only its
hash-verified APK and uniquely named files afterward, including on test failure.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET
import zipfile

PACKAGE = 'org.autojs.autojs6.inrt'
SPLASH = 'org.autojs.autojs.inrt.SplashActivity'
LOG = 'org.autojs.autojs.inrt.LogActivity'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    device = parser.add_mutually_exclusive_group(required=True)
    device.add_argument('--owned-avd')
    device.add_argument('--physical-device', action='store_true')
    parser.add_argument('--expected-fingerprint', help='Required in physical mode; must match the selected phone')
    parser.add_argument('--fixtures', type=Path, required=True)
    parser.add_argument('--upgrade-fixtures', type=Path, help='Run the four-check target 29 to 37 upgrade instead of the UI matrix')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--target', type=int, choices=(29, 37), required=True)
    parser.add_argument('--permanent-denial', action='store_true', help='Exercise legacy Do not ask again and app settings recovery')
    parser.add_argument('--shared-file', required=True)
    args = parser.parse_args()
    if args.upgrade_fixtures:
        assert args.target == 29
    if not args.physical_device:
        assert re.fullmatch(r'emulator-\d+', args.serial)
    else:
        assert args.expected_fingerprint and not args.serial.startswith('emulator-')
    assert re.fullmatch(r'/sdcard/Download/autojs6-sdk37-storage-[\w-]+\.txt', args.shared_file)
    args.output.mkdir(parents=True, exist_ok=False)
    report = {'target': args.target, 'serial': args.serial, 'checks': [], 'success': False}
    transcript = []
    fixture_hashes = set()
    physical_owned = False
    shared_created = False
    ui_path = '/data/local/tmp/sdk37-storage-ui-' + uuid.uuid4().hex + '.xml'

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

    def wait_receipt(count, target=None):
        # Fresh emulator boots also initialize the script engine on first use.
        deadline = time.monotonic() + 60
        while True:
            rows = receipts()
            if len(rows) >= count:
                assert len(rows) == count, rows
                row = rows[-1]
                assert row['private'] and not row.get('error') and row['target'] == (args.target if target is None else target), row
                if row['case'] != 'private':
                    assert row['shared'], row
                return row
            if time.monotonic() >= deadline:
                raise AssertionError(f'Expected {count} script receipts, got {rows}')
            time.sleep(.3)

    def ui(label):
        prepare_screen()
        shell('uiautomator', 'dump', ui_path)
        raw = shell('cat', ui_path)
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
        prepare_screen()
        if activity == SPLASH:
            deadline = time.monotonic() + 15
            while SPLASH not in launcher() and time.monotonic() < deadline:
                time.sleep(.2)
            assert SPLASH in launcher(), 'Update broadcast has not restored the launcher'
        result = shell('am', 'start', '-W', '-n', PACKAGE + '/' + activity, *extras)
        assert 'Error' not in result, result

    def install(case, update=False):
        out = adb('install', *(['--no-incremental'] if args.physical_device else []),
                  *(['-r'] if update else []), args.fixtures / (case + '.apk'), timeout=120)
        assert 'Success' in out, out

    def prepare_screen():
        if args.physical_device:
            shell('input', 'keyevent', 'KEYCODE_WAKEUP')
            # Dismisses an ordinary swipe screen; does not supply credentials or change lock settings.
            result = shell('wm', 'dismiss-keyguard')
            assert 'Error' not in result, 'Unlock the selected test phone before running UI checks'

    def launcher():
        return shell('cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN',
                     '-c', 'android.intent.category.LAUNCHER', PACKAGE)

    def deny():
        if api >= 30:
            shell('appops', 'set', '--uid', PACKAGE, 'MANAGE_EXTERNAL_STORAGE', 'ignore')
        else:
            for name in ('READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE'):
                shell('pm', 'revoke', PACKAGE, 'android.permission.' + name, check=False)

    def physical_preflight():
        assert shell('getprop', 'ro.build.fingerprint') == args.expected_fingerprint
        assert shell('am', 'get-current-user') == '0'
        assert re.findall(r'UserInfo\{(\d+):', shell('pm', 'list', 'users')) == ['0']
        # The template shares signature permissions with the host; never remove a preexisting host.
        assert not shell('pm', 'list', 'packages', '-u', 'org.autojs.autojs6')
        assert shell('getprop', 'persist.sys.locale').startswith('en')
        metadata = json.loads((args.fixtures / 'fixtures.json').read_text(encoding='utf-8'))
        assert metadata['runOnBoot'] is False and metadata['sharedFile'] == args.shared_file
        assert [f['case'] for f in metadata['fixtures']] == ['private', 'shared', 'hidden', 'missing']
        for item in metadata['fixtures']:
            apk = args.fixtures / (item['case'] + '.apk')
            assert hashlib.sha256(apk.read_bytes()).hexdigest() == item['sha256']
            assert item['target'] == args.target
            with zipfile.ZipFile(apk) as archive:
                config = json.loads(archive.read('assets/project/project.json'))
                assert config['packageName'] == PACKAGE
                assert config['launchConfig']['runOnBoot'] is False
                assert config['launchConfig']['requiresSharedStorage'] == (item['case'] != 'private')
                assert json.dumps(args.shared_file).encode() in archive.read('assets/project/main.js')
            fixture_hashes.add(item['sha256'])
        report.update(physicalDevice=True, fixtures=metadata, baselinePackagesAbsent=True)

    def check_upgrade_fixture():
        before = json.loads((args.fixtures / 'fixtures.json').read_text(encoding='utf-8'))
        after = json.loads((args.upgrade_fixtures / 'fixtures.json').read_text(encoding='utf-8'))
        assert before['sharedFile'] == after['sharedFile'] == args.shared_file
        old = next(f for f in before['fixtures'] if f['case'] == 'shared')
        new = next(f for f in after['fixtures'] if f['case'] == 'shared')
        assert old['target'] == 29 and new['target'] == 37 and new['versionCode'] > old['versionCode']
        assert old['signer'] == new['signer']
        apk = args.upgrade_fixtures / 'shared.apk'
        assert hashlib.sha256(apk.read_bytes()).hexdigest() == new['sha256']
        with zipfile.ZipFile(apk) as archive:
            config = json.loads(archive.read('assets/project/project.json'))
            assert config['packageName'] == PACKAGE and config['launchConfig']['requiresSharedStorage'] is True
            if args.physical_device:
                assert after['runOnBoot'] is False and config['launchConfig']['runOnBoot'] is False
            assert json.dumps(args.shared_file).encode() in archive.read('assets/project/main.js')
        fixture_hashes.add(new['sha256'])
        report['upgradeFixtures'] = {'before': old, 'after': new}

    def run_upgrade():
        assert api >= 30
        install('shared')
        shell('appops', 'set', '--uid', PACKAGE, 'MANAGE_EXTERNAL_STORAGE', 'allow')
        start()
        row = wait_receipt(1)
        assert row['manager'] is True
        previous = receipts()
        content = shell('cat', args.shared_file)
        record('target29-all-files-shared-read-write', receipt=row, file=content)
        assert 'Success' in adb('install', *(['--no-incremental'] if args.physical_device else []),
                                '-r', args.upgrade_fixtures / 'shared.apk', timeout=120)
        assert receipts() == previous and shell('cat', args.shared_file) == content
        start()
        row = wait_receipt(2, 37)
        assert row['manager'] is True
        record('target37-update-preserves-files-and-all-files-grant', receipt=row, oldPrivateReceiptPreserved=True)
        previous = receipts()
        content = shell('cat', args.shared_file)
        deny()
        start()
        root = prompt('upgrade-revocation')
        assert receipts() == previous and shell('cat', args.shared_file) == content
        record('revocation-after-upgrade-stops-script-without-changing-files')
        tap(by_id(root, 'android:id/button2'))
        shell('appops', 'set', '--uid', PACKAGE, 'MANAGE_EXTERNAL_STORAGE', 'allow')
        start()
        row = wait_receipt(3, 37)
        assert row['manager'] is True
        record('target37-update-resumes-old-shared-file-after-regrant', receipt=row, file=shell('cat', args.shared_file))

    def physical_cleanup():
        report['cleanup'] = {'success': False}
        try:
            assert shell('getprop', 'ro.build.fingerprint') == args.expected_fingerprint
            installed = shell('pm', 'path', PACKAGE)
            if installed:
                lines = installed.splitlines()
                assert len(lines) == 1 and lines[0].startswith('package:/data/app/')
                apk_hash = shell('sha256sum', lines[0].removeprefix('package:')).split()[0]
                assert apk_hash in fixture_hashes, 'Installed APK changed; leave it for inspection'
                report['cleanup']['removedApkSha256'] = apk_hash
                assert 'Success' in adb('uninstall', PACKAGE)
            if shared_created:
                content = shell('cat', args.shared_file)
                assert re.fullmatch(r'legacy-fixture(?:\nshared-write-ok)*', content), 'Test file changed unexpectedly'
                report['cleanup']['sharedFileContent'] = content
                shell('rm', args.shared_file)
            shell('rm', '-f', ui_path)
            assert not shell('pm', 'list', 'packages', '-u', PACKAGE)
            assert not shell('ls', args.shared_file, check=False)
            report['cleanup']['success'] = True
        finally:
            save()

    try:
        if args.physical_device:
            physical_preflight()
        else:
            assert args.owned_avd in adb('emu', 'avd', 'name').splitlines()
            assert '1' in (shell('getprop', 'ro.boot.qemu'), shell('getprop', 'ro.kernel.qemu'))
        if args.upgrade_fixtures:
            check_upgrade_fixture()
        assert not shell('pm', 'list', 'packages', PACKAGE)
        assert not shell('ls', args.shared_file, check=False)
        physical_owned = args.physical_device
        # This new path is the only shared-storage file the scripts access.
        local = args.output / 'shared.txt'
        local.write_text('legacy-fixture', encoding='utf-8')
        adb('push', local, args.shared_file)
        shared_created = True
        api = int(shell('getprop', 'ro.build.version.sdk'))
        report.update(api=api, fingerprint=shell('getprop', 'ro.build.fingerprint'))
        if args.upgrade_fixtures:
            run_upgrade()
            report['success'] = True
            return
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
            switches = [n for n in root.iter('node') if n.get('package') == 'com.android.settings' and n.get('checkable') == 'true']
            assert len(switches) == 1, 'Expected the app-specific all-files switch'
            switch = switches[0]
            assert switch.get('checked') == 'false'
            tap(switch)
            # Recreation while away must restore the pending result and launch once.
            shell('am', 'kill', PACKAGE)
            if args.physical_device:
                pid = shell('pidof', PACKAGE, check=False)
                if pid:
                    assert re.fullmatch(r'\d+', pid)
                    shell('run-as', PACKAGE, 'kill', '-9', pid)
            killed = not shell('pidof', PACKAGE, check=False)
            if args.physical_device:
                assert killed, 'Process recreation was not exercised'
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
        if physical_owned:
            physical_cleanup()


if __name__ == '__main__':
    main()
