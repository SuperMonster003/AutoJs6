// utils/properties.mjs

import * as fs from 'node:fs';
import * as fsp from 'node:fs/promises';
import { compareVersionStrings } from './versioning.mjs';
import { generatePropertiesFileTimestamp } from './date.mjs';
import { sortByMap } from './sorting.mjs';

/**
 * Convert JS string to .properties format escaping rules (store format)
 * - Escape: backslash, whitespace/control chars, delimiters (= :), comment chars (# !)
 * - All non-ASCII chars are encoded as \uXXXX (aligned with java.util.Properties.store)<br>
 * zh-CN:<br>
 * 将 JS 字符串按 .properties 规范转义 (store 格式)
 * - 转义: backslash, 空白/控制字符, 分隔符 (= :), 注释首字符 (# !)
 * - 非 ASCII 均编码为 \uXXXX (与 java.util.Properties.store 对齐)
 *
 * @example string
 * escapeProperty('https://www.example.com'); // 'https\://www.example.com'
 * escapeProperty('a==b'); // 'a\=\=b'
 * escapeProperty('\n\r\t\f'); // '\n\r\t\f'
 *
 * escapeProperty('#comment', true); // \#comment
 * escapeProperty('#comment', false); // \#comment
 * escapeProperty('comment#today', true); // comment\#today
 * escapeProperty('comment#today', false); // comment#today
 *
 * escapeProperty(' comment', true); // \ comment
 * escapeProperty(' comment', false); // \ comment
 * escapeProperty('comment today', true); // comment\ today
 * escapeProperty('comment today', false); // comment today
 *
 * @param {string} str
 * @param {boolean} isKey
 * @returns {string}
 */
function escapeProperty(str, isKey) {
    if (!str) return '';
    let out = '';
    for (let i = 0; i < str.length; i++) {
        const ch = str[i];
        const code = ch.codePointAt(0);
        // Handle non-ASCII or control chars uniformly.
        // zh-CN: 统一处理非 ASCII 或控制字符.
        if (code < 0x20 || code > 0x7e) {
            if (ch === '\t') {
                out += '\\t';
                continue;
            }
            if (ch === '\n') {
                out += '\\n';
                continue;
            }
            if (ch === '\r') {
                out += '\\r';
                continue;
            }
            if (ch === '\f') {
                out += '\\f';
                continue;
            }
            // 其他非 ASCII -> \uXXXX
            const hex = code.toString(16).padStart(4, '0');
            out += '\\u' + hex.slice(-4);
            continue;
        }
        switch (ch) {
            case '\\':
                out += '\\\\';
                break;
            case '=':
            case ':':
                // Escape both key and value for compatibility.
                // zh-CN: 在 key 和 value 中都转义, 保证兼容性.
                out += '\\' + ch;
                break;
            case ' ':
                // Spaces in key need to be escaped; leading spaces in value need to be escaped.
                // zh-CN: key 中任意空格需要转义; value 的前导空格需要转义.
                if (isKey || out === '') out += '\\ ';
                else out += ' ';
                break;
            case '\t':
            case '\n':
            case '\r':
            case '\f':
                // Redundant protection, control chars have been handled above.
                // zh-CN: 冗余保护, 已在上方控制字符分支处理.
                out += ch === '\t' ? '\\t'
                    : ch === '\n' ? '\\n'
                        : ch === '\r' ? '\\r'
                            : '\\f';
                break;
            case '#':
            case '!':
                // When used as key or as first char of value, need to escape to avoid being parsed as comment.
                // zh-CN: 作为 key 时或 value 的首字符, 为避免被解析为注释, 需转义.
                if (isKey || out === '') out += '\\' + ch;
                else out += ch;
                break;
            default:
                out += ch;
        }
    }
    return out;
}

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
                    // Illegal \u sequence, keep as literal.
                    // zh-CN: 非法 \u 序列, 按字面量保留.
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
                // Unknown escape sequence, keep (but without the preceding "\").
                // zh-CN: 未知转义, 保留 (但不包含前面的 "\").
                out += next;
        }
    }
    return out;
}

/**
 * @param {string} text
 * @param {MapSortable['sort']} [sortingPattern=null]
 * @returns {Map<string, string>}
 */
