import assert from 'node:assert/strict';
import test from 'node:test';
import {
    buildUpdatedCommonJson,
    validateLatestStableMetadata,
} from '../sync-platform-version-data.mjs';

const metadata = {
    schemaVersion: 1,
    sourceUrl: 'https://jb.gg/android-studio-releases-list.json',
    name: 'Android Studio Quail 3 | 2026.1.3 Patch 1',
    version: '2026.1.3.8',
    build: 'AI-261.8',
    platformVersion: '2026.1.4',
    channel: 'Patch',
    releaseDate: '2026-08-10',
    downloads: {
        windowsExe: {
            fileName: 'android-studio-quail3-patch1-windows.exe',
            url: 'https://example.test/android-studio-quail3-patch1-windows.exe',
            sizeBytes: 1_507_848_042,
        },
        windowsZip: {
            fileName: 'android-studio-quail3-patch1-windows.zip',
            url: 'https://example.test/android-studio-quail3-patch1-windows.zip',
            sizeBytes: 1_516_955_868,
        },
        linuxTar: {
            fileName: 'android-studio-quail3-patch1-linux.tar.gz',
            url: 'https://example.test/android-studio-quail3-patch1-linux.tar.gz',
            sizeBytes: 1_560_605_310,
        },
    },
};

const datasets = {
    buildVersions: new Map([ [ '261.8', '2026.1.3.8' ] ]),
    codenameVersions: new Map([ [ '2026.1', 'Q' ] ]),
    codenames: new Map([ [ 'Q', 'Quail' ] ]),
};

test('shared Android Studio metadata is checked against all three version maps', () => {
    assert.equal(validateLatestStableMetadata(metadata, datasets).version, '2026.1.3.8');
    assert.throws(
        () => validateLatestStableMetadata({ ...metadata, version: '2026.1.3.9' }, datasets),
        /inconsistent with the build map/,
    );
});

test('shared Android Studio metadata projects to the existing common.json schema', () => {
    const updated = buildUpdatedCommonJson({ keep_me: true }, metadata);
    assert.equal(updated.keep_me, true);
    assert.equal(updated.android_studio_latest_recommended_version_name, metadata.name);
    assert.equal(updated.var_date_android_studio_latest_recommended_version_name, '2026/08/10');
    assert.equal(updated.android_studio_latest_recommended_file_size_of_exe, '1.40 GiB');
    assert.equal(updated.android_studio_latest_recommended_file_size_of_zip, '1.41 GiB');
    assert.equal(updated.android_studio_latest_recommended_file_size_of_tar, '1.45 GiB');
});
