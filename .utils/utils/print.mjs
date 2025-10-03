/**
 * @param {string} str
 * @returns {string[]}
 */
const extractConcernedLines = (str) => str
    .split(/,?\s*\r?\n/)
    .map(s => s.trim())
    .filter(s => s && s.match(/\w/) && !s.includes('@Updated'));

/**
 * @param {Array} a1
 * @param {Array} a2
 * @returns {Set}
 */
const findCommonSet = (a1, a2) => {
    const s2 = new Set(a2);
    return new Set(a1.filter(l => s2.has(l)));
};

/**
 * @param {string} src
 * @param {string} other
 * @param {Object} [options={}]
 * @param {RegExp} [options.regexForKeyMatching]
 * @return {void}
 */
export function printLinesDiffs(src, other, options = {}) {
    const leftRaw = extractConcernedLines(src);
    const rightRaw = extractConcernedLines(other);
    const commonSet = findCommonSet(leftRaw, rightRaw);
    const left = leftRaw.filter(l => !commonSet.has(l));
    const right = rightRaw.filter(r => !commonSet.has(r));
    const minLineLength = Math.min(...[ ...left, ...right ].map(l => l.length));

    const keyOf = (/** @type {string} */ line) => {
        const reKey = options.regexForKeyMatching ?? null;
        if (reKey) {
            const m = line.match(reKey);
            return m ? m[0] : line;
        }
        const reClassicMap = /^"([^"]+)"\s+to\s+("[^"]+"|\d+)$/;
        const matchedMap = line.match(reClassicMap);
        if (matchedMap) {
            return matchedMap[1];
        }
        const reClassicVersion = /^"(\d+(\.\d+)?)(\.\d+)(\s*[-_]\w+\s*)*"$/;
        const matchedVersion = line.match(reClassicVersion);
        if (matchedVersion) {
            return matchedVersion[1];
        }
        return line.slice(0, Math.max(1, Math.ceil(minLineLength * 0.4))).trimEnd();
    };

    const lMap = new Map();
    const rMap = new Map();
    left.forEach(l => {
        const k = keyOf(l);
        if (!lMap.has(k)) lMap.set(k, [ l ]);
        else lMap.get(k).push(l);
    });
    right.forEach(r => {
        const k = keyOf(r);
        if (!rMap.has(k)) rMap.set(k, [ r ]);
        else rMap.get(k).push(r);
    });

    const deletions = [];
    const modifications = [];
    const additions = [];

    for (const l of left) {
        const k = keyOf(l);
        if (!rMap.has(k)) {
            deletions.push(`-- ${l}`);
        } else {
            const lLineList = lMap.get(k);
            const rLineList = rMap.get(k);
            const rLine = rLineList?.[0];
            const shouldRecordModification = lLineList.length === 1 && rLineList.length === 1 && l !== rLine;
            if (shouldRecordModification) {
                modifications.push({ from: `-- ${l}`, to: `-> ${rLine}` });
            } else {
                deletions.push(`-- ${l}`);
            }
        }
    }
    for (const r of right) {
        const k = keyOf(r);
        if (!lMap.has(k)) {
            additions.push(`++ ${r}`);
        } else {
            const lLineList = lMap.get(k);
            const rLineList = rMap.get(k);
            const lLine = lLineList?.[0];
            const shouldRecordModification = lLineList.length === 1 && rLineList.length === 1 && r !== lLine;
            if (!shouldRecordModification) {
                additions.push(`++ ${r}`);
            }
        }
    }

    const allLines = [
        ...deletions,
        ...modifications.flatMap(m => [ m.from, m.to ]),
        ...additions,
    ];
    const maxLen = allLines.reduce((mx, s) => Math.max(mx, s.length), 0);
    const SEP_EQ = '='.repeat(Math.max(6, maxLen));
    const SEP_DASH = '-'.repeat(Math.max(6, maxLen));

    const sections = [];

    if (deletions.length > 0) {
        sections.push(deletions.slice());
    }
    if (modifications.length > 0) {
        const modLines = [];
        modifications.forEach((m, idx) => {
            if (idx > 0) modLines.push(SEP_DASH);
            modLines.push(m.from);
            modLines.push(m.to);
        });
        sections.push(modLines);
    }
    if (additions.length > 0) {
        sections.push(additions.slice());
    }

    sections.forEach((lines, idx) => {
        console.log(SEP_EQ);
        lines.forEach(l => console.log(l));
        if (idx === sections.length - 1) {
            console.log(SEP_EQ);
        }
    });
}

/**
 * @param {Map<string,string>} src
 * @param {Map<string,string>} other
 */
export function printMapDiffs(src, other) {
    printLinesDiffs(mapToLines(src), mapToLines(other));
}

/**
 * @param {string[]} src
 * @param {string[]} other
 */
export function printListDiffs(src, other) {
    printLinesDiffs(src.join('\n'), other.join('\n'));
}

/**
 * @param {Map<string,string>} map
 * @returns {string}
 */
function mapToLines(map) {
    return Array.from(map.entries()).map(([ k, v ]) => `"${k}" to "${v}"`).join('\n');
}