export function parseProperties(text, sortingPattern = null) {
    const props = new Map();
    if (!text) return props;

    const lines = [];
    const rawLines = text.split(/\r?\n/);

    // Merge continuation lines (lines ending with an unescaped backslash).
    // zh-CN: 合并续行 (以反斜杠结尾且反斜杠未被转义).
    for (let i = 0; i < rawLines.length; i++) {
        let line = rawLines[i];
        if (line == null) continue;

        // Remove trailing CR (for \r\n already split cases, usually not needed).
        // zh-CN: 去除行尾 CR (兼容 \r\n 已 split 的情况, 一般无需此步).
        line = line.replace(/\r$/, '');

        // Merge continuation lines.
        // zh-CN: 合并续行.
        while (true) {
            // Count consecutive backslashes at end, odd number indicates continuation.
            // zh-CN: 统计结尾连续反斜杠数量, 奇数表示续行.
            let backslashes = 0;
            for (let j = line.length - 1; j >= 0 && line[j] === '\\'; j--) backslashes++;
            const isContinuation = backslashes % 2 === 1;

            if (!isContinuation) break;
            const next = rawLines[++i];
            if (next == null) break;
            // Remove one continuation backslash, append next line, newline is discarded at continuation point per spec.
            // zh-CN: 去掉一个续行用的反斜杠, 再拼接后续行, 续行处按规范会吞掉换行.
            line = line.slice(0, -1) + next;
        }
        lines.push(line);
    }

    for (const raw of lines) {
        const line = raw.trim();
        if (!line || line.startsWith('#') || line.startsWith('!')) continue;

        // Key-value separator: first =/: or unescaped whitespace.
        // zh-CN: 键值分隔: 第一个 =/: 或未转义空白.
        let key = '';
        let value = '';
        let sepIdx = -1;

        // Scan character by character to identify unescaped separators.
        // zh-CN: 逐字符扫描, 识别未转义的分隔符.
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
            // If separator is whitespace, value should start from first non-whitespace.
            // zh-CN: 如果分隔符是空白, value 应该从第一个非空白处开始.
            if (/^\s$/.test(line[sepIdx])) {
                value = value.replace(/^\s+/, '');
            }
        }

        // In spec, leading whitespace in key can be separator, trailing whitespace should be removed.
        // zh-CN: 规范里 key 前部空白可作为分隔符, 末尾空白需要去掉.
        key = key.replace(/\s+$/, '');
        const k = unescapeProperty(key);
        const v = unescapeProperty(value.trim());

        if (k) props.set(k, v);
    }
    return new Map(sortByMap(props.entries(), sortingPattern));
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {GradleMapRwOptions} [options={}]
 * @returns {Promise<Map<string, string>>}
 */
