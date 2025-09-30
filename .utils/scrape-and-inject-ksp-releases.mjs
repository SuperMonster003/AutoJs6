// scrape-and-inject-ksp-releases.mjs

import * as fsp from 'node:fs/promises';
import { httpFetch } from './utils/fetch.mjs';
import { readProperties } from './utils/properties.mjs';
import { toUpdatedStamp } from './utils/date.mjs';
import { updateAnchoredMapInFile } from './utils/anchors.mjs';

const URL = 'https://api.github.com/repos/google/ksp/releases';
const GITHUB_TOKEN = process.env.GITHUB_TOKEN;

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

            const parts = tag.split('-');
            if (parts.length < 2) continue;
            /**
             * @example string
             * "2.2.20-2.0.3" -> "2.2.20"
             * "1.9.10-1.0.13" -> "1.9.10"
             * "2.2.20-RC2-2.0.2" -> "2.2.20-RC2"
             * "1.9.20-RC-1.0.13" -> "1.9.20-RC"
             * @type {string}
             */
            const kotlinVer = parts.slice(0, -1).join('-');
            if (kotlinVer === minToCheck) {
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
    const raw = await fsp.readFile('../settings.gradle.kts', 'utf8');
    const tag = 'GRADLE_KOTLIN_COMPATIBILITY_LIST';
    const beginIndex = raw.indexOf(`@AnchorBegin ${tag}`);
    const endIndex = raw.indexOf(`@AnchorEnd ${tag}`);

    if (beginIndex !== -1 && endIndex !== -1) {
        const props = await readProperties();
        const minGradle = props['MIN_SUPPORTED_GRADLE_VERSION'];

        const lines = raw.slice(beginIndex + tag.length + 1, endIndex).split('\n');
        for (const line of lines) {
            const m = /^"(\d+(?:\.\d+)+)" to "(\d+(?:\.\d+)+)",/.exec(line.trim());
            if (m) {
                const [ _, gradleVersion, kotlinVersion ] = m;
                if (gradleVersion === minGradle) {
                    return kotlinVersion;
                }
            }
        }
    }
    throw new Error(`Failed to find the minimum Kotlin version to check in settings.gradle.kts`);
}

/**
 * @param {KspRelease[]} releases
 * @returns {string[]}
 */
function parseReleases(releases) {
    if (!Array.isArray(releases)) return [];

    // De-duplicate by Kotlin version, keep the latest one by release date.
    // zh-CN: 根据 Kotlin 版本去重, 保留发布时间最新的一条.

    /** @type {Map<string, { kspVer: string, date: Date }>} */
    const latestByKotlin = new Map();
    for (const r of releases) {
        const rawVer = String(r.version || '').trim();
        const parts = rawVer.split('-');
        if (parts.length < 2) continue;

        const kspVer = parts.pop();
        const KotlinVer = parts.join('-');

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
            return `"${kotlinVer}" to "${kspVer}", /* ${(toUpdatedStamp(date))}. */`;
        });
}

(async function main() {
    const releases = await fetchKspReleases();
    await updateAnchoredMapInFile('../settings.gradle.kts', {
        anchorTag: 'KSP_VERSION_MAP',
        mapName: 'kspVersionMap',
        lines: parseReleases(releases),
        updatedLabel: 'KSP releases version map',
    });
})().catch((err) => {
    console.error('Failed to fetch KSP releases:', err);
    process.exitCode = 1;
});
