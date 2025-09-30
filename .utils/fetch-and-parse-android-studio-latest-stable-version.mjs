// fetch-and-parse-android-studio-latest-stable-version.mjs

import * as cheerio from 'cheerio';
import { bytes2GiB } from './utils/format.mjs';
import { fileURLToPath } from 'node:url';
import { getRemoteFileSizeBytes } from './utils/fetch.mjs';

const URL = 'https://developer.android.com/studio?hl=en';

/**
 * @param {string} s
 * @returns {string}
 */
const normalize = (s) => (s ?? '').replace(/\s+/g, ' ').trim();

/**
 * @param {string} filename
 * @param {string} kind
 * @returns {string | null}
 */
function buildDownloadUrlFromFilename(filename, kind) {
    const m = /android-studio-([\d.]+)-/.exec(filename);
    if (!m) return null;
    const version = m[1];
    const urlType = {
        'exe': 'install',
        'zip': 'ide-zips',
        'tar': 'ide-zips',
    }[kind];
    if (!urlType) {
        throw new Error(`Unsupported kind: ${kind}`);
    }
    return `https://redirector.gvt1.com/edgedl/android/studio/${urlType}/${version}/${filename}`;
}

/**
 * @param {string} url
 * @returns {Promise<string>}
 */
async function fetchHtml(url) {
    const res = await fetch(url, {
        headers: {
            'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36',
            'accept-language': 'en',
        },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
    return await res.text();
}

/**
 * @param {string} html
 * @returns {Promise<AndroidStudioStableArchiveItem[]>}
 */
async function parseItemsFromHtml(html) {
    const $ = cheerio.load(html);
    const rows = [];

    /** @type {import('cheerio').Cheerio<import('domhandler').Element>} */
    const tableRows = $('table.download tbody tr');
    await Promise.all(Array.from(tableRows).map(async tr => {
        const tds = $(tr).find('td');
        if (tds.length !== 4) return;

        const platform = normalize($(tds[0]).text());
        if (!/windows|linux/i.test(platform)) return;

        const btn = $(tds[1]).find('button.devsite-dialog-button').first();
        const filename = normalize(btn.text());
        if (!filename || !filename.includes('android-studio')) return;

        const sha256 = normalize($(tds[3]).text());

        // @formatter:off
        const kind = filename.match(/\.exe$/) ? 'exe'
                   : filename.match(/\.zip$/) ? 'zip'
                   : filename.match(/\.tar(\.gz)?$/) ? 'tar'
                   : 'other';
        // @formatter:on

        const url = buildDownloadUrlFromFilename(filename, kind);
        const bytes = await getRemoteFileSizeBytes(url);
        const size = bytes2GiB(bytes);
        rows.push({ platform, filename, size, sha256, url, kind });
    }))
    return rows
        .filter(r => r.kind === 'exe' || r.kind === 'zip' || r.kind === 'tar')
        .sort((_, b) => b.kind === 'exe' ? 1 : b.kind === 'zip' ? 1 : -1);
}

/**
 * Export: Get download information for the latest stable archives (exe, zip, tar)<br>
 * Returns array like: [ { platform, filename, size, sha256, url, kind: 'exe'|'zip'|'tar' } ]<br>
 * zh-CN:<br>
 * 导出: 获取最新稳定版档案的下载信息 (exe, zip, tar)<br>
 * 返回形如: [ { platform, filename, size, sha256, url, kind: 'exe'|'zip'|'tar' } ]
 *
 * @param {string} [sourceUrl=URL]
 * @returns {Promise<AndroidStudioStableArchiveItem[]>}
 */
export async function getLatestStableArchives(sourceUrl = URL) {
    const html = await fetchHtml(sourceUrl);
    const items = await parseItemsFromHtml(html);
    if (!items.length) {
        throw new Error('No latest stable archives found in the page');
    }
    return items;
}

async function main() {
    const items = await getLatestStableArchives(URL);
    console.table(items.map(({ filename, size, url, sha256 }) => ({ filename, size, url, sha256 })));
}

// Determine if this file is being run directly.
// zh-CN: 判断是否为直接执行该文件.
if (fileURLToPath(import.meta.url) === process.argv[1]) {
    main().catch(err => {
        console.error(err);
        process.exitCode = 1;
    });
}
