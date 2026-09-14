"""Check that physical storage probes cannot remove preexisting or replaced packages."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import run_inrt_storage_probe as probe


class PhysicalStorageProbeTest(unittest.TestCase):
    shared = '/sdcard/Download/autojs6-sdk37-storage-unit.txt'

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='aj6-storage-probe-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.fixtures = self.root / 'fixtures'
        self.fixtures.mkdir()
        self.output = self.root / 'result'
        self.calls = []
        self.fingerprint = 'expected-build'
        self.users = 'Users:\n UserInfo{0:Owner:0} running'
        self.existing = ''
        self.installed_hash = None
        self.metadata = {'runOnBoot': False, 'sharedFile': self.shared, 'fixtures': []}
        for case in ('private', 'shared', 'hidden', 'missing'):
            apk = self.fixtures / (case + '.apk')
            with zipfile.ZipFile(apk, 'w') as archive:
                config = {'packageName': probe.PACKAGE, 'launchConfig': {
                    'runOnBoot': False, 'requiresSharedStorage': case != 'private'}}
                archive.writestr('assets/project/project.json', json.dumps(config))
                archive.writestr('assets/project/main.js', json.dumps(self.shared))
            self.metadata['fixtures'].append({'case': case, 'target': 29,
                                              'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()})

    def fake_adb(self, args, **kwargs):
        command = tuple(map(str, args[3:]))
        self.calls.append(command)
        output, code = '', 0
        if command == ('shell', 'getprop', 'ro.build.fingerprint'):
            output = self.fingerprint
        elif command == ('shell', 'am', 'get-current-user'):
            output = '0'
        elif command == ('shell', 'pm', 'list', 'users'):
            output = self.users
        elif command == ('shell', 'pm', 'list', 'packages', '-u', 'org.autojs.autojs6'):
            output = self.existing
        elif command == ('shell', 'getprop', 'persist.sys.locale'):
            output = 'en-US'
        elif command in [('shell', 'pm', 'list', 'packages', probe.PACKAGE),
                         ('shell', 'pm', 'list', 'packages', '-u', probe.PACKAGE)]:
            output = ''
        elif command == ('shell', 'ls', self.shared):
            code = 1
        elif command[0] == 'push':
            self.assertEqual(command[-1], self.shared)
        elif command == ('shell', 'getprop', 'ro.build.version.sdk'):
            output = '37'
        elif command[0] == 'install':
            # Model an ambiguous install failure after the package became visible.
            code, output = 1, 'install transport failed'
        elif command == ('shell', 'pm', 'path', probe.PACKAGE):
            output = 'package:/data/app/fixture/base.apk'
        elif command == ('shell', 'sha256sum', '/data/app/fixture/base.apk'):
            output = self.installed_hash + '  /data/app/fixture/base.apk'
        elif command == ('uninstall', probe.PACKAGE):
            output = 'Success'
        elif command == ('shell', 'cat', self.shared):
            output = 'legacy-fixture'
        elif command == ('shell', 'rm', self.shared):
            pass
        elif command[:3] == ('shell', 'rm', '-f'):
            self.assertTrue(command[3].startswith('/data/local/tmp/sdk37-storage-ui-'))
        else:
            self.fail('Unexpected ADB command: ' + str(command))
        return subprocess.CompletedProcess(args, code, output, '')

    def run_probe(self, physical=True):
        (self.fixtures / 'fixtures.json').write_text(json.dumps(self.metadata), encoding='utf-8')
        argv = ['probe', '--adb', 'unused-adb', '--serial', 'phone:1234',
                '--fixtures', str(self.fixtures), '--output', str(self.output),
                '--target', '29', '--shared-file', self.shared]
        argv += ['--physical-device', '--expected-fingerprint', 'expected-build'] if physical else ['--owned-avd', 'AVD_API_37']
        with patch.object(sys, 'argv', argv), patch.object(probe.subprocess, 'run', self.fake_adb), contextlib.redirect_stdout(io.StringIO()):
            probe.main()

    def assert_no_mutation(self):
        for command in self.calls:
            self.assertEqual(command[0], 'shell')
            self.assertIn(command[1], ('getprop', 'am', 'pm', 'ls'))
            if command[1] == 'am':
                self.assertEqual(command[2], 'get-current-user')
            if command[1] == 'pm':
                self.assertEqual(command[2], 'list')

    def test_phone_without_explicit_physical_mode_never_contacts_adb(self):
        with self.assertRaises(AssertionError):
            self.run_probe(physical=False)
        self.assertEqual(self.calls, [])

    def test_changed_fingerprint_stops_before_mutation(self):
        self.fingerprint = 'different-build'
        with self.assertRaises(AssertionError):
            self.run_probe()
        self.assert_no_mutation()

    def test_preexisting_host_is_not_uninstalled(self):
        self.existing = 'package:org.autojs.autojs6'
        with self.assertRaises(AssertionError):
            self.run_probe()
        self.assert_no_mutation()

    def test_second_profile_stops_before_mutation(self):
        self.users += '\n UserInfo{10:Work:30} running'
        with self.assertRaises(AssertionError):
            self.run_probe()
        self.assert_no_mutation()

    def test_boot_enabled_fixture_stops_before_mutation(self):
        self.metadata['runOnBoot'] = True
        with self.assertRaises(AssertionError):
            self.run_probe()
        self.assert_no_mutation()

    def test_changed_fixture_bytes_stop_before_mutation(self):
        (self.fixtures / 'private.apk').write_bytes(b'replaced')
        with self.assertRaises(AssertionError):
            self.run_probe()
        self.assert_no_mutation()

    def test_cleanup_leaves_an_unrecognized_installed_package(self):
        self.installed_hash = '0' * 64
        with self.assertRaisesRegex(AssertionError, 'Installed APK changed'):
            self.run_probe()
        self.assertNotIn(('uninstall', probe.PACKAGE), self.calls)
        self.assertFalse(any(command[:2] == ('shell', 'rm') for command in self.calls))
        self.assertFalse(json.loads((self.output / 'results.json').read_text())['cleanup']['success'])

    def test_cleanup_removes_only_the_known_fixture_after_install_failure(self):
        self.installed_hash = self.metadata['fixtures'][0]['sha256']
        with self.assertRaises(RuntimeError):
            self.run_probe()
        self.assertEqual([command for command in self.calls if command[0] == 'uninstall'], [('uninstall', probe.PACKAGE)])
        self.assertIn(('shell', 'rm', self.shared), self.calls)
        self.assertTrue(json.loads((self.output / 'results.json').read_text())['cleanup']['success'])


if __name__ == '__main__':
    unittest.main()
