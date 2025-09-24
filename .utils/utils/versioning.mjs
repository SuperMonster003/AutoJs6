// utils/versioning.mjs

const PRIORITY_STABLE = 100;

const SUFFIX_PRIORITY = Object.fromEntries([

    [ [ 'canary', 'nightly', 'snapshot', 'dev', 'experimental', 'dev-experimental', 'wip', 'prototype' ], 5 ],
    [ [ 'pre-alpha', 'preview', 'tech-preview', 'eap', 'milestone' ], 10 ],
    [ [ 'alpha' ], 15 ],
    [ [ 'beta' ], 20 ],
    [ [ 'rc', 'ga-candidate' ], 25 ],
    [ [ '', 'stable', 'ga', 'final', 'release', 'lts', 'rtm', 'sp', 'patch', 'maintenance' ], PRIORITY_STABLE ],

].map((/** @type {[string[], number]} */ [ suffixes, priority ]) => {
    return suffixes.map(suffix => [ suffix, priority ]);
}).flat());

/**
 * @param {string} v1
 * @param {string} v2
 * @returns {number}
 */
export function compareVersionStrings(v1, v2) {
    const [ n1, s1 ] = toVersionParts(v1);
    const [ n2, s2 ] = toVersionParts(v2);
    return compareVersionParts(n1, n2) || compareVersionSuffix(s1, s2);
}

/**
 * @param {string} v1
 * @param {string} v2
 * @returns {number}
 */
export function compareVersionStringsDescending(v1, v2) {
    const [ n1, s1 ] = toVersionParts(v1);
    const [ n2, s2 ] = toVersionParts(v2);
    return compareVersionParts(n2, n1) || compareVersionSuffix(s2, s1);
}

/**
 * @param {string} version
 * @returns {boolean}
 */
export function isVersionStable(version) {
    const [ _, [ suffixName ] ] = toVersionParts(version);
    return getSuffixPriority(suffixName) === PRIORITY_STABLE;
}

/**
 * @param {string} version
 * @param {Object} options
 * @param {string} [options.min]
 * @param {string} [options.max]
 * @returns {boolean}
 */
export function isVersionInRange(version, { min, max } = {}) {
    return (min == null || compareVersionStrings(version, min) >= 0)
        && (max == null || compareVersionStrings(version, max) <= 0);
}

/**
 * @param {string} suffix
 * @returns {number}
 */
function getSuffixPriority(suffix) {
    return SUFFIX_PRIORITY[normalizeSuffixName(suffix)] ?? Number.MAX_SAFE_INTEGER;
}

/**
 * Normalize suffix aliases to standard keys.<br>
 * zh-CN: 统一后缀别名到规范键.
 *
 * @param {string} nameRaw
 * @returns {string}
 */
function normalizeSuffixName(nameRaw) {
    const n = String(nameRaw || '').toLowerCase().replaceAll('_', '-');
    switch (n) {
        case 'a':
            return 'alpha';
        case 'b':
            return 'beta';
        case 'exp':
            return 'experimental';
        case 'cr':
        case 'release-candidate':
            return 'rc';
        case 'general-availability':
            return 'ga';
        case 'm':
            return 'milestone';
        case 'pre':
        case 'prev':
            return 'preview';
        case 'prealpha':
            return 'pre-alpha';
        case 'proto':
            return 'prototype';
        default:
            return n;
    }
}

/**
 * @example [ numberPart[], [ suffixName, suffixNumber ] ]
 * // All results below will be: [ [ 4, 1, 1 ], [ 'alpha', 2 ] ].
 *
 * toVersionParts('4.1.1 Alpha2');
 * toVersionParts('4.1.1 alpha2');
 * toVersionParts('4.1.1alpha2');
 * toVersionParts('4.1.1alpha 2');
 * toVersionParts('4.1.1 alpha 2');
 * toVersionParts('4.1.1-alpha2');
 * toVersionParts('4.1.1-alpha-2');
 * toVersionParts('4.1.1 - alpha 2');
 * toVersionParts('4.1.1_alpha_2');
 * toVersionParts('4.1.1a2');
 *
 * // All results below will be: [ [ 2024, 3, 2 ], [ 'beta', 1 ] ].
 *
 * toVersionParts('2024.3.2 Beta');
 * toVersionParts('2024.3.2 Beta1');
 * toVersionParts('2024.3.2 Beta01');
 * toVersionParts('2024.3.2 Beta.1');
 * toVersionParts('2024.3.2beta1');
 * toVersionParts('2024.3.2b1');
 * toVersionParts('2024.3.2b');
 *
 * @param {string} version
 * @returns { [number[], [string, number]] }
 */
function toVersionParts(version) {
    const ver = version.trim();
    const parts = ver.split(/[\s_+-]+/);
    const numberParts = parts[0].split('.').map((partRaw, idx, arr) => {
        const part = String(partRaw);
        if (idx === arr.length - 1) {
            const matched = part.match(/^\d+([A-Za-z]+)(\d*)$/);
            if (matched) {
                const suffix = matched[1];
                const suffixNum = matched[2];
                if (suffixNum) {
                    parts.splice(1, 0, suffix, suffixNum);
                } else {
                    parts.splice(1, 0, suffix);
                }
            }
        }
        const num = parseInt(part, 10);
        if (Number.isNaN(num)) {
            throw new Error(`Invalid version part: '${part}' in version: '${ver}'`);
        }
        return num;
    });

    const suffixPattern = /([A-Za-z]+)[\s._-]*(\d*)|([A-Za-z]*)[\s._-]*(\d+)/;
    const suffixStr = parts.slice(1).join('') || '';
    const m = suffixStr.match(suffixPattern);
    if (!m) return [ numberParts, [ '', 0 ] ];

    const rawName = m[1] ?? m[3] ?? '';
    const rawNum = m[2] ?? m[4] ?? '';
    const suffixName = normalizeSuffixName(rawName);
    const suffixNumberParsed = parseInt(rawNum || '1', 10);
    const suffixNumber = Number.isNaN(suffixNumberParsed) ? 1 : suffixNumberParsed;
    return [ numberParts, [ suffixName, suffixNumber ] ];
}

/**
 * @param {number[]} a
 * @param {number[]} b
 * @returns {number}
 */
function compareVersionParts(a, b) {
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
 * @returns {number}
 */
function compareVersionSuffix(s1, s2) {
    const [ name1Raw, num1 ] = s1;
    const [ name2Raw, num2 ] = s2;
    const p1 = getSuffixPriority(name1Raw);
    const p2 = getSuffixPriority(name2Raw);
    if (p1 !== p2) return p1 > p2 ? 1 : -1;
    if (num1 !== num2) return num1 > num2 ? 1 : -1;
    return 0;
}
