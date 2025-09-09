// utils/versioning.mjs

const SUFFIX_PRIORITY = { '': 10, 'alpha': 1, 'beta': 2, 'canary': 3, 'rc': 5 };

/**
 * @param {string} version
 * @return { [number[], [string, number]]}
 */
export function toVersionParts(version) {
    const parts = version.split(/[\s+-]/);
    const numberParts = parts[0].split('.').map(part => {
        const num = parseInt(String(part), 10);
        if (Number.isNaN(num)) {
            throw new Error(`Invalid version part: '${part}' in version: '${version}'`);
        }
        return num;
    });

    // 解析后缀, 如 Alpha2 / Beta / RC1 等; 默认数字为 1
    const suffixPattern = /([A-Za-z]+)\s*(\d*)|([A-Za-z]*)\s*(\d+)/;
    const suffixStr = parts[1] || '';
    const m = suffixStr.match(suffixPattern);
    if (!m) return [ numberParts, [ '', 0 ] ];

    const suffixName = m[1] || '';
    const suffixNumber = parseInt(m[2] || '1', 10);
    return [ numberParts, [ suffixName, Number.isNaN(suffixNumber) ? 1 : suffixNumber ] ];
}

/**
 * @param {number[]} a
 * @param {number[]} b
 * @return {number}
 */
export function compareVersionParts(a, b) {
    const max = Math.max(a.length, b.length);
    for (let i = 0; i < max; i++) {
        const x = a[i] ?? 0;
        const y = b[i] ?? 0;
        if (x !== y) return x > y ? 1 : -1;
    }
    return 0;
}

/**
 * @param {[string, number]} s1
 * @param {[string, number]} s2
 * @return {number}
 */
export function compareVersionSuffix(s1, s2) {
    const [ name1Raw, num1 ] = s1;
    const [ name2Raw, num2 ] = s2;
    const name1 = name1Raw.toLowerCase();
    const name2 = name2Raw.toLowerCase();
    const p1 = SUFFIX_PRIORITY[name1] ?? Number.MAX_SAFE_INTEGER;
    const p2 = SUFFIX_PRIORITY[name2] ?? Number.MAX_SAFE_INTEGER;
    if (p1 !== p2) return p1 > p2 ? 1 : -1;
    if (num1 !== num2) return num1 > num2 ? 1 : -1;
    return 0;
}

/**
 * @param {string} v1
 * @param {string} v2
 * @return {number}
 */
export function compareVersionStrings(v1, v2) {
    const [ n1, s1 ] = toVersionParts(v1);
    const [ n2, s2 ] = toVersionParts(v2);
    const cmp = compareVersionParts(n1, n2);
    return cmp !== 0 ? cmp : compareVersionSuffix(s1, s2);
}

/**
 * @param {string} v1
 * @param {string} v2
 * @return {number}
 */
export function compareVersionStringsDescending(v1, v2) {
    const [ n1, s1 ] = toVersionParts(v1);
    const [ n2, s2 ] = toVersionParts(v2);
    const cmp = compareVersionParts(n2, n1);
    return cmp !== 0 ? cmp : compareVersionSuffix(s2, s1);
}

/**
 * @param {string} v
 * @param {Object} options
 * @param {string} [options.min]
 * @param {string} [options.max]
 * @return {boolean}
 */
export function isVersionInRange(v, { min, max } = {}) {
    return (min !== null && compareVersionStrings(v, min) >= 0)
        && (max !== null && compareVersionStrings(v, max) <= 0);

}