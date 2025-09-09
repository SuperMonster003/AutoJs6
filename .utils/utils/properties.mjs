// utils/properties.mjs

import * as fs from 'node:fs';
import * as fsp from 'node:fs/promises';
import { compareVersionStrings } from './versioning.mjs';

/**
 * @param {string} str
 * @returns {string}
 */
function unescapeProperty(str) {
    let i = 0, out = '';
    while (i < str.length) {
        const ch = str[i++];
        if (ch !== '\\') {
            out += ch;
            continue;
        }
        const next = str[i++];
        switch (next) {
            case 't':
                out += '\t';
                break;
            case 'n':
                out += '\n';
                break;
            case 'r':
                out += '\r';
                break;
            case 'f':
                out += '\f';
                break;
            case 'u': {
                const hex = str.slice(i, i + 4);
                if (/^[0-9a-fA-F] {4}$/.test(hex)) {
                    out += String.fromCharCode(parseInt(hex, 16));
                    i += 4;
                } else {
                    // 非法 \u 序列, 按字面量保留
                    out += '\\u';
                }
                break;
            }
            case ':':
            case '=':
            case ' ':
            case '\\':
                out += next;
                break;
            default:
                // 未知转义, 保留第二个字符
                out += next;
        }
    }
    return out;
}

/**
 * @param {string} text
 * @returns {Object<string, string>}
 */
export function parseProperties(text) {
    const props = Object.create(null);
    if (!text) return props;

    const lines = [];
    const rawLines = text.split(/\r?\n/);

    // 合并续行 (以反斜杠结尾且反斜杠未被转义)
    for (let i = 0; i < rawLines.length; i++) {
        let line = rawLines[i];
        if (line == null) continue;

        // 去除行尾 CR (兼容 \r\n 已 split 的情况, 一般无需此步)
        line = line.replace(/\r$/, '');

        // 合并续行
        while (true) {
            // 统计结尾连续反斜杠数量, 奇数表示续行
            let backslashes = 0;
            for (let j = line.length - 1; j >= 0 && line[j] === '\\'; j--) backslashes++;
            const isContinuation = backslashes % 2 === 1;

            if (!isContinuation) break;
            const next = rawLines[++i];
            if (next == null) break;
            // 去掉一个续行用的反斜杠, 再拼接后续行, 续行处按规范会吞掉换行
            line = line.slice(0, -1) + next;
        }
        lines.push(line);
    }

    for (const raw of lines) {
        const line = raw.trim();
        if (!line || line.startsWith('#') || line.startsWith('!')) continue;

        // 键值分隔: 第一个 =/: 或未转义空白
        let key = '';
        let value = '';
        let sepIdx = -1;

        // 逐字符扫描, 识别未转义的分隔符
        let escaped = false;
        for (let i = 0; i < line.length; i++) {
            const ch = line[i];
            if (!escaped && (ch === '=' || ch === ':')) {
                sepIdx = i;
                break;
            }
            if (!escaped && /\s/.test(ch)) {
                sepIdx = i;
                break;
            }
            escaped = ch === '\\' && !escaped;
            if (!escaped && ch !== '\\') escaped = false;
        }

        if (sepIdx === -1) {
            key = line;
            value = '';
        } else {
            key = line.slice(0, sepIdx);
            value = line.slice(sepIdx + 1);
            // 如果分隔符是空白, value 应该从第一个非空白处开始
            if (/^\s$/.test(line[sepIdx])) {
                value = value.replace(/^\s+/, '');
            }
        }

        key = key.replace(/\s+$/, ''); // 规范里 key 前部空白可作为分隔符, 末尾空白需要去掉
        const k = unescapeProperty(key);
        const v = unescapeProperty(value.trim());

        if (k) props[k] = v;
    }
    return props;
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {Object} options
 * @param {BufferEncoding} [options.encoding='utf8']
 * @returns {Promise<Object<string, string>>}
 */
export async function readProperties(filePath = '../version.properties', { encoding = 'utf8' } = {}) {
    const text = await fsp.readFile(filePath, { encoding });
    return parseProperties(text);
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {Object} options
 * @param {BufferEncoding} [options.encoding='utf8']
 * @returns {Object<string, string>}
 */
export function readPropertiesSync(filePath = '../version.properties', { encoding = 'utf8' } = {}) {
    const text = fs.readFileSync(filePath, { encoding });
    return parseProperties(text);
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {Object} options
 * @param {BufferEncoding} [options.encoding='utf8']
 * @returns {string}
 */
export function getMinSupportedAgpVersion(filePath = '../version.properties', { encoding = 'utf8' } = {}) {
    let minSupportedVersion = null;
    Object.entries(readPropertiesSync(filePath, { encoding })).forEach(([ key, value ]) => {
        if (!/agp.version.*min.supported|min.supported.*agp.version/i.test(key)) return;
        if (minSupportedVersion === null || compareVersionStrings(value, minSupportedVersion) < 0) {
            minSupportedVersion = value;
        }
    });
    return minSupportedVersion ?? '8.0';
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {Object} options
 * @param {BufferEncoding} [options.encoding='utf8']
 * @returns {string}
 */
export function getMinSupportedGradleVersion(filePath = '../version.properties', { encoding = 'utf8' } = {}) {
    let minSupportedVersion = null;
    Object.entries(readPropertiesSync(filePath, { encoding })).forEach(([ key, value ]) => {
        if (!/gradle.version.*min.supported|min.supported.*gradle.version/i.test(key)) return;
        if (minSupportedVersion === null || compareVersionStrings(value, minSupportedVersion) < 0) {
            minSupportedVersion = value;
        }
    });
    return minSupportedVersion ?? '8.0';
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {Object} options
 * @param {BufferEncoding} [options.encoding='utf8']
 * @returns {number}
 */
export function getMinSupportedJavaVersionInt(filePath = '../version.properties', { encoding = 'utf8' } = {}) {
    return getJavaVersionInfo(filePath, { encoding }).minSupportedJavaVersionInt;
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {Object} options
 * @param {BufferEncoding} [options.encoding='utf8']
 * @returns {{ currentJavaVersionInt: number, minSupportedJavaVersionInt: number, minSuggestedJavaVersionInt: number, maxSupportedJavaVersionInt: number }}
 */
export function getJavaVersionInfo(filePath = '../version.properties', { encoding = 'utf8' } = {}) {
    let currentVersion = 0;
    let minSuggestedVersion = 19;
    let minSupportedVersion = 17;
    let maxSupportedVersion = 0;
    Object.entries(readPropertiesSync(filePath, { encoding })).forEach(([ key, value ]) => {
        const versionNumber = parseInt(value, 10);
        if (/^java.version$/i.test(key)) {
            currentVersion = Math.max(currentVersion, versionNumber);
        } else if (/java.version.*min.suggested|min.suggested.*java.version/i.test(key)) {
            minSuggestedVersion = Math.min(minSuggestedVersion, versionNumber);
        } else if (/java.version.*min.supported|min.supported.*java.version/i.test(key)) {
            minSupportedVersion = Math.min(minSupportedVersion, versionNumber);
        } else if (/java.version.*max.supported|max.supported.*java.version/i.test(key)) {
            maxSupportedVersion = Math.max(maxSupportedVersion, versionNumber);
        }
    });
    return {
        currentJavaVersionInt: currentVersion,
        minSuggestedJavaVersionInt: minSuggestedVersion,
        minSupportedJavaVersionInt: minSupportedVersion,
        maxSupportedJavaVersionInt: maxSupportedVersion,
    };
}