"""Run the debug-only host audio probe on a disposable emulator, without instrumentation.

Install the matching host/inrt debug APK first and grant WRITE_SETTINGS and
POST_NOTIFICATIONS for the fixture. The helper force-stops only that package and
uses its explicit probe Activity. arm-boot reboots only the selected emulator.
Audio hardening overrides are never changed by this helper.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default=os.environ.get('ADB', 'adb'))
    parser.add_argument('--serial', required=True)
    parser.add_argument('--package', choices=['org.autojs.autojs6', 'org.autojs.autojs6.inrt'], default='org.autojs.autojs6')
    parser.add_argument('--scenario', choices=['visible', 'visible-service', 'recover-service', 'background', 'alarm-service', 'alarm-stream', 'arm-boot'], required=True)
    parser.add_argument('--expect', choices=['allowed', 'blocked', 'focus-blocked-volume-allowed', 'diagnostic-errors'], required=True)
    parser.add_argument('--tts', action='store_true')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        parser.error('Use a disposable emulator; this probe changes its volume and app lifecycle.')
    if args.output.exists() and any(args.output.iterdir()):
        parser.error('Use a new output directory to preserve earlier evidence.')
    args.output.mkdir(parents=True, exist_ok=True)
    base = [args.adb, '-s', args.serial]

    def adb(*command, timeout=30, check=True):
        result = subprocess.run(base + list(command), capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(result.stderr or result.stdout)
        return result.stdout

    package = adb('shell', 'dumpsys', 'package', args.package)
    if not re.search(r'(?:pkgFlags|flags)=\[[^\]]*DEBUGGABLE', package):
        raise RuntimeError('The selected package must be a debuggable fixture build.')
    metadata = {'startedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                'serial': args.serial, 'package': args.package, 'scenario': args.scenario,
                'expected': args.expect, 'ttsRequested': args.tts,
                'fingerprint': adb('shell', 'getprop', 'ro.build.fingerprint').strip()}
    (args.output / 'package-before.txt').write_text(package, encoding='utf-8')
    audio_before = adb('shell', 'dumpsys', 'audio')
    (args.output / 'audio-before.txt').write_text(audio_before, encoding='utf-8')
    override = re.search(r'mHardeningOverride=(-?\d+)', audio_before)
    metadata['hardeningOverride'] = int(override[1]) if override else None
    (args.output / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
    if args.expect == 'diagnostic-errors':
        if metadata['hardeningOverride'] in (None, 0):
            raise RuntimeError('Audio hardening override is not enabled; do not label the default behavior as throw mode.')
    elif metadata['hardeningOverride'] != 0:
        raise RuntimeError('Default-behavior comparisons require a verified mHardeningOverride=0.')
    if args.scenario != 'recover-service':
        adb('shell', 'am', 'force-stop', args.package)
    adb('shell', 'run-as', args.package, 'rm', '-f', 'files/sdk37-audio-probe.json')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    component = args.package + '/org.autojs.autojs.compat.AudioCompatibilityProbeActivity'
    # NEW_TASK | MULTIPLE_TASK guarantees onCreate even when the previous probe is on top.
    command = ['shell', 'am', 'start', '-W', '-f', '0x18000000', '-n', component, '--es', 'scenario', args.scenario,
               '--ez', 'tts', str(args.tts).lower()]
    metadata['command'] = base + command
    (args.output / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
    (args.output / 'launch.txt').write_text(adb(*command, timeout=60), encoding='utf-8')
    rebooted = False
    result = None
    deadline = time.monotonic() + (180 if args.scenario == 'arm-boot' else 90)
    while time.monotonic() < deadline:
        raw = adb('shell', 'run-as', args.package, 'cat', 'files/sdk37-audio-probe.json', check=False)
        try:
            result = json.loads(raw)
        except (ValueError, TypeError):
            time.sleep(1)
            continue
        if result.get('armedForBoot'):
            if not rebooted:
                adb('reboot')
                rebooted = True
            time.sleep(2)
            continue
        if result.get('scenario') != ('boot-service' if args.scenario == 'arm-boot' else args.scenario):
            raise RuntimeError('The result does not belong to the requested scenario.')
        break
    else:
        for command, filename in [(['logcat', '-d', '-v', 'threadtime'], 'failure-logcat.txt'),
                                  (['shell', 'dumpsys', 'activity', 'exit-info', args.package], 'failure-exit-info.txt')]:
            (args.output / filename).write_text(adb(*command, check=False), encoding='utf-8')
        raise RuntimeError('No completed probe result within the deadline.')
    (args.output / 'result.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    for service, filename in [('audio', 'audio-after.txt'), ('media.audio_flinger', 'audio-flinger.txt'),
                              ('activity services ' + args.package, 'services.txt'),
                              ('activity processes ' + args.package, 'processes.txt')]:
        (args.output / filename).write_text(adb('shell', 'dumpsys', *service.split()), encoding='utf-8')
    (args.output / 'logcat.txt').write_text(adb('logcat', '-d', '-v', 'threadtime',
                                              'AudioHardening:V', 'Sdk37AudioProbe:V', 'AndroidRuntime:E', '*:S'), encoding='utf-8')
    allowed = result.get('focus') == 1 and result.get('volumeAfter') == result.get('volumeRequested')
    blocked = result.get('focus') == 0 and result.get('volumeAfter') == result.get('volumeBefore')
    expectations = {
        'allowed': allowed,
        'blocked': blocked,
        # Keep the observed volume/focus difference distinct; it does not establish a general exemption.
        'focus-blocked-volume-allowed': result.get('focus') == 0 and result.get('volumeAfter') == result.get('volumeRequested'),
        'diagnostic-errors': ('IllegalStateException' in result.get('volumeCallError', '')
                              and 'IllegalStateException' in result.get('focusError', '')
                              and isinstance(result.get('track'), dict)
                              and result['track'].get('written', 0) < 0),
    }
    success = expectations[args.expect]
    metadata.update(success=success, rebooted=rebooted,
                    note='Focus/volume expectations only. AudioTrack counters and TTS callbacks do not prove audible output.')
    (args.output / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'success': success, 'result': result, 'evidence': str(args.output)}, ensure_ascii=False))
    if not success:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
