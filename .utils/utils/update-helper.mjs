// utils/update-helper.mjs

import * as fs from 'node:fs';
import * as path from 'node:path';
import { parseProperties, readPropertiesSync, writePropertiesSyncWithLines, writePropertiesSyncWithMap } from './properties.mjs';
import { printListDiffs, printMapDiffs } from './print.mjs';
import { sortByList, sortByMap } from './sorting.mjs';
import { generatePropertiesFileTimestamp } from './date.mjs';

/**
 * @param {string} filename
 * @param {Map<string, string>} map
 * @param {GradleMapUpdateOptions} [options={}]
 * @returns {Promise<void>}
 */
export async function updateGradleMapData(filename, map, options = {}) {
    const niceName = filename.endsWith('.properties') ? filename : `${filename}.properties`;
    const filePath = path.resolve(process.cwd(), `../gradle/data/${niceName}`);
    if (!fs.existsSync(filePath)) {
        throw new Error(`File not found: ${filePath}`);
    }
    const rwOptions = {
        encoding: options.encoding || 'utf8',
        sort: options.sort,
    };
    const original = readPropertiesSync(filePath, rwOptions);
    const updated = new Map(sortByMap(map.entries(), options.sort));
    if (!shallowEqualMaps(original, updated)) {
        writePropertiesSyncWithMap(filePath, updated, rwOptions);
        console.log(`[${niceName}] Updated` + (options.label ? ` (${options.label})` : ''));
        printMapDiffs(original, updated);
    } else {
        // console.log(`[${filename}] No update needed` + (options.label ? ` (${options.label})` : ''));
    }
}

/**
 * @param {string} filename
 * @param {Set<string>} list
 * @param {GradleListUpdateOptions} [options={}]
 * @returns {Promise<void>}
 */
export async function updateGradleListData(filename, list, options = {}) {
    const niceName = filename.endsWith('.list') ? filename : `${filename}.list`;
    const filePath = path.resolve(process.cwd(), `../gradle/data/${niceName}`);
    if (!fs.existsSync(filePath)) {
        throw new Error(`File not found: ${filePath}`);
    }
    const rwOptions = {
        encoding: options.encoding || 'utf8',
        sort: options.sort,
    };
    const original = fs.readFileSync(filePath, { encoding: rwOptions.encoding })
        .split('\n')
        .map(line => line.trim())
        .filter(line => line && !line.startsWith('#'));
    const niceList = Array.from(list)
        .map(line => line.trim())
        .filter(line => line && !line.startsWith('#'));
    const updated = sortByList(niceList, options.sort);
    if (!shallowEqualLists(original, updated)) {
        fs.writeFileSync(filePath, generatePropertiesFileTimestamp() + '\n' + updated.join('\n') + '\n', { encoding: rwOptions.encoding });
        console.log(`[${niceName}] Updated` + (options.label ? ` (${options.label})` : ''));
        printListDiffs(original, updated);
    } else {
        // console.log(`[${filename}] No update needed` + (options.label ? ` (${options.label})` : ''));
    }
}

/**
 * @param {string} filename
 * @param {string[]} lines
 * @param {GradleLinesUpdateOptions} [options={}]
 * @returns {Promise<void>}
 */
export async function updateGradleLinesData(filename, lines, options = {}) {
    const niceName = filename.endsWith('.properties') ? filename : `${filename}.properties`;
    const filePath = path.resolve(process.cwd(), `../gradle/data/${niceName}`);
    if (!fs.existsSync(filePath)) {
        throw new Error(`File not found: ${filePath}`);
    }
    const rwOptions = {
        encoding: options.encoding || 'utf8',
    };
    const original = readPropertiesSync(filePath, rwOptions);
    const linesToCheck = parseProperties(lines.join('\n'));
    if (!shallowEqualMaps(original, linesToCheck)) {
        writePropertiesSyncWithLines(filePath, lines, rwOptions);
        console.log(`[${niceName}] Updated` + (options.label ? ` (${options.label})` : ''));
        printMapDiffs(original, linesToCheck);
    } else {
        // console.log(`[${filename}] No update needed` + (options.label ? ` (${options.label})` : ''));
    }
}

/**
 * @param {Map<string, string>} a
 * @param {Map<string, string>} b
 * @returns {boolean}
 */
function shallowEqualMaps(a, b) {
    if (a === b) return true;
    if (!(a instanceof Map) || !(b instanceof Map)) return false;
    if (a.size !== b.size) return false;
    for (const [ k, v ] of a) {
        if (!b.has(k)) return false;
        if (b.get(k) !== v) return false;
    }
    return true;
}

/**
 * @param {string[]} a
 * @param {string[]} b
 * @returns {boolean}
 */
function shallowEqualLists(a, b) {
    if (a === b) return true;
    if (!Array.isArray(a) || !Array.isArray(b)) return false;
    if (a.length !== b.length) return false;
    for (let i = 0; i < a.length; i++) {
        if (a[i] !== b[i]) return false;
    }
    return true;
}
