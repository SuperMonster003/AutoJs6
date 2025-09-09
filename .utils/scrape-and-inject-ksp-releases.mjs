// scrape-and-inject-ksp-releases.mjs

/** @typedef {import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/releases']['response']['data']} ReleasesData */

import * as https from 'https';
import { updateAnchoredMapInFile } from './utils/anchors.mjs';
import { toUpdatedStamp } from './utils/date.mjs';

/**
 * @param {string} url
 * @param {Object} [options={}]
 * @param {import("http").OutgoingHttpHeaders} [options.headers={}]
 * @param {number} [options.timeout=15000]
 * @return {Promise<ReleasesData>}
 */
function httpsGetJson(url, options = {}) {
    return new Promise((resolve, reject) => {
        const req = https.request(
            url,
            {
                method: 'GET',
                headers: {
                    'User-Agent': 'node',
                    'Accept': 'application/vnd.github+json',
                    ...(options.headers || {}),
                },
                timeout: options.timeout || 15000,
            },
            (res) => {
                const { statusCode } = res;
                const chunks = [];

                res.on('data', (d) => chunks.push(d));
                res.on('end', () => {
                    const body = Buffer.concat(chunks).toString('utf8');

                    if (statusCode < 200 || statusCode >= 300) {
                        return reject(
                            new Error(`HTTP ${statusCode}: ${body.slice(0, 200)}`),
                        );
                    }

                    try {
                        const json = /** @type {ReleasesData} */ JSON.parse(body);
                        resolve(json);
                    } catch (e) {
                        reject(new Error(`JSON parse error: ${e.message}`));
                    }
                });
            },
        );

        req.on('error', reject);
        req.on('timeout', () => {
            req.destroy(new Error('Request timed out'));
        });
        req.end();
    });
}

/**
 * @typedef {Object} KspRelease
 * @property {string} version
 * @property {string} name
 * @property {string} publishedAt
 */
/**
 * @return {Promise<KspRelease[]>}
 */
async function fetchKspReleases() {
    const base = 'https://api.github.com/repos/google/ksp/releases';
    const perPage = 100;
    let page = 1;
    let reached = false;
    const out = [];

    /** @type {import("http").OutgoingHttpHeaders} */
    const headers = {};
    if (process.env.GITHUB_TOKEN) {
        headers.authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
    }

    while (!reached) {
        const url = `${base}?per_page=${perPage}&page=${page}`;
        const releases = await httpsGetJson(url, { headers });
        if (!Array.isArray(releases) || releases.length === 0) break;

        for (const release of releases) {
            const tag = String(release.tag_name || '').trim();
            // 记录
            out.push({
                version: tag,
                name: release.name,
                publishedAt: release.published_at,
            });

            // 判断是否已到达目标最旧版本（含）
            const parts = tag.split('-');
            if (parts.length >= 2) {
                const kspVer = parts.slice(0, -1).join('-');
                if (kspVer === '1.8.0-RC2') {
                    reached = true;
                    break;
                }
            }
        }

        if (reached) break;
        page += 1;
    }

    return out;
}

/**
 * @param {KspRelease[]} releases
 * @return {string[]}
 */
function parseReleases(releases) {
    if (!Array.isArray(releases)) return [];

    // 先根据 KSP 版本去重，保留发布时间最新的一条
    /** @type {Map<string, { kotlinVer: string, date: Date }>} */
    const latestByKsp = new Map(); // kspVer -> { kotlinVer, date }
    for (const r of releases) {
        const rawVer = String(r.version || '').trim();
        const parts = rawVer.split('-');
        if (parts.length < 2) continue; // 跳过无效项

        const kotlinVer = parts.pop();
        const kspVer = parts.join('-');

        const d = new Date(r.publishedAt);
        if (Number.isNaN(d.getTime())) continue;

        const prev = latestByKsp.get(kspVer);
        if (!prev || d > prev.date) {
            latestByKsp.set(kspVer, { kotlinVer, date: d });
        }
    }

    /**
     * 解析 KSP 版本用于排序.
     *
     * @param {string} ksp
     * @return {{ baseNums: number[], rank: number, qName: string, qNum: number }}
     */
    function parseKspVer(ksp) {
        const [ base, qualifierRaw = '' ] = ksp.split('-', 2);
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

        // 等级：稳定版 > RC > Beta > 其他
        const rankMap = { '': 3, RC: 2, BETA: 1 };
        const rank = Object.prototype.hasOwnProperty.call(rankMap, qName) ? rankMap[qName] : 0;

        return { baseNums, rank, qName, qNum };
    }

    /**
     * @param {number[]} aNums
     * @param {number[]} bNums
     * @return {number}
     */
    function cmpBaseDesc(aNums, bNums) {
        const len = Math.max(aNums.length, bNums.length);
        for (let i = 0; i < len; i++) {
            const av = aNums[i] ?? 0;
            const bv = bNums[i] ?? 0;
            if (av !== bv) return bv - av; // 降序
        }
        return 0;
    }

    // 转为数组并按 KSP 版本降序排列
    /**
     * @type {Array<{ kspVer: string, kotlinVer: string, date: Date, parsed: { baseNums: number[], rank: number, qName: string, qNum: number } }>}
     */
    const items = Array.from(latestByKsp.entries())
        .map(([ kspVer, v ]) => {
            const parsed = parseKspVer(kspVer);
            return { kspVer, kotlinVer: v.kotlinVer, date: v.date, parsed };
        })
        .sort((a, b) => {
            // 1) 基础版本号降序
            let c = cmpBaseDesc(a.parsed.baseNums, b.parsed.baseNums);
            if (c !== 0) return c;
            // 2) 级别降序（稳定版 > RC > Beta > 其他）
            if (a.parsed.rank !== b.parsed.rank) return b.parsed.rank - a.parsed.rank;
            // 3) 同级别数字降序（RC2 > RC1；Beta2 > Beta1；无数字视为 0）
            if (a.parsed.qNum !== b.parsed.qNum) return b.parsed.qNum - a.parsed.qNum;
            // 4) 兜底，限定词字典序降序（稳定排序用）
            if (a.parsed.qName !== b.parsed.qName) return a.parsed.qName < b.parsed.qName ? 1 : -1;
            // 5) 仍然相同则按日期降序（保险）
            return b.date.getTime() - a.date.getTime();
        });

    return items.map(({ kspVer, kotlinVer, date }) => {
        const dateStr = toUpdatedStamp(date);
        return `"${kspVer}" to "${kotlinVer}", /* ${dateStr}. */`;
    });
}

fetchKspReleases()
    .then(async (releases) => {
        await updateAnchoredMapInFile('../settings.gradle.kts', {
            anchorTag: 'KSP_VERSION_MAP',
            mapName: 'kspVersionMap',
            lines: parseReleases(releases).map(l => `${l}`),
            updatedLabel: 'KSP 发行版本映射',
        });
    })
    .catch((error) => console.error('Failed to fetch KSP releases:', error));