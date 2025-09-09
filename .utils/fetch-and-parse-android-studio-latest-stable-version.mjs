// fetch-and-parse-android-studio-latest-stable-version.mjs

import { load } from 'cheerio';
import { fileURLToPath } from 'node:url';

const URL = 'https://developer.android.com/studio?hl=en';

/**
 * @param {string} s
 * @return {string}
 */
function norm(s) {
    return (s ?? '').replace(/\s+/g, ' ').trim();
}

/**
 * @param {string} filename
 * @return {string | null}
 */
function buildDownloadUrlFromFilename(filename) {
    // 例: android-studio-2025.1.2.13-windows.exe
    const m = /android-studio-([\d.]+)-/.exec(filename);
    if (!m) return null;
    const version = m[1];
    return `https://redirector.gvt1.com/edgedl/android/studio/install/${version}/${filename}`;
}

/**
 * @param {string} url
 * @return {Promise<string>}
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
 * @typedef {'exe' | 'zip' | 'other'} RowKind
 */

/**
 * @typedef {Object} Row
 * @property {string} platform
 * @property {string} filename
 * @property {string} size
 * @property {string} sha256
 * @property {string | null} url
 * @property {RowKind} kind
 */
/**
 * @param {string} html
 * @return {Row[]}
 */
function parseWindowsRowsFromHtml(html) {
    const $ = load(html);
    const rows = [];

    /** @type {import('cheerio').Cheerio<import('domhandler').Element>} */
    const tableRows = $('table.download tbody tr');
    tableRows.each((_, tr) => {
        const tds = $(tr).find('td');
        if (tds.length !== 4) return;

        const platform = norm($(tds[0]).text());
        if (!/windows/i.test(platform)) return;

        const btn = $(tds[1]).find('button.devsite-dialog-button').first();
        const filename = norm(btn.text());
        if (!filename || !filename.includes('android-studio')) return;

        const size = norm($(tds[2]).text());
        const sha256 = norm($(tds[3]).text());
        const url = buildDownloadUrlFromFilename(filename);

        rows.push({
            platform,
            filename,
            size,
            sha256,
            url,
            kind: filename.endsWith('.exe')
                ? 'exe'
                : filename.endsWith('.zip')
                    ? 'zip'
                    : 'other',
        });
    });

    return rows
        .filter(r => r.kind === 'exe' || r.kind === 'zip')
        .sort((a) => a.kind === 'exe' ? -1 : 1);
}

/**
 * 导出: 获取最新稳定版 Windows 的下载信息 (exe 与 zip)
 * 返回形如:
 * [
 *   { platform, filename, size, sha256, url, kind: 'exe' },
 *   { platform, filename, size, sha256, url, kind: 'zip' }
 * ]
 *
 * @param {string} [sourceUrl=URL]
 * @returns {Promise<Row[]>}
 */
export async function getLatestStableWindows(sourceUrl = URL) {
    const html = await fetchHtml(sourceUrl);
    const rows = parseWindowsRowsFromHtml(html);
    if (!rows.length) {
        throw new Error('未在页面中找到 Windows 稳定版下载条目');
    }
    return rows;
}

// CLI 模式: 直接运行则打印结果; 被 import 时不执行
async function main() {
    const rows = await getLatestStableWindows(URL);
    for (const r of rows) {
        console.log(`${r.kind.toUpperCase()}:`);
        console.log(`  filename : ${r.filename}`);
        console.log(`  size     : ${r.size}`);
        console.log(`  sha256   : ${r.sha256}`);
        console.log(`  url      : ${r.url}`);
    }
}

// 判断是否为直接执行该文件
if (fileURLToPath(import.meta.url) === process.argv[1]) {
    main().catch(err => {
        console.error(err);
        process.exitCode = 1;
    });
}
