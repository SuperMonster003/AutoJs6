// print-android-version-codes.mjs

import * as fsp from 'node:fs/promises';
import * as path from 'path';

const CSV_PATH = path.resolve(process.cwd(), '../build-logic/ksp-version-codes-processor/src/main/resources/version-codes.csv');

/**
 * Simple CSV parsing: supports double quoted fields, commas and line breaks.<br>
 * zh-CN: 简易 CSV 解析: 支持双引号字段, 逗号, 换行.
 *
 * @param {string} text
 * @returns {string[][]}
 */
function parseCsv(text) {
    /** @type {string[][]} */
    const rows = [];
    /** @type {string[]} */
    const row = [];

    let i = 0;
    let field = '';
    let inQuotes = false;

    const pushField = () => {
        row.push(field);
        field = '';
    };
    const pushRow = () => {
        if (row.length) rows.push(row.slice());
        row.splice(0);
    };

    while (i < text.length) {
        const c = text[i];
        if (inQuotes) {
            if (c === '"') {
                if (i + 1 < text.length && text[i + 1] === '"') {
                    field += '"';
                    i += 2;
                    continue;
                }
                inQuotes = false;
                i++;
                continue;
            }
            field += c;
            i++;
            continue;
        }
        if (c === '"') {
            inQuotes = true;
            i++;
            continue;
        }
        if (c === ',') {
            pushField();
            i++;
            continue;
        }
        if (c === '\r') {
            i++;
            continue;
        }
        if (c === '\n') {
            pushField();
            pushRow();
            i++;
            continue;
        }
        field += c;
        i++;
    }
    // Last cell or last row.
    // zh-CN: 最后一格或最后一行.
    if (field.length || row.length) {
        pushField();
        pushRow();
    }
    return rows;
}

/**
 * @param {string[][]} rows
 * @returns {Object<string, string|number>[]}
 */
function toObjects(rows) {
    if (!rows.length) return [];
    /** @type {string[]} */
    const header = rows[0].map(h => h.trim());
    return rows.slice(1).map(r => {
        /** @type {Object<string, string|number>} */
        const o = {};
        for (let i = 0; i < header.length; i++) {
            const key = header[i];
            const value = (r[i] ?? '').trim();
            o[key] = key === 'API_LEVEL' ? parseInt(value, 10) : value;
        }
        return o;
    });
}

(async function main() {
    const text = await fsp.readFile(CSV_PATH, 'utf8');
    const objs = toObjects(parseCsv(text));
    console.table(objs);
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
