// utils/versioning.mjs

const SUFFIX_PRIORITY = {
    // 预发布 (越小越靠前)
    'canary': 1, 'nightly': 1, 'snapshot': 1, 'dev': 1,
    'pre-alpha': 2, 'prealpha': 2, 'preview': 2, 'eap': 2, 'milestone': 2,
    'alpha': 3,
    'beta': 4,
    'rc': 5,
    // 正式/稳定
    '': 10, 'stable': 10, 'ga': 10, 'final': 10, 'release': 10, 'lts': 10,
};

/**
 * 统一后缀别名到规范键.
 *
 * @param {string} nameRaw
 * @return {string}
 */
function normalizeSuffixName(nameRaw) {
    const n = String(nameRaw || '').toLowerCase();
    switch (n) {
        case 'a':
            return 'alpha';
        case 'b':
            return 'beta';
        case 'cr':
            return 'rc';
        case 'm':
            return 'milestone';
        case 'pre':
            return 'preview';
        case 'canary':
        case 'nightly':
        case 'snapshot':
        case 'dev':
        case 'pre-alpha':
        case 'prealpha':
        case 'preview':
        case 'eap':
        case 'milestone':
        case 'alpha':
        case 'beta':
        case 'rc':
        case 'stable':
        case 'ga':
        case 'final':
        case 'release':
        case 'lts':
            return n;
        default:
            return n; // 未知后缀维持原样, 优先级将落在默认分支
    }
}

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

    // 解析后缀, 支持 rc1 / rc 1 / rc.1 / RC1 等; 默认数字为 1
    const suffixPattern = /([A-Za-z]+)[\s._-]*(\d*)|([A-Za-z]*)[\s._-]*(\d+)/;
    const suffixStr = parts[1] || '';
    const m = suffixStr.match(suffixPattern);
    if (!m) return [ numberParts, [ '', 0 ] ];

    const rawName = (m[1] ?? m[3] ?? '');
    const rawNum = (m[2] ?? m[4] ?? '');
    const suffixName = normalizeSuffixName(rawName);
    const suffixNumberParsed = parseInt(rawNum || '1', 10);
    const suffixNumber = Number.isNaN(suffixNumberParsed) ? 1 : suffixNumberParsed;
    return [ numberParts, [ suffixName, suffixNumber ] ];
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
    const name1 = normalizeSuffixName(name1Raw);
    const name2 = normalizeSuffixName(name2Raw);
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
 * @return {boolean}
 */
export function isVersionStable(v) {
    const [ _, [ suffixName ] ] = toVersionParts(v);
    const normalizedName = normalizeSuffixName(suffixName);
    return (SUFFIX_PRIORITY[normalizedName] ?? Number.MAX_SAFE_INTEGER) === 10;
}

/**
 * @param {string} v
 * @param {Object} options
 * @param {string} [options.min]
 * @param {string} [options.max]
 * @return {boolean}
 */
export function isVersionInRange(v, { min, max } = {}) {
    return (min == null || compareVersionStrings(v, min) >= 0)
        && (max == null || compareVersionStrings(v, max) <= 0);
}
