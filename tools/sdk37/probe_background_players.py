#!/usr/bin/env python3
"""Verify real Ember/Terra playback on an English-language disposable API 37 AVD."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET


PLAYERS = {
    'ember': ('Three-Ember-Player', 'videoplayer', 'threeemberplayer'),
    'terra': ('Three-Terra-Player', 'audioplayer', 'threeterraplayer'),
}
PREFIX = 'io.github.supermonster003.autojs6.plugin.'
FIXTURE = 'org.autojs.sdk37.fleetfixture'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--expected-avd', required=True, help='Exact disposable AVD name; physical devices are refused')
    parser.add_argument('--projects', type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument('--fixture', type=Path, required=True, help='APK from build_fleet_fixture.py')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--player', choices=PLAYERS, nargs='+', default=list(PLAYERS))
    parser.add_argument('--reset-test-data', action='store_true', help='Required: reset only the selected player test installations')
    args = parser.parse_args()
    if not args.serial.startswith('emulator-') or not args.reset_test_data:
        parser.error('Use a disposable emulator and explicitly pass --reset-test-data')

    def adb(*command, binary=False, timeout=60, check=True):
        result = subprocess.run([args.adb, '-s', args.serial, *map(str, command)], capture_output=True, timeout=timeout)
        if result.returncode and check:
            raise RuntimeError((result.stdout + result.stderr).decode('utf-8', errors='replace'))
        return result.stdout if binary else result.stdout.decode('utf-8', errors='replace')

    def shell(*command, timeout=60):
        return adb('shell', '-T', shlex.join(map(str, command)), timeout=timeout)

    if adb('emu', 'avd', 'name').splitlines()[0].strip() != args.expected_avd:
        parser.error('The selected device is not the expected AVD')
    if shell('getprop', 'ro.build.version.sdk').strip() != '37':
        parser.error('This probe requires API 37')
    args.output.mkdir(parents=True, exist_ok=False)

    def save(name, value):
        (args.output / name).write_text(value, encoding='utf-8')

    def window(label):
        shell('uiautomator', 'dump', '/sdcard/sdk37-fleet-window.xml', timeout=30)
        xml = shell('cat', '/sdcard/sdk37-fleet-window.xml')
        save(label + '.xml', xml)
        (args.output / (label + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        return ET.fromstring(xml)

    def hardening_override():
        value = shell('dumpsys', 'audio')
        match = re.search(r'mHardeningOverride=(-?\d+)', value)
        return int(match[1]) if match else None, value

    original, audio = hardening_override()
    save('audio-before.txt', audio)
    if original != 0:
        raise RuntimeError('Start with the default audio hardening override (0)')
    # Shell help can return -1 despite supplying its command table.
    audio_help = adb('shell', '-T', 'cmd audio', check=False)
    save('audio-shell-help.txt', audio_help)
    command = 'set-hardening' if 'set-hardening' in audio_help else 'set-enable-hardening'
    if command not in audio_help:
        raise RuntimeError('The image does not advertise an audio hardening test command')
    adb('install', '-r', args.fixture, timeout=180)
    fingerprint = shell('getprop', 'ro.build.fingerprint').strip()
    page_size = int(shell('getconf', 'PAGE_SIZE').strip())
    selected = [PREFIX + PLAYERS[name][1] for name in args.player]
    try:
        shell('cmd', 'audio', command, 'throw')
        override, audio = hardening_override()
        save('audio-throw.txt', audio)
        if override != 3:
            raise RuntimeError('The audio service did not enter throw mode (3)')
        for name in args.player:
            repo_name, package_suffix, namespace_suffix = PLAYERS[name]
            package, namespace = PREFIX + package_suffix, PREFIX + namespace_suffix
            directory = args.projects / ('AutoJs6-Plugin-' + repo_name) / 'app/build/outputs/apk/debug'
            metadata = json.loads((directory / 'output-metadata.json').read_text(encoding='utf-8'))
            if metadata['applicationId'] != package:
                raise RuntimeError('Unexpected APK application ID')
            element, = [entry for entry in metadata['elements'] if not entry['filters']]
            apk = directory / element['outputFile']
            adb('install', '-r', apk, timeout=180)
            shell('pm', 'clear', package)
            for other in selected:
                shell('am', 'force-stop', other)
            if 'targetSdk=37' not in shell('dumpsys', 'package', package):
                raise RuntimeError('The installed player does not target SDK 37')
            shell('pm', 'grant', package, 'android.permission.POST_NOTIFICATIONS')
            if name == 'ember':
                # Home normally enters visible PiP. Test the separate opt-in audio path.
                shell('appops', 'set', package, 'PICTURE_IN_PICTURE', 'deny')
                shell('run-as', package, 'mkdir', '-p', 'shared_prefs')
                prefs = '<?xml version="1.0" encoding="utf-8"?><map><boolean name="continue_audio_in_background" value="true"/></map>'
                shell('run-as', package, 'sh', '-c', 'printf %s ' + shlex.quote(prefs) + ' > shared_prefs/player_settings.xml')
            shell('input', 'keyevent', 'KEYCODE_WAKEUP')
            shell('wm', 'dismiss-keyguard')
            shell('cmd', 'statusbar', 'collapse')
            since = shell('date', '+%m-%d %H:%M:%S.000').strip()
            shell('am', 'force-stop', FIXTURE)
            shell('am', 'start', '-W', '-n', FIXTURE + '/.FixtureActivity', '--es', 'player', name)
            time.sleep(6)
            window(name + '-visible')
            shell('input', 'keyevent', 'KEYCODE_HOME')
            time.sleep(3)

            def snapshot(label):
                session = shell('dumpsys', 'media_session')
                service = shell('dumpsys', 'activity', 'services', namespace)
                save(name + '-' + label + '-session.txt', session)
                save(name + '-' + label + '-service.txt', service)
                save(name + '-' + label + '-audio.txt', shell('dumpsys', 'audio'))
                block = session.partition('package=' + package)[2]
                state = re.search(r'state=PlaybackState \{state=(?:\w+\()?([0-9]+)\)?, position=([0-9]+)', block)
                if not state:
                    raise AssertionError('The selected player has no playback state: ' + label)
                return {'state': int(state[1]), 'position': int(state[2]), 'foreground': 'isForeground=true' in service}

            start = snapshot('background')
            assert start['state'] == 3 and start['foreground'], start
            time.sleep(5)
            shell('cmd', 'media_session', 'dispatch', 'pause')
            time.sleep(1)
            paused = snapshot('paused')
            assert paused['state'] == 2 and paused['position'] > start['position'] + 2000, paused
            shell('cmd', 'media_session', 'dispatch', 'play')
            time.sleep(2)
            assert snapshot('resumed')['state'] == 3
            shell('input', 'keyevent', 'KEYCODE_SLEEP')
            time.sleep(4)
            locked = snapshot('locked')
            assert locked['state'] == 3 and locked['foreground'], locked
            shell('cmd', 'media_session', 'dispatch', 'pause')
            time.sleep(1)
            lock_paused = snapshot('locked-paused')
            assert lock_paused['state'] == 2 and lock_paused['position'] > paused['position'] + 2000, lock_paused
            shell('cmd', 'media_session', 'dispatch', 'play')
            time.sleep(1)
            assert snapshot('locked-resumed')['state'] == 3
            if name == 'terra':
                # The product's Exit player action releases its restorable session.
                shell('input', 'keyevent', 'KEYCODE_WAKEUP')
                shell('wm', 'dismiss-keyguard')
                shell('cmd', 'statusbar', 'expand-notifications')
                time.sleep(2)
                root = window('terra-exit-action')
                control = next(node for node in root.iter('node') if node.get('content-desc') == 'Exit player')
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', control.get('bounds')))
                shell('input', 'tap', (x1 + x2) // 2, (y1 + y2) // 2)
            else:
                shell('cmd', 'media_session', 'dispatch', 'stop')
            deadline = time.monotonic() + 30
            while True:
                time.sleep(1)
                service = shell('dumpsys', 'activity', 'services', namespace)
                if 'isForeground=true' not in service or time.monotonic() >= deadline:
                    break
            save(name + '-stopped-service.txt', service)
            assert 'isForeground=true' not in service, service
            log = adb('logcat', '-d', '-T', since, '-s', 'AudioHardening:V', 'AndroidRuntime:E')
            save(name + '-hardening-log.txt', log)
            assert not any(package in line and ('level: full' in line or 'level: partial' in line) for line in log.splitlines()), log
            record = {'package': package, 'serial': args.serial, 'fingerprint': fingerprint, 'pageSize': page_size,
                      'targetSdk': 37, 'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'hardeningOverride': override,
                      'background': start, 'paused': paused, 'locked': locked, 'lockedPaused': lock_paused,
                      'stopReleasedForegroundService': True, 'physicalAudibilityTested': False}
            save(name + '-result.json', json.dumps(record, indent=2) + '\n')
            shell('am', 'force-stop', package)
            print(repo_name + ': real background / controls / lock / exit PASS', flush=True)
    finally:
        for package in selected:
            shell('am', 'force-stop', package)
        shell('cmd', 'audio', 'clear-hardening')
        shell('input', 'keyevent', 'KEYCODE_WAKEUP')
        shell('wm', 'dismiss-keyguard')
        shell('cmd', 'statusbar', 'collapse')


if __name__ == '__main__':
    main()
