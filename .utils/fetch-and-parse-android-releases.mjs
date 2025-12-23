// fetch-and-parse-android-releases.mjs

import * as cheerio from 'cheerio';
import fetch from 'node-fetch';
import { fileURLToPath } from 'node:url';

const versionCodeBlacklist = [ 'CUR_DEVELOPMENT' ];

const WIKI_URL = 'https://en.wikipedia.org/wiki/Android_version_history';
const ANDROID_VC_URL = 'https://developer.android.com/reference/android/os/Build.VERSION_CODES';

const norm = (/** @type {string} */ s) => (s ?? '').trim().replace(/\s+/g, ' ');
const trimAndNormDash = (/** @type {string} */ s) => s.replace(/\s*[\u002d\u2013\u2014](\s*N\/A)?\s*/ig, '-');
const stripSupportPrefix = (/** @type {string} */ s) => s.replace(/^(un)?supported:\s*/ig, '');
const handleCsvEmptyString = (/** @type {string} */ s) => /^-?$/.test(s) ? '""' : s;

/**
 * @param {AndroidReleaseCsv} o
 * @returns {AndroidReleaseCsv}
 */
function quoteIfNeeded(o) {
    Object.keys(o).forEach((k) => {
        if (typeof o[k] === 'string') {
            o[k] = handleCsvEmptyString(o[k]);
        }
    });
    return o;
}

/**
 * Remove style, script, and sup.reference elements before getting text.<br>
 * zh-CN: 获取文本前, 先移除 style, script 及 sup.reference.
 *
 * @param {Cheerio<DomElement>} cell
 * @returns {string}
 */
function cleanCell(cell) {
    const cloned = cell.clone();
    cloned.find('style,script,sup.reference,span.sr-only').remove();
    return cloned.text().trim().replace(/\s+/g, ' ');
}

/**
 * Fetch official VERSION_CODE and API_LEVEL from Android documentation.<br>
 * zh-CN: 抓取官方 VERSION_CODE 与 API_LEVEL.
 *
 * @example Map<apiLevel, versionCode>
 * @returns {Promise<Map<number, string>>}
 */
async function fetchOfficialVC() {
    const html = await (await fetch(ANDROID_VC_URL)).text();
    const $ = cheerio.load(html);

    /**
     * @example Map<apiLevel, versionCode>
     * @type {Map<number, string>}
     */
    const map = new Map();
    $('h3.api-name').each((_i, h3) => {
        const $h3 = $(h3);
        const versionCode = norm($h3.attr('id') || $h3.text());
        if (!versionCode || versionCodeBlacklist.includes(versionCode.toUpperCase())) return;

        const container = $h3.parent();
        const apiLevelText = container.find('.api-level').first().text();
        const apiLevel = (/* @IIFE */ () => {
            // "Constant Value: X"
            const constValMatched = container.text().match(/Constant\s+Value:\s*(\d+)/i);
            if (constValMatched) return parseInt(constValMatched[1], 10);
            // "Added in API level X"
            const addedInMatched = apiLevelText.match(/API level\s+(\d+)/i);
            if (addedInMatched) return parseInt(addedInMatched[1], 10);
        })();
        if (apiLevel && Number.isFinite(apiLevel)) {
            if (!map.has(apiLevel)) {
                map.set(apiLevel, versionCode);
            }
        }
    });

    return map;
}

/**
 * Fetch all fields except VERSION_CODE from Wikipedia.<br>
 * zh-CN: 抓取维基除 VERSION_CODE 外的字段.
 *
 * @returns {Promise<AndroidReleaseWiki[]>}
 */
