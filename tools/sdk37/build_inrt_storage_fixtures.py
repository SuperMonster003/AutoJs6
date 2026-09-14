"""Create disposable storage-test APKs from an inrt debug APK, without changing runtime code.

Only assets/project and manifest versionCode/storage declarations are changed. The
fixtures keep the template package name and use a separate test key. Physical
testing requires --disable-boot and the runner's explicit physical-device mode.
This does not test the APK Builder plugin, encryption, RuntimeKit distribution,
or release signing.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile


def manifest(data, version, omit_storage):
    """Edit typed AXML values; preserve the template resource table and SDK values."""
    data = bytearray(data)
    strings = []
    chunks = []
    offset = 8
    skip_permission = False
    changed = 0
    while offset < len(data):
        kind, header, size = struct.unpack_from('<HHI', data, offset)
        assert size >= header >= 8 and offset + size <= len(data)
        chunk = bytearray(data[offset:offset + size])
        if kind == 1:
            count, _, flags, start, _ = struct.unpack_from('<5I', chunk, 8)
            for index in range(count):
                pos = start + struct.unpack_from('<I', chunk, header + index * 4)[0]
                if flags & 0x100:
                    def length(p):
                        first = chunk[p]
                        return (((first & 0x7f) << 8) | chunk[p + 1], p + 2) if first & 0x80 else (first, p + 1)
                    _, pos = length(pos)
                    length_bytes, pos = length(pos)
                    strings.append(bytes(chunk[pos:pos + length_bytes]).decode('utf-8'))
                else:
                    length_chars = struct.unpack_from('<H', chunk, pos)[0]
                    pos += 2
                    if length_chars & 0x8000:
                        length_chars = ((length_chars & 0x7fff) << 16) | struct.unpack_from('<H', chunk, pos)[0]
                        pos += 2
                    strings.append(bytes(chunk[pos:pos + length_chars * 2]).decode('utf-16le'))
        if kind == 0x102:
            name = strings[struct.unpack_from('<I', chunk, 20)[0]]
            attribute_start, attribute_size, count = struct.unpack_from('<3H', chunk, 24)
            assert attribute_size == 20
            for index in range(count):
                at = 16 + attribute_start + index * attribute_size
                attribute_name = strings[struct.unpack_from('<I', chunk, at + 4)[0]]
                value = struct.unpack_from('<I', chunk, at + 16)[0]
                if name == 'manifest' and attribute_name == 'versionCode':
                    assert chunk[at + 15] == 0x10
                    struct.pack_into('<I', chunk, at + 8, 0xffffffff)
                    struct.pack_into('<I', chunk, at + 16, version)
                    changed += 1
                if omit_storage and name == 'uses-permission' and attribute_name == 'name':
                    assert chunk[at + 15] == 3
                    skip_permission = strings[value] in {
                        'android.permission.MANAGE_EXTERNAL_STORAGE',
                        'android.permission.READ_EXTERNAL_STORAGE',
                        'android.permission.WRITE_EXTERNAL_STORAGE',
                    }
        if not skip_permission:
            chunks.append(chunk)
        if kind == 0x103:
            skip_permission = False
        offset += size
    assert changed == 1
    result = data[:8] + b''.join(chunks)
    struct.pack_into('<I', result, 4, len(result))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--template', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--key', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--version-code', type=int, required=True)
    parser.add_argument('--shared-file', required=True)
    parser.add_argument('--disable-boot', action='store_true', help='Disable fixture boot execution; required for physical tests')
    args = parser.parse_args()
    assert re.fullmatch(r'/sdcard/Download/autojs6-sdk37-storage-[\w-]+\.txt', args.shared_file)
    assert 0 < args.version_code < 2 ** 31 - 4
    args.output.mkdir(parents=True, exist_ok=False)
    env = dict(os.environ, JAVA_HOME=str(args.jdk.resolve()))
    bt = args.sdk / 'build-tools/37.0.0'
    exe, bat = ('.exe', '.bat') if os.name == 'nt' else ('', '')

    def run(*command):
        return subprocess.run([str(v) for v in command], env=env, check=True, capture_output=True, text=True).stdout

    if not args.key.exists():
        args.key.parent.mkdir(parents=True, exist_ok=True)
        run(args.jdk / ('bin/keytool' + exe), '-genkeypair', '-keystore', args.key,
            '-storepass', 'android', '-keypass', 'android', '-alias', 'fixture',
            '-keyalg', 'RSA', '-validity', '30', '-dname', 'CN=SDK37 storage fixture', '-noprompt')
    records = []
    for index, case in enumerate(('private', 'shared', 'hidden', 'missing')):
        version = args.version_code + index
        shared = case != 'private'
        config = {
            'name': 'SDK37 storage fixture', 'packageName': 'org.autojs.autojs6.inrt',
            'versionName': '1.0.0', 'versionCode': version, 'main': 'main.js',
            'build': {'id': f'storage-{version}-{case}', 'number': version, 'time': 0},
            'launchConfig': {'requiresSharedStorage': shared, 'splashVisible': False,
                             'logsVisible': False, 'launcherVisible': case != 'hidden', 'runOnBoot': not args.disable_boot},
        }
        script = '''let env = android.os.Environment;
let result = {case: CASE, target: context.getApplicationInfo().targetSdkVersion,
  api: android.os.Build.VERSION.SDK_INT, private: false, shared: null,
  manager: android.os.Build.VERSION.SDK_INT >= 30 ? env.isExternalStorageManager() : null,
  legacy: android.os.Build.VERSION.SDK_INT >= 29 ? env.isExternalStorageLegacy() : null};
try {
  files.write('./private-probe.txt', 'private-ok');
  result.private = files.read('./private-probe.txt') === 'private-ok';
  if (SHARED) {
    result.shared = files.read(SHARED_PATH).startsWith('legacy-fixture');
    files.append(SHARED_PATH, '\\nshared-write-ok');
  }
} catch (e) { result.error = String(e); }
files.append(String(context.getFilesDir().getPath()) + '/storage-result.jsonl', JSON.stringify(result) + '\\n');
'''.replace('CASE', json.dumps(case)).replace('SHARED_PATH', json.dumps(args.shared_file)).replace('SHARED', str(shared).lower())
        unsigned = args.output / (case + '-unsigned.apk')
        with zipfile.ZipFile(args.template) as source, zipfile.ZipFile(unsigned, 'w') as dest:
            for entry in source.infolist():
                if entry.filename.startswith('assets/project/') or (entry.filename.startswith('META-INF/') and entry.filename.upper().endswith(('.SF', '.RSA', '.DSA', '.EC', 'MANIFEST.MF'))):
                    continue
                payload = source.read(entry.filename)
                if entry.filename == 'AndroidManifest.xml':
                    payload = manifest(payload, version, case == 'missing')
                dest.writestr(entry, payload)
            dest.writestr('assets/project/project.json', json.dumps(config), compress_type=zipfile.ZIP_DEFLATED)
            dest.writestr('assets/project/main.js', script, compress_type=zipfile.ZIP_DEFLATED)
        aligned, apk = args.output / (case + '-aligned.apk'), args.output / (case + '.apk')
        run(bt / ('zipalign' + exe), '-f', '-P', '16', '4', unsigned, aligned)
        run(bt / ('apksigner' + bat), 'sign', '--ks', args.key, '--ks-pass', 'pass:android',
            '--key-pass', 'pass:android', '--out', apk, aligned)
        signer = run(bt / ('apksigner' + bat), 'verify', '--print-certs', apk)
        badging = run(bt / ('aapt2' + exe), 'dump', 'badging', apk)
        assert "package: name='org.autojs.autojs6.inrt'" in badging
        assert f"versionCode='{version}'" in badging
        assert "application-debuggable" in badging
        if case == 'missing':
            assert "uses-permission: name='android.permission.MANAGE_EXTERNAL_STORAGE'" not in badging
        (args.output / (case + '-badging.txt')).write_text(badging, encoding='utf-8')
        target = int(re.search(r"targetSdkVersion:'(\d+)'", badging)[1])
        records.append({'case': case, 'versionCode': version, 'target': target,
                        'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'signer': signer})
        unsigned.unlink()
        aligned.unlink()
    metadata = {'templateSha256': hashlib.sha256(args.template.read_bytes()).hexdigest(),
                'sharedFile': args.shared_file, 'runOnBoot': not args.disable_boot, 'fixtures': records}
    (args.output / 'fixtures.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    print(json.dumps(records))


if __name__ == '__main__':
    main()
