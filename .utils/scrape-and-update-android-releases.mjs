// scrape-and-update-android-releases.mjs

import * as fs from 'fs';
import * as path from 'path';
import { fetchAndroidReleases } from './fetch-and-parse-android-releases.mjs';
import { printLinesDiffs } from './utils/print.mjs';

const CSV_PATH = path.resolve(process.cwd(), '../build-logic/ksp-version-codes-processor/src/main/resources/version-codes.csv');
const TXT_PATH = path.resolve(process.cwd(), '../build-logic/ksp-version-codes-processor/src/main/resources/version-codes.txt');

const readFileSafe = (/** @type {string} */ p) => (fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : '');

const quoteCsv = (/** @type {string | number} */ v) => {
    const s = String(v ?? '');
    if (s === '""') return s;
    return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
};

/**
 * @param {AndroidReleaseCsv[]} rows
 * @returns {string}
 */
const toCsv = (rows) => {
    /** @type {(keyof AndroidReleaseCsv)[]} */
    const headers = [
        'VERSION_CODE',
        'RELEASE_NAME',
        'INTERNAL_CODENAME',
        'PLATFORM_VERSION',
        'API_LEVEL',
        'RELEASE_DATE',
    ];
    const lines = rows.map(r => headers.map(k => quoteCsv(r[k])).join());
    return [ headers.join(), ...lines ].join('\n');
};

/**
 * @param {AndroidReleaseCsv[]} data
 * @returns {void}
 */
function updateCsv(data) {
    fs.mkdirSync(path.dirname(CSV_PATH), { recursive: true });
    fs.writeFileSync(CSV_PATH, toCsv(data), 'utf8');
}

/**
 * @param {AndroidReleaseCsv[]} data
 * @param {object} options
 * @param {boolean} [options.showIndex=false]
 * @param {{ mode: 'all' | 'not-all-numbers' | 'none', style: 'single' | 'double' }} [options.quote]
 * @param {{ key: string, compare?: (a: AndroidReleaseCsv, b: AndroidReleaseCsv) => number, direction?: 'asc' | 'desc' }} [options.sortBy]
 * @param {{ [key: string]: string }} [options.headerMap]
 * @param {'lower' | 'upper' | 'snake' | 'kebab' | 'title' | 'camel' | 'pascal' | null} [options.headerCase]
 * @param {string[]} [options.headers]
 * @param {{ [key: string]: (v: any, row: AndroidReleaseCsv, header: string) => string }} [options.renderers]
 * @returns {void}
 */
