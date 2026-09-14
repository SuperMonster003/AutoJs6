"""Exercise the production history UI and real exits on an exclusively owned API 37 AVD.

Terminates only the installed debuggable host and its crash-report process. Requires
an English, unlocked, disposable AVD with host test permissions already configured.
No memory allocation workload is used; these exits do not test the memory limiter.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

from physical_device import Device, utc_now, write_json
from run_framework_probe import checked

HOST = 'org.autojs.autojs6'
ACTIVITY = 'org.autojs.autojs.ui.error.ProcessExitHistoryActivity'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--owned-avd', required=True, help='Name of the disposable AVD exclusively owned by this run')
    parser.add_argument('--expected-target', type=int, choices=(36, 37), required=True)
    parser.add_argument('--require-empty-initial', action='store_true', help='Assert empty history after a separate fresh installation on the owned clone')
    parser.add_argument('--ui-only', action='store_true', help='Only exercise entry, refresh, copy and recreation; cleanup still stops the host')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        parser.error('An explicit emulator serial is required')
    args.output.mkdir(parents=True, exist_ok=False)
    d = Device(args.adb, args.serial)
    d.require_online()
    identity = d.identity()
    if identity['sdk'] != '37' or checked(d.call('emu', 'avd', 'name')).splitlines()[0] != args.owned_avd:
        raise RuntimeError('Owned API 37 AVD identity mismatch')
    package = checked(d.shell('dumpsys', 'package', HOST))
    if not re.search(r'targetSdk=' + str(args.expected_target) + r'\b', package) or 'DEBUGGABLE' not in package:
        raise RuntimeError('Expected debuggable host target mismatch')
    result = {'started': utc_now(), 'device': identity, 'target': args.expected_target, 'success': False, 'stages': {}, 'observers': []}
    case = None
    command_id = 0

    def receipt():
        try:
            return json.loads(d.shell('run-as', HOST, 'cat', 'files/sdk37-exit-' + case + '.json')['stdout'])
        except json.JSONDecodeError:
            return {}

    def wait_for(predicate, label, timeout=30):
        deadline = time.monotonic() + timeout
        latest = {}
        while time.monotonic() < deadline:
            latest = receipt()
            if latest.get('errors'):
                raise RuntimeError(str(latest['errors']))
            if predicate(latest):
                return latest
            time.sleep(0.3)
        raise RuntimeError('Timed out: ' + label + ' ' + json.dumps(latest.get('current'))[:1000])

    def command(action, wait=True):
        nonlocal command_id
        command_id += 1
        data = json.dumps({'id': command_id, 'action': action})
        path = 'files/sdk37-exit-' + case + '.command'
        checked(d.shell('run-as', HOST, 'sh', '-c', 'printf %s ' + shlex.quote(data) + ' > ' + path + '.tmp && mv ' + path + '.tmp ' + path))
        if wait:
            return wait_for(lambda r: any(c['id'] == command_id for c in r.get('commands', [])), action)

    def start_observer():
        nonlocal case, command_id
        case, command_id = uuid.uuid4().hex, 0
        checked(d.shell('am', 'start', '-W', '-n', HOST + '/org.autojs.autojs.ui.main.MainActivity'))
        script = Path(__file__).with_name('smoke-exit-history.js').read_text(encoding='utf-8').replace('__SDK37_EXIT_HISTORY_CONFIG__', json.dumps({'caseId': case}))
        (args.output / (case + '.js')).write_text(script, encoding='utf-8')
        result['observers'].append({'caseId': case, 'scriptSha256': hashlib.sha256(script.encode()).hexdigest()})
        checked(d.shell('am', 'start', '-n', HOST + '/org.autojs.autojs.external.open.RunIntentActivity', '--es', 'script', script, timeout=45))
        wait_for(lambda r: r.get('ready') is True, 'observer ready', 120)

    def capture(label):
        state = wait_for(lambda r: (r.get('current') or {}).get('activity') == ACTIVITY and r['current'].get('copyEnabled') is True, label)['current']
        window = checked(d.shell('dumpsys', 'window'))
        focus = next((x.strip() for x in window.splitlines() if 'mCurrentFocus=' in x), '')
        if ACTIVITY not in focus:
            raise RuntimeError('History UI is not focused: ' + focus)
        state['focusedWindow'] = focus
        result['stages'][label] = state
        (args.output / (label + '-system-history.txt')).write_text(checked(d.shell('dumpsys', 'activity', 'exit-info', HOST)), encoding='utf-8')
        with (args.output / (label + '.png')).open('wb') as stream:
            subprocess.run([args.adb, '-s', args.serial, 'exec-out', 'screencap', '-p'], stdout=stream, check=True, timeout=30)
        return state

    def open_report():
        command('settings')
        wait_for(lambda r: (r.get('current') or {}).get('activity', '').endswith('.PreferencesActivity'), 'settings')
        title = command('scroll-entry')['commands'][-1]['preferenceTitle']
        time.sleep(0.5)
        remote_xml = '/data/local/tmp/sdk37-exit-' + case + '.xml'
        checked(d.shell('uiautomator', 'dump', remote_xml, timeout=30))
        xml = checked(d.shell('cat', remote_xml))
        checked(d.shell('rm', remote_xml))
        xml = xml[xml.index('<?xml'):xml.rindex('</hierarchy>') + len('</hierarchy>')]
        (args.output / (case + '-settings.xml')).write_text(xml, encoding='utf-8')
        node = next(n for n in ET.fromstring(xml).iter('node') if n.get('text') == title)
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
        checked(d.shell('input', 'tap', str((x1 + x2)//2), str((y1 + y2)//2)))
        wait_for(lambda r: (r.get('current') or {}).get('activity') == ACTIVITY and r['current'].get('copyEnabled') is True, 'history loaded')

    def save_observer():
        write_json(args.output / (case + '-receipt.json'), receipt())

    try:
        apk = checked(d.shell('pm', 'path', HOST)).splitlines()[0].removeprefix('package:')
        result['installedApkSha256'] = checked(d.shell('sha256sum', apk)).split()[0]
        result['memoryLimiter'] = d.shell('am', 'memory-limiter', 'status')
        start_observer()
        open_report()
        first = capture('initial')
        assert first['title'] == 'Process exit history'
        if args.require_empty_initial:
            assert 'No system exit records in this time range.' in first['report']
            result['emptyHistoryVerified'] = True
        command('refresh')
        refreshed = capture('refreshed')
        assert first['report'] == refreshed['report']
        copied = command('copy')['commands'][-1]['clipboard']
        assert copied == refreshed['report']
        result['clipboardMatches'] = True
        command('recreate')
        wait_for(lambda r: (r.get('current') or {}).get('instance') != first['instance'] and (r.get('current') or {}).get('copyEnabled') is True, 'recreated activity')
        recreated = capture('recreated')
        assert recreated['report'] == refreshed['report']
        if args.ui_only:
            command('finish')
            result['success'] = True
            return

        main_pid = receipt()['pid']
        result['selfExitPid'] = main_pid
        command('self-exit', wait=False)
        wait_for(lambda r: r.get('selfExitRequested', {}).get('pid') == main_pid, 'self exit request')
        time.sleep(1)
        save_observer()
        start_observer()
        open_report()
        self_exit = capture('self-exit')
        assert f'PID {main_pid})\nProcess exited on its own\nSystem reason: REASON_EXIT_SELF (1), status: 0' in self_exit['report']

        command('crash-report')
        deadline = time.monotonic() + 30
        crash_pid = ''
        while time.monotonic() < deadline:
            crash_pid = d.shell('pidof', HOST + ':crash_report')['stdout'].strip()
            if crash_pid.isdigit():
                break
            time.sleep(0.3)
        if not crash_pid.isdigit():
            raise RuntimeError('Owned crash-report process not running')
        result['crashReportPid'] = int(crash_pid)
        checked(d.shell('run-as', HOST, 'kill', '-9', crash_pid))
        time.sleep(1)
        open_report()
        multi = capture('multiple-processes')
        assert f'Process: {HOST}:crash_report (PID {crash_pid})' in multi['report']
        assert f'PID {main_pid})' in multi['report']

        result['forceStopPid'] = receipt()['pid']
        save_observer()
        checked(d.shell('am', 'force-stop', HOST))
        time.sleep(1)
        start_observer()
        open_report()
        stopped = capture('user-stop')
        assert f'PID {result["forceStopPid"]})\nUser or system stop request\nSystem reason: REASON_USER_REQUESTED (10)' in stopped['report']
        assert 'System memory limit reached' not in stopped['report']
        assert command('copy')['commands'][-1]['clipboard'] == stopped['report']
        command('finish')
        result['success'] = True
    except Exception as error:
        result['error'] = f'{type(error).__name__}: {error}'
    finally:
        if case:
            save_observer()
        result['finished'] = utc_now()
        write_json(args.output / 'result.json', result)
        checked(d.shell('am', 'force-stop', HOST))
        if not result['success']:
            raise RuntimeError(result.get('error', 'Probe failed'))
        print(json.dumps({'success': True, 'target': args.expected_target, 'stages': list(result['stages'])}))


if __name__ == '__main__':
    main()
