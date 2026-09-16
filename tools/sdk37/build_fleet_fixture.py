"""Build an isolated target-37 fixture with synthetic media and a known screen color."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--ffmpeg', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    source = Path(__file__).resolve().parent / 'fixtures/fleet'
    build_tools = args.sdk.resolve() / 'build-tools/37.0.0'
    android = args.sdk.resolve() / 'platforms/android-37/android.jar'
    if not android.exists():
        android = args.sdk.resolve() / 'platforms/android-37.0/android.jar'
    env = dict(os.environ, JAVA_HOME=str(args.jdk.resolve()))
    exe, bat = ('.exe', '.bat') if os.name == 'nt' else ('', '')

    def run(*command):
        subprocess.run([str(c) for c in command], env=env, check=True)

    video, audio = output / 'fixture.mp4', output / 'fixture.m4a'
    run(args.ffmpeg, '-v', 'error', '-f', 'lavfi', '-i', 'color=c=0x336699:s=320x240:r=15',
        '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=44100', '-t', '120',
        '-c:v', 'mpeg4', '-q:v', '10', '-c:a', 'aac', '-strict', 'experimental', '-b:a', '32k', video)
    run(args.ffmpeg, '-v', 'error', '-i', video, '-vn', '-c:a', 'copy', audio)
    classes, dex = output / 'classes', output / 'dex'
    classes.mkdir()
    dex.mkdir()
    run(args.jdk / f'bin/javac{exe}', '--release', '8', '-classpath', android, '-d', classes, *sorted(source.glob('*.java')))
    run(build_tools / f'd8{bat}', '--min-api', '24', '--lib', android, '--output', dex, *sorted(classes.rglob('*.class')))
    unsigned, aligned, apk = output / 'unsigned.apk', output / 'aligned.apk', output / 'fleet-fixture.apk'
    run(build_tools / f'aapt2{exe}', 'link', '--manifest', source / 'AndroidManifest.xml', '-I', android, '-o', unsigned)
    with zipfile.ZipFile(unsigned, 'a') as archive:
        archive.write(dex / 'classes.dex', 'classes.dex')
        for media in [video, audio]:
            archive.write(media, 'assets/' + media.name)
    run(build_tools / f'zipalign{exe}', '-f', '4', unsigned, aligned)
    key = output / 'fixture.keystore'
    run(args.jdk / f'bin/keytool{exe}', '-genkeypair', '-keystore', key, '-storepass', 'android',
        '-keypass', 'android', '-alias', 'fixture', '-keyalg', 'RSA', '-validity', '30',
        '-dname', 'CN=SDK37 disposable fixture', '-noprompt')
    run(build_tools / f'apksigner{bat}', 'sign', '--ks', key, '--ks-pass', 'pass:android',
        '--key-pass', 'pass:android', '--out', apk, aligned)
    run(build_tools / f'apksigner{bat}', 'verify', apk)
    result = {'apk': str(apk), 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
              'color': '#123456', 'mediaSeconds': 120, 'media': 'generated color and 440 Hz sine'}
    (output / 'build.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
