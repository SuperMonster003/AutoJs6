// scrape-and-inject-ksp-releases-map.mjs
// after: [ scrape-and-inject-gradle-kotlin-compatibility-map.mjs ]

import { httpFetch } from './utils/fetch.mjs';
import { readPropertiesSync } from './utils/properties.mjs';
import { toUpdatedStamp } from './utils/date.mjs';
import { updateGradleLinesData } from './utils/update-helper.mjs';

const URL = 'https://api.github.com/repos/google/ksp/releases';
const GITHUB_TOKEN = process.env.GITHUB_TOKEN;

/**
 * Legacy tag: "<kotlin>-<ksp>"
 * - kotlinKey = full kotlin part
 * - kspVer = last part
 *
 * KSP2 tag: "<ksp>"
 * - kotlinKey = "<major>.<minor>.Z" (and keep qualifier if exists)
 * - kspVer = full part
 *
 * @param {string} rawTag
 * @returns {{ kotlinKey: string, kspVer: string } | null}
 */
function splitKspTag(rawTag) {
    const tag = String(rawTag || '').trim();
    if (!tag) return null;

    /**
     * @param {string} s
     * @returns {boolean}
     */
    const looksLikePlainSemver = (s) => /^\d+\.\d+\.\d+(?:\.\d+)?$/.test(s);

    // Legacy: "<kotlin>-<kspSemver>"
    const parts = tag.split('-');
    const last = parts[parts.length - 1];
    if (parts.length >= 2 && looksLikePlainSemver(last)) {
        return {
            kotlinKey: parts.slice(0, -1).join('-'),
            kspVer: last,
        };
    }

    // KSP2: tag itself is kspVer (e.g. "2.3.4", or maybe "2.4.0-RC1") ----
    // Parse base numbers from the first segment before '-'
    const base = tag.split('-', 1)[0];
    const m = /^(\d+)\.(\d+)\.(\d+)(?:\.\d+)?$/.exec(base);
    if (!m) return null;

    const major = m[1];
    const minor = m[2];

    // Keep qualifier if you want separate buckets for RC/Beta (optional)
    const qualifier = tag.includes('-') ? tag.slice(tag.indexOf('-') + 1).trim() : '';
    const kotlinKeyPrefix = `${major}.${minor}.Z`;
    const kotlinKey = qualifier ? `${kotlinKeyPrefix}-${qualifier}` : kotlinKeyPrefix;

    return { kotlinKey, kspVer: tag };
}

/**
 * @returns {Promise<KspRelease[]>}
 */
async function fetchKspReleases() {
    /** @type {import('http').OutgoingHttpHeaders} */
    const headers = {
        'accept': 'application/vnd.github+json',
        'user-agent': 'node',
        ...(GITHUB_TOKEN ? { 'authorization': `Bearer ${GITHUB_TOKEN}` } : {}),
    };
    const minToCheck = await getMinKotlinVersionToCheck();
    const out = [];

    let page = 1;
    let reached = false;

    while (!reached) {
        const query = { per_page: 100, page };
        /** @type {ReleasesData} */
        const releases = await httpFetch(URL, { query, headers });

        if (!Array.isArray(releases) || releases.length === 0) break;

        for (const release of releases) {
            const tag = String(release.tag_name || '').trim();
            out.push({
                version: tag,
                name: release.name,
                publishedAt: release.published_at,
            });

            const parsed = splitKspTag(tag);
            if (!parsed) continue;

            if (parsed.kotlinKey === minToCheck) {
                reached = true;
                break;
            }
        }

        if (reached) break;
        page += 1;
    }

    return out;
}

async function getMinKotlinVersionToCheck() {
    const minGradle = readPropertiesSync('../version.properties').get('MIN_SUPPORTED_GRADLE_VERSION');
    const mapElement = readPropertiesSync('../gradle/data/gradle-kotlin-compat.properties').get(minGradle);
    if (mapElement) return mapElement;
    throw new Error(`Failed to find the minimum Kotlin version to check in settings.gradle.kts`);
}

