"""Private AVD only: kill the real host between two production-plugin test phases.

Requires the host debug APK, host instrumentation APK and production Agent debug APK.
No model provider, credentials, network or conformance-driver APK is used.
"""
import argparse
import json
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not args.serial.startswith('emulator-'):
        parser.error('An emulator serial is required; real phones must not be killed')
    adb = [args.adb, '-s', args.serial]
    host = 'org.autojs.autojs6'
    plugin = 'io.github.supermonster003.autojs6.plugin.ai.agent'
    test = 'org.autojs.autojs.core.plugin.agent.AiAgentPluginRoundTripTest'

    def command(*parts):
        return subprocess.check_output(adb + list(parts), text=True, encoding='utf-8', errors='replace', timeout=20).strip()

    model = command('shell', 'getprop', 'ro.product.model')
    fingerprint = command('shell', 'getprop', 'ro.build.fingerprint')
    if 'sdk' not in model and 'generic' not in fingerprint:
        parser.error('The selected Android device is not an AVD')
    args.output.mkdir(parents=True, exist_ok=True)
    # Keep the plugin's local binding alive when its task service stops after host death.
    command('shell', 'am', 'start', '-n', f'{plugin}/.ui.LauncherActivity')

    def instrument(phase):
        method = 'prepareHostDeathCheckpoint' if phase == 'prepare' else 'verifyHostDeathCheckpoint'
        return adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'autojs.agent.plugin', 'true',
                      '-e', 'autojs.agent.lifecycle', 'true', '-e', 'autojs.agent.hostDeathPhase', phase,
                      '-e', 'class', f'{test}#{method}', f'{host}.test/androidx.test.runner.AndroidJUnitRunner']

    prepare = args.output / 'host-death-prepare.log'
    with prepare.open('w', encoding='utf-8') as log:
        process = subprocess.Popen(instrument('prepare'), stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 55
        while 'P7_HOST_DEATH_READY' not in prepare.read_text(encoding='utf-8'):
            if process.poll() is not None or time.monotonic() >= deadline:
                raise RuntimeError(f'No durable checkpoint; see {prepare}')
            time.sleep(0.1)
        checkpoint = json.loads(command('exec-out', 'run-as', host, 'cat', 'cache/agent-p7-host-death.json'))
        host_pid = str(checkpoint['hostPid'])
        assert host_pid == command('shell', 'pidof', host), 'Kill only the instrumented host process'
        plugin_pid = command('shell', 'pidof', plugin + ':agent')
        assert plugin_pid.isdecimal()
        command('shell', 'run-as', host, 'kill', '-9', host_pid)
        process.wait(timeout=20)  # Instrumentation abort here is the expected death injection.
    print('Real host process ended; checking surviving plugin history', flush=True)

    deadline = time.monotonic() + 20
    while True:
        rows = [json.loads(command('exec-out', 'run-as', plugin, 'cat', f'files/runs/{run_id}.json'))['run']
                for run_id in checkpoint['ids']]
        if all(row['state'] == 'blocked' for row in rows):
            break
        if time.monotonic() >= deadline:
            raise AssertionError('Running and queued tasks did not become blocked')
        time.sleep(0.1)
    assert plugin_pid == command('shell', 'pidof', plugin + ':agent'), 'Plugin must survive this host-death case'
    for row in rows:
        assert row['result']['error']['code'] == 'HOST_UNAVAILABLE' and 'pending' not in row

    verify = subprocess.run(instrument('verify'), capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=60)
    (args.output / 'host-death-verify.log').write_text(verify.stdout + verify.stderr, encoding='utf-8')
    assert verify.returncode == 0 and 'OK (1 test)' in verify.stdout, 'Recovery phase must pass'
    summary = {'hostActuallyKilled': True, 'pluginSurvived': True, 'blockedRuns': len(rows),
               'automaticReplay': False, 'explicitNewRunCompleted': True}
    (args.output / 'host-death-result.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(summary), flush=True)


if __name__ == '__main__':
    main()