async function fetchWikiRows() {
    const $ = cheerio.load(await (await fetch(WIKI_URL)).text());

    const re = /^(Name|Internal\s*codename|Version\s*number(\(s\)|s)?|API\s*level|Release\s*date)$/i;
    const table = $('table.wikitable').filter((_, table) => {
        return Array.from($(table).find('tr th')).filter(th => {
            const extractedText = $(th).text().replace(/\[\d+]/g, '').trim();
            return re.test(extractedText);
        }).length >= 3;
    }).first();

    /** @type {AndroidReleaseWiki[]} */
    const rows = [];

    /**
     * Maintain column state.<br>
     * zh-CN: 维护列状态.
     *
     * @example Record<columnIndex, { text: string; rowSpansLeft: number }>
     * { 2: { text: "Gingerbread"; rowSpansLeft: 3 } }
     *
     * @type {Record<number, { cell: Cheerio<DomElement>; rowSpansLeft: number }>}
     */
    const rowSpansState = {};

    table.find('tr').each((_i, tr) => {
        const $tr = $(tr);
        const tds = $tr.find('td');
        if (!tds.length) return;

        /** @type {Cheerio<DomElement>[]} */
        const cells = [];

        let chosenTdsIdx = 0;

        for (let i = 0; i < 5; i += 1) {
            let cell = null;
            if (i in rowSpansState) {
                cell = rowSpansState[i].cell;
                cells.push(cell);
                rowSpansState[i].rowSpansLeft -= 1;
            } else {
                cell = tds.eq(chosenTdsIdx++);
                if (!cell || cell.length === 0) return;
                cells.push(cell);
                const rowSpansLeft = parseInt(cell.attr('rowspan') || '1', 10) - 1;
                if (rowSpansLeft > 0) {
                    rowSpansState[i] = { cell, rowSpansLeft };
                }
            }
        }

        const [ nameCell, codenameCell, versionCell, apiCell, dateCell ] = cells;

        const name = cleanCell(nameCell);
        const codename = trimAndNormDash(cleanCell(codenameCell));

        // Version prefers data-sort-value (can be on current td or its child elements).
        // zh-CN: Version 优先使用 data-sort-value (可在当前 td 或其子元素上).
        const sortVal = versionCell.attr('data-sort-value') || versionCell.find('[data-sort-value]').attr('data-sort-value');
        const versionText = trimAndNormDash(stripSupportPrefix(sortVal || cleanCell(versionCell)));

        const api = cleanCell(apiCell);
        const date = cleanCell(dateCell);

        rows.push({
            RELEASE_NAME: name,
            INTERNAL_CODENAME: codename,
            PLATFORM_VERSION: versionText,
            API_LEVEL: parseInt(api, 10),
            RELEASE_DATE: date,
        });

        for (const k of Object.keys(rowSpansState)) {
            if (rowSpansState[k].rowSpansLeft === 0) {
                delete rowSpansState[k];
            }
        }
    });

    return rows;
}

/**
 * @returns {Promise<AndroidReleaseCsv[]>}
 */
export async function fetchAndroidReleases() {
    const official = await fetchOfficialVC();
    const wiki = await fetchWikiRows();

    /** @type {Map<number, AndroidReleaseCsv>} */
    const mergedByApi = new Map();

    // First, populate with wiki data.
    // zh-CN: 先放入 wiki 数据.
    for (const r of wiki) {
        const api = r.API_LEVEL;
        if (!Number.isFinite(api)) continue;
        const prev = mergedByApi.get(api) || {
            VERSION_CODE: '', RELEASE_NAME: '', INTERNAL_CODENAME: '',
            PLATFORM_VERSION: '', API_LEVEL: api, RELEASE_DATE: '',
        };
        mergedByApi.set(api, quoteIfNeeded({
            ...prev,
            RELEASE_NAME: r.RELEASE_NAME || prev.RELEASE_NAME,
            INTERNAL_CODENAME: r.INTERNAL_CODENAME || prev.INTERNAL_CODENAME,
            PLATFORM_VERSION: r.PLATFORM_VERSION || prev.PLATFORM_VERSION,
            RELEASE_DATE: r.RELEASE_DATE || prev.RELEASE_DATE,
        }));
    }

    // Override VERSION_CODE with official data.
    // zh-CN: 用 official 覆盖 VERSION_CODE.
    for (const [ api, versionCode ] of official.entries()) {
        const prev = mergedByApi.get(api) || {
            VERSION_CODE: '', RELEASE_NAME: '', INTERNAL_CODENAME: '',
            PLATFORM_VERSION: '', API_LEVEL: api, RELEASE_DATE: '',
        };
        mergedByApi.set(api, { ...prev, VERSION_CODE: versionCode, API_LEVEL: api });
    }

    // Output: Sort by API_LEVEL in descending order,
    // keep only records with VERSION_CODE (officially released/named).
    // zh-CN: 输出: 按 API_LEVEL 降序, 仅保留存在 VERSION_CODE 的记录 (即官方已发布/命名的).
    return Array.from(mergedByApi.values())
        .filter(r => r.VERSION_CODE)
        .sort((a, b) => b.API_LEVEL - a.API_LEVEL);
}

async function main() {
    console.table(await fetchAndroidReleases());
}

// Determine if this file is being run directly.
// zh-CN: 判断是否为直接执行该文件.
if (fileURLToPath(import.meta.url) === process.argv[1]) {
    main().catch(err => {
        console.error(err);
        process.exitCode = 1;
    });
}