/**
 * @param {KspRelease[]} releases
 * @returns {string[]}
 */
function parseReleases(releases) {
    if (!Array.isArray(releases)) {
        console.warn('Failed to parse KSP releases');
        return [];
    }

    // De-duplicate by Kotlin version, keep the latest one by release date.
    // zh-CN: 根据 Kotlin 版本去重, 保留发布时间最新的一条.

    /** @type {Map<string, { kspVer: string, date: Date }>} */
    const latestByKotlin = new Map();
    for (const r of releases) {
        const rawVer = String(r.version || '').trim();
        const parsed = splitKspTag(rawVer);
        if (!parsed) {
            console.warn(`Failed to parse KSP tag "${rawVer}"`);
            continue;
        }
        const { kotlinKey: KotlinVer, kspVer } = parsed;

        const d = new Date(r.publishedAt);
        if (Number.isNaN(d.getTime())) continue;

        const prev = latestByKotlin.get(KotlinVer);
        if (!prev || d > prev.date) {
            latestByKotlin.set(KotlinVer, { kspVer, date: d });
        }
    }

    /**
     * Parse Kotlin version for sorting.<br>
     * zh-CN: 解析 Kotlin 版本用于排序.
     *
     * @param {string} kotlinVer
     * @returns {{ baseNums: number[], rank: number, qName: string, qNum: number }}
     */
    function parseKotlinVer(kotlinVer) {
        const [ base, qualifierRaw = '' ] = kotlinVer.split('-', 2);
        const baseNums = base.split('.').map((n) => parseInt(String(n), 10) || 0);

        let qName = '';
        let qNum = 0;
        if (qualifierRaw) {
            const m = /^([A-Za-z]+)(\d+)?$/i.exec(qualifierRaw.trim());
            if (m) {
                qName = m[1].toUpperCase();
                qNum = m[2] ? parseInt(m[2], 10) : 0;
            } else {
                qName = qualifierRaw.toUpperCase();
            }
        }

        const rankMap = { '': 3, RC: 2, BETA: 1 };
        const rank = Object.prototype.hasOwnProperty.call(rankMap, qName) ? rankMap[qName] : 0;

        return { baseNums, rank, qName, qNum };
    }

    /**
     * @param {number[]} aNums
     * @param {number[]} bNums
     * @returns {number}
     */
    function cmpBaseDesc(aNums, bNums) {
        const len = Math.max(aNums.length, bNums.length);
        for (let i = 0; i < len; i++) {
            const av = aNums[i] ?? 0;
            const bv = bNums[i] ?? 0;
            if (av !== bv) return bv - av;
        }
        return 0;
    }

    return Array.from(latestByKotlin.entries())
        .map(([ kotlinVer, v ]) => {
            const parsed = parseKotlinVer(kotlinVer);
            return { kotlinVer, kspVer: v.kspVer, date: v.date, parsed };
        })
        .sort((a, b) => {
            const c = cmpBaseDesc(a.parsed.baseNums, b.parsed.baseNums);
            if (c !== 0) return c;
            if (a.parsed.rank !== b.parsed.rank) return b.parsed.rank - a.parsed.rank;
            if (a.parsed.qNum !== b.parsed.qNum) return b.parsed.qNum - a.parsed.qNum;
            if (a.parsed.qName !== b.parsed.qName) return a.parsed.qName < b.parsed.qName ? 1 : -1;
            return b.date.getTime() - a.date.getTime();
        })
        .map(({ kotlinVer, kspVer, date }) => {
            return [ `#${toUpdatedStamp(date)}`, `${kotlinVer}=${kspVer}` ];
        })
        .flat(1);
}

(async function main() {
    const releases = await fetchKspReleases();
    const lines = parseReleases(releases);
    await updateGradleLinesData('ksp-releases', lines, {
        label: 'KSP releases version map',
    });
})().catch((err) => {
    console.error('Failed to fetch KSP releases:', err);
    process.exitCode = 1;
});
