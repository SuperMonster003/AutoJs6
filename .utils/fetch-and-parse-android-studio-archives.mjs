// fetch-and-parse-android-studio-archives.mjs

import { bytes2GiB } from './utils/format.mjs';
import { compareVersionStrings } from './utils/versioning.mjs';
import { fileURLToPath } from 'node:url';
import { getRemoteFileSizeBytes } from './utils/fetch.mjs';
import { readPropertiesSync } from './utils/properties.mjs';

const URL = 'https://jb.gg/android-studio-releases-list.json';

/**
 * @returns {Promise<AndroidStudioReleaseItem[]>}
 */
export async function getAndroidStudioReleases() {
    const res = await fetch(URL);
    if (!res.ok) {
        throw new Error(`Failed to fetch Android Studio releases list: ${res.status} ${res.statusText}`);
    }
    /** @type {AndroidStudioRelease} */
    const json = await res.json();
    if (!Array.isArray(((json || {}).content || {}).item)) {
        throw new Error(`Invalid Android Studio releases list format: ${json}`);
    }
    return json.content.item;
}

/**
 * @param {AndroidStudioReleaseItem[]} releases
 * @returns {AndroidStudioReleaseItem}
 */
export function extractLatestStableRelease(releases) {
    return releases.find(release => {
        return /Release|Patch/i.test(release.channel);
    });
}

/**
 * @param {AndroidStudioReleaseDownloadItem[]} downloadItems
 * @param {RegExp|null} [linkFilter=null]
 * @returns {Promise<AndroidStudioReleaseDownloadItem[]>}
 */
export async function refineDownloadItemsWithRealSize(downloadItems, linkFilter = null) {
    const results = linkFilter
        ? downloadItems.filter(item => linkFilter.test(item.link))
        : [ ...downloadItems ];
    return await Promise.all(results.map(async (item) => {
        item.size = bytes2GiB(await getRemoteFileSizeBytes(item.link));
        return item;
    }));
}

/**
 * @example string
 * 'September 26, 2025' -> 'Sep 26, 2025'
 * @param {string} date
 */
export function formatDate(date) {
    return date.replace(/([A-Z][a-z]{2})\w* (\d+), (\d+)/, '$1 $2, $3');
}

async function main() {
    const props = readPropertiesSync();
    const minSupportedAndroidStudioVersion = props.get('MIN_SUPPORTED_ANDROID_STUDIO_IDE_VERSION');
    const releases = await getAndroidStudioReleases();
    const results = releases.filter(release => {
        return compareVersionStrings(release.version, minSupportedAndroidStudioVersion) >= 0;
    }).map(({ name, date, version, download, channel }) => {
        const windowsZipUrl = download.find(e => e.link.match(/-windows(-exe)?\.zip/i))?.link;
        if (!windowsZipUrl) {
            console.log('Unable to find Windows zip link for:');
            console.log(download.map(e => e.link).join('\n'));
        }
        const stable = /Release|Patch/i.test(channel) || '-';
        return { name, date: formatDate(date), version, stable, 'link for Windows (zip)': windowsZipUrl };
    }).sort((a, b) => {
        return compareVersionStrings(b.version, a.version);
    });
    console.table(results);
}

// Determine if this file is being run directly.
// zh-CN: 判断是否为直接执行该文件.
if (fileURLToPath(import.meta.url) === process.argv[1]) {
    await main().catch((err) => {
        console.error(err);
        process.exitCode = 1;
    });
}