export async function readProperties(filePath = '../version.properties', { encoding = 'utf8', sort = null } = {}) {
    const text = await fsp.readFile(filePath, { encoding });
    return parseProperties(text, sort);
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {GradleMapRwOptions} [options={}]
 * @returns {Map<string, string>}
 */
export function readPropertiesSync(filePath = '../version.properties', { encoding = 'utf8', sort = null } = {}) {
    const text = fs.readFileSync(filePath, { encoding });
    return parseProperties(text, sort);
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {Map<string,string>} [map={}]
 * @param {GradleMapRwOptions} [options={}]
 * @returns {Promise<void>}
 */
export async function writePropertiesWithMap(filePath = '../version.properties', map = new Map(), { encoding = 'utf8', sort = null } = {}) {
    const lines = [];
    sortByMap(map.entries(), sort).forEach(([ key, value ]) => {
        if (value == null) return;
        const k = escapeProperty(String(key), true);
        const v = escapeProperty(String(value), false);
        lines.push(`${k}=${v}`);
    });
    lines.unshift(generatePropertiesFileTimestamp());
    const text = lines.join('\n') + '\n';
    return fsp.writeFile(filePath, text, { encoding });
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {Map<string,string>} [map={}]
 * @param {GradleMapRwOptions} [options={}]
 * @returns {void}
 */
export function writePropertiesSyncWithMap(filePath = '../version.properties', map = new Map(), { encoding = 'utf8', sort = null } = {}) {
    const lines = [];
    sortByMap(map.entries(), sort).forEach(([ key, value ]) => {
        if (value == null) return;
        const k = escapeProperty(String(key), true);
        const v = escapeProperty(String(value), false);
        lines.push(`${k}=${v}`);
    });
    lines.unshift(generatePropertiesFileTimestamp());
    const text = lines.join('\n') + '\n';
    return fs.writeFileSync(filePath, text, { encoding });
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {string[]} [lines=[]]
 * @param {GradleDataRwOptions} [options={}]
 * @returns {Promise<void>}
 */
export async function writePropertiesWithLines(filePath = '../version.properties', lines = [], { encoding = 'utf8' } = {}) {
    const results = [];
    lines.forEach((line) => {
        if (line.startsWith('#') || line.startsWith('!')) {
            results.push(line);
        }
        const [ key, value ] = parseProperties(line).entries().next().value || [];
        if (value == null) return;
        const k = escapeProperty(String(key), true);
        const v = escapeProperty(String(value), false);
        results.push(`${k}=${v}`);
    });
    results.unshift(generatePropertiesFileTimestamp());
    const text = results.join('\n') + '\n';
    return fsp.writeFile(filePath, text, { encoding });
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {string[]} [lines=[]]
 * @param {GradleDataRwOptions} [options={}]
 * @returns {void}
 */
export function writePropertiesSyncWithLines(filePath = '../version.properties', lines = [], { encoding = 'utf8' } = {}) {
    const results = [];
    lines.forEach((line) => {
        if (line.startsWith('#') || line.startsWith('!')) {
            results.push(line);
        }
        const [ key, value ] = parseProperties(line).entries().next().value || [];
        if (value == null) return;
        const k = escapeProperty(String(key), true);
        const v = escapeProperty(String(value), false);
        results.push(`${k}=${v}`);
    });
    results.unshift(generatePropertiesFileTimestamp());
    const text = results.join('\n') + '\n';
    return fs.writeFileSync(filePath, text, { encoding });
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {Object} options
 * @param {BufferEncoding} [options.encoding='utf8']
 * @returns {string}
 */
export function getMinSupportedAgpVersion(filePath = '../version.properties', { encoding = 'utf8' } = {}) {
    let minSupportedVersion = null;
    Array.from(readPropertiesSync(filePath, { encoding }).entries()).forEach(([ key, value ]) => {
        if (!/agp.version.*min.supported|min.supported.*agp.version/i.test(key)) return;
        if (minSupportedVersion === null || compareVersionStrings(value, minSupportedVersion) < 0) {
            minSupportedVersion = value;
        }
    });
    if (!minSupportedVersion) {
        throw new Error('Could not determine minSupportedAgpVersion from "version.properties" file');
    }
    return minSupportedVersion;
}

/**
 * @param {string} [filePath='../version.properties']
 * @param {Object} options
 * @param {BufferEncoding} [options.encoding='utf8']
 * @returns {string}
 */
export function getMinSupportedGradleVersion(filePath = '../version.properties', { encoding = 'utf8' } = {}) {
    let minSupportedVersion = null;
    Array.from(readPropertiesSync(filePath, { encoding }).entries()).forEach(([ key, value ]) => {
        if (!/gradle.version.*min.supported|min.supported.*gradle.version/i.test(key)) return;
        if (minSupportedVersion === null || compareVersionStrings(value, minSupportedVersion) < 0) {
            minSupportedVersion = value;
        }
    });
    if (!minSupportedVersion) {
        throw new Error('Could not determine minSupportedGradleVersion from "version.properties" file');
    }
    return minSupportedVersion;
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
 * @returns {{ minSupportedJavaVersionInt: number, minSuggestedJavaVersionInt: number, maxSupportedJavaVersionInt: number }}
 */
export function getJavaVersionInfo(filePath = '../version.properties', { encoding = 'utf8' } = {}) {
    let minSuggestedVer = Infinity;
    let minSupportedVer = Infinity;
    let maxSupportedVer = -Infinity;

    Array.from(readPropertiesSync(filePath, { encoding }).entries()).forEach(([ key, value ]) => {
        const currentVer = parseInt(value, 10);
        if (/java.version.*min.suggested|min.suggested.*java.version/i.test(key)) {
            minSuggestedVer = Math.min(minSuggestedVer, currentVer);
        } else if (/java.version.*min.supported|min.supported.*java.version/i.test(key)) {
            minSupportedVer = Math.min(minSupportedVer, currentVer);
        } else if (/java.version.*max.supported|max.supported.*java.version/i.test(key)) {
            maxSupportedVer = Math.max(maxSupportedVer, currentVer);
        }
    });

    /**
     * @param {number} target
     * @param {string} variableName
     * @throws {Error}
     */
    const validate = (target, variableName) => {
        if (!isFinite(target)) throw new Error(`Could not determine ${variableName} from "version.properties" file`);
    };

    validate(minSuggestedVer, 'minSuggestedJavaVersionInt');
    validate(minSupportedVer, 'minSupportedJavaVersionInt');
    validate(maxSupportedVer, 'maxSupportedJavaVersionInt');

    return {
        minSuggestedJavaVersionInt: minSuggestedVer,
        minSupportedJavaVersionInt: minSupportedVer,
        maxSupportedJavaVersionInt: maxSupportedVer,
    };
}