function updateTxt(data, options) {
    const defaultOptions = {
        showIndex: false,
        quote: {
            mode: 'not-all-numbers',
            style: 'single',
        },
        sortBy: null,
        headerMap: null,
        headerCase: null,
        headers: [
            'VERSION_CODE',
            'RELEASE_NAME',
            'INTERNAL_CODENAME',
            'PLATFORM_VERSION',
            'API_LEVEL',
            'RELEASE_DATE',
        ],
        renderers: {},
    };
    const opts = Object.assign(defaultOptions, options ?? {});

    const headers = opts.headers.slice();

    const toWords = (/** @type {any} */ s) => String(s ?? '')
        .replace(/[_\-\s]+/g, ' ')
        .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
        .trim()
        .split(/\s+/);

    /**
     * @param {any} s
     * @returns {string}
     */
    const toCase = (s) => {
        const words = toWords(s);
        switch (opts.headerCase) {
            case 'lower':
                return words.join(' ').toLowerCase();
            case 'upper':
                return words.join(' ').toUpperCase();
            case 'snake':
                return words.map(w => w.toLowerCase()).join('_');
            case 'kebab':
                return words.map(w => w.toLowerCase()).join('-');
            case 'title':
                return words.map(w => w[0] ? (w[0].toUpperCase() + w.slice(1).toLowerCase()) : w).join(' ');
            case 'camel':
                return words.map((w, i) => i === 0 ? w.toLowerCase() : (w[0]?.toUpperCase() + w.slice(1).toLowerCase())).join('');
            case 'pascal':
                return words.map(w => w[0]?.toUpperCase() + w.slice(1).toLowerCase()).join('');
            default:
                return s;
        }
    };

    /**
     * Copy and sort data.<br>
     * zh-CN: 数据拷贝与排序.
     * @type {AndroidReleaseCsv[]}
     */
    const rows = data.slice();
    if (opts.sortBy) {
        if (typeof opts.sortBy === 'function') {
            rows.sort(opts.sortBy);
        } else if (opts.sortBy && typeof opts.sortBy === 'object' && opts.sortBy.key) {
            const key = opts.sortBy.key;
            const dir = (opts.sortBy.direction || 'asc').toLowerCase() === 'desc' ? -1 : 1;
            const cmp = typeof opts.sortBy.compare === 'function'
                ? opts.sortBy.compare
                : (/** @type {AndroidReleaseCsv} */ a, /** @type {AndroidReleaseCsv} */ b) => {
                    const va = a?.[key];
                    const vb = b?.[key];
                    if (va == null && vb == null) return 0;
                    if (va == null) return -1;
                    if (vb == null) return 1;
                    if (va < vb) return -1;
                    if (va > vb) return 1;
                    return 0;
                };
            rows.sort((a, b) => dir * cmp(a, b));
        }
    }

    /**
     * Prepare column values (strings) and determine column quotation in "not-all-numbers" mode.<br>
     * zh-CN: 先把每列值准备好 (字符串), 并在 "not-all-numbers" 模式下做整列判定.
     * @type {string[][]}
     */
    const tableValues = rows.map(r => {
        return headers.map(h => {
            const v = r[h];
            const renderer = opts.renderers?.[h];
            const rendered = renderer ? renderer(v, r, h) : (v ?? '');
            return String(rendered);
        });
    });

    /**
     * Column headers (remapping + case transformation + affixes).<br>
     * zh-CN: 列标题 (重映射 + 大小写 + 前后缀).
     * @type {string[]}
     */
    const headerLabels = headers.map(h => {
        const mapped = opts.headerMap?.[h] ?? h;
        return toCase(mapped);
    });
    const finalHeader = opts.showIndex ? [ '(index)', ...headerLabels ] : headerLabels;

    const qStyle = opts.quote?.style === 'double' ? `"` : `'`;
    const isNumericLike = (/** @type {any} */ v) => /^-?\d+(\.\d+)?$/.test(String(v ?? '').trim());

    /** @type {boolean[]} */
    const quotedColumns = tableValues[0] ? new Array(tableValues[0].length).fill(false) : [];
    if (opts.quote?.mode === 'all') {
        for (let c = 0; c < quotedColumns.length; c++) {
            quotedColumns[c] = true;
        }
    } else if (opts.quote?.mode === 'not-all-numbers' || !opts.quote?.mode) {
        for (let c = 0; c < quotedColumns.length; c++) {
            const col = tableValues.map(row => row[c]);
            const allNumeric = col.length > 0 && col.every(isNumericLike);
            quotedColumns[c] = !allNumeric;
        }
    }

    /**
     * Apply quotes to data.<br>
     * zh-CN: 应用引号到数据.
     * @type {string[][]}
     */
    const quotedTable = tableValues.map(row => {
        return row.map((cell, c) => quotedColumns[c]
            ? qStyle + cell + qStyle
            : cell === `""` ? qStyle + qStyle : cell);
    });

    /**
     * Calculate the width of each column.<br>
     * zh-CN: 计算每列宽度.
     * @type {string[][]}
     */
    const rowsForWidth = [];
    rowsForWidth.push(finalHeader);
    if (opts.showIndex) {
        for (let i = 0; i < quotedTable.length; i++) {
            rowsForWidth.push([ String(i), ...quotedTable[i] ]);
        }
    } else {
        rowsForWidth.push(...quotedTable);
    }
    const colCount = rowsForWidth[0]?.length ?? 0;
    /** @type {number[]} */
    const colWidths = new Array(colCount).fill(0);
    for (const r of rowsForWidth) {
        for (let c = 0; c < colCount; c++) {
            colWidths[c] = Math.max(colWidths[c], String(r[c] ?? '').length);
        }
    }

    /**
     * @param {any} s
     * @param {number} w
     * @returns {string}
     */
    const pad = (s, w) => {
        const str = String(s ?? '');
        const len = str.length;
        return str + (len < w ? ' '.repeat(w - len) : '');
    };
    const joinRow = (/** @type {any[]} */ cells) => `| ${cells.map((v, i) => pad(v, colWidths[i])).join(' | ')} |`;
    const divider = `+-${colWidths.map(w => '-'.repeat(w)).join('-+-')}-+`;

    const lines = [];
    lines.push(divider);
    lines.push(joinRow(finalHeader));
    lines.push(divider);
    if (quotedTable.length > 0) {
        for (let i = 0; i < quotedTable.length; i++) {
            const cells = opts.showIndex ? [ String(i), ...quotedTable[i] ] : quotedTable[i];
            lines.push(joinRow(cells));
        }
    }
    lines.push(divider);

    fs.mkdirSync(path.dirname(TXT_PATH), { recursive: true });
    fs.writeFileSync(TXT_PATH, lines.join('\n'), 'utf8');
}

(async function main() {
    const filename = path.basename(CSV_PATH);
    const releases = await fetchAndroidReleases();
    const prev = readFileSafe(CSV_PATH).replaceAll('\r\n', '\n');
    if (prev !== toCsv(releases)) {
        updateCsv(releases);
        updateTxt(releases, {
            showIndex: false,
            quote: {
                mode: 'none',
                style: 'single',
            },
        });
        console.log(`[${filename}] Updated (Android releases)`);
        printLinesDiffs(prev, toCsv(releases));
    } else {
        // console.log('[${filename}] No update needed (Android releases)');
    }
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
