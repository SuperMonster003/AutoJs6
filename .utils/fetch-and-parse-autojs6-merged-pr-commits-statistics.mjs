// fetch-and-parse-autojs6-merged-pr-commits-statistics.mjs

/** @typedef {import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/pulls']['response']['data']} PullsData */
/** @typedef {import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/pulls/{pull_number}/commits']['response']['data']} PullCommitsData */

import fetch from 'node-fetch';
import * as dotenv from 'dotenv';
import { fileURLToPath } from 'node:url';
import { toYYYYMMDD } from './utils/date.mjs';

dotenv.config({ path: '.env', quiet: true });

const REPO = 'AutoJs6';
const OWNER = 'SuperMonster003';
const BASE = `https://api.github.com/repos/${OWNER}/${REPO}`;
const GITHUB_TOKEN = process.env.GITHUB_TOKEN || '';

// 调试: 设置为某个作者的登录名 (login) 以打印该作者的匹配细节
const DEBUG_LOGIN = process.env.DEBUG_LOGIN || '';
const DEBUG_VERBOSE = process.env.DEBUG_VERBOSE === '1';

// 可排除的登录名列表 (默认排除仓库维护者); 可用 EXCLUDED_LOGINS 环境变量覆盖, 逗号分隔
const EXCLUDED_LOGINS = (process.env.EXCLUDED_LOGINS || OWNER)
    .split(',')
    .map(s => s.trim())
    .filter(Boolean);

/**
 * @return {import('node-fetch').HeadersInit}
 */
function headers() {
    return {
        accept: 'application/vnd.github+json',
        ...(GITHUB_TOKEN ? { authorization: `Bearer ${GITHUB_TOKEN}` } : {}),
        'user-agent': 'pr-commit-contributions',
    };
}

/**
 * @typedef {Object} UserProfile
 * @property {string} login
 * @property {string | null} name
 */
/**
 * 简单内存缓存, 避免重复请求.
 *
 * @type {Map<string, UserProfile>}
 */
const userProfileCache = new Map();

/**
 * @param {string} login
 * @return {Promise<UserProfile>}
 */
async function getUserProfile(login) {
    if (userProfileCache.has(login)) return userProfileCache.get(login);
    const url = `https://api.github.com/users/${login}`;
    const res = await fetch(url, { headers: headers() });
    if (!res.ok) {
        userProfileCache.set(login, { login, name: null });
        return { login, name: null };
    }
    const data = await res.json();
    const profile = {
        login: data?.['login'] || login,
        name: data?.['name'] || null,
    };
    userProfileCache.set(login, profile);
    return profile;
}

/**
 * @return {Promise<PullsData>}
 */
async function fetchAllMergedPRs() {
    const perPage = 100;
    let page = 1;
    const merged = [];

    while (true) {
        const url = `${BASE}/pulls?state=closed&per_page=${perPage}&page=${page}&sort=created&direction=asc`;
        const res = await fetch(url, { headers: headers() });
        if (!res.ok) {
            const text = await res.text();
            throw new Error(`获取 PR 列表失败: ${res.status} ${res.statusText} - ${text}`);
        }
        const prs = /** @type {PullsData} */ await res.json();
        if (!Array.isArray(prs) || prs.length === 0) break;

        for (const pr of prs) {
            if (pr.merged_at) merged.push(pr);
        }

        if (prs.length < perPage) break;
        page += 1;
    }

    return merged;
}

/**
 * @param {string} a
 * @param {string} b
 * @return {boolean}
 */
function equalsIgnoreCase(a, b) {
    return typeof a === 'string'
        && typeof b === 'string'
        && a.toLowerCase() === b.toLowerCase();
}

/**
 * @param {PullCommitsData[number]} c
 * @return {string | null}
 */
function commitTimeFrom(c) {
    return c.commit?.committer?.date
        || c.commit?.author?.date
        || null;
}

/**
 * 规则: 排除 EXCLUDED_LOGINS (author.login 或 committer.login 命中即排除); 其余一律计入.
 *
 * @param {PullCommitsData[number]} c
 * @return {boolean}
 */
function isCommitBelongsToLogin(c) {
    const authorLogin = c.author?.login || null;
    const committerLogin = c.committer?.login || null;

    return !(authorLogin && EXCLUDED_LOGINS.some(x => equalsIgnoreCase(x, authorLogin)))
        && !(committerLogin && EXCLUDED_LOGINS.some(x => equalsIgnoreCase(x, committerLogin)));
}

/**
 * @typedef {Object} DebugRecord
 * @property {string} sha
 * @property {boolean} belongs
 * @property {string | null} author_login
 * @property {string | null} committer_login
 * @property {string | null} author_name
 * @property {string | null} author_email
 * @property {string | null} time
 */
/**
 * @param {PullsData[number]} pr
 * @return {Promise<{count: number, latestCommitAt: string | null}>} - 该 PR 中属于 PR 作者本人的提交计数与最新提交时间.
 */
async function getPRCommitStatsByAuthor(pr) {
    const perPage = 100;
    let page = 1;
    let total = 0;
    let latestCommitAt = null;

    const login = pr.user?.login;
    if (!login) return { count: 0, latestCommitAt: null };

    // 仅在调试该作者时收集详细信息
    /** @type {DebugRecord[]} */
    const debugRecords = [];

    while (true) {
        const url = `${BASE}/pulls/${pr.number}/commits?per_page=${perPage}&page=${page}`;
        const res = await fetch(url, { headers: headers() });
        if (!res.ok) {
            const text = await res.text();
            throw new Error(`获取 PR #${pr.number} 的 commits 失败: ${res.status} ${res.statusText} - ${text}`);
        }
        const commits = /** @type {PullCommitsData} */ await res.json();
        if (!Array.isArray(commits) || commits.length === 0) break;

        for (const c of commits) {
            const included = isCommitBelongsToLogin(c);
            if (included) {
                total += 1;
                const t = commitTimeFrom(c);
                if (t && (!latestCommitAt || new Date(t) > new Date(latestCommitAt))) {
                    latestCommitAt = t;
                }
            }
            if (DEBUG_LOGIN && equalsIgnoreCase(login, DEBUG_LOGIN)) {
                debugRecords.push({
                    sha: c.sha,
                    belongs: included,
                    author_login: c.author?.login || null,
                    committer_login: c.committer?.login || null,
                    author_name: c.commit?.author?.name || null,
                    author_email: c.commit?.author?.email || null,
                    time: c.commit?.committer?.date || c.commit?.author?.date || null,
                });
            }
        }

        if (commits.length < perPage) break;
        page += 1;
    }

    // 打印调试: 仅针对目标作者; 默认仅在该 PR 统计结果为 0 时打印, 或开启 DEBUG_VERBOSE 时总是打印
    if (DEBUG_LOGIN && equalsIgnoreCase(login, DEBUG_LOGIN) && (DEBUG_VERBOSE || total === 0)) {
        const matched = debugRecords.filter(r => r.belongs).length;
        const unmatched = debugRecords.length - matched;
        console.log(`\n[DEBUG] PR #${pr.number} by ${login}: commits=${debugRecords.length}, included=${matched}, excluded=${unmatched}`);
        for (const r of debugRecords) {
            if (DEBUG_VERBOSE || !r.belongs) {
                console.log(`[DEBUG] ${r.sha} | included=${r.belongs} | author_login=${r.author_login} | committer_login=${r.committer_login} | author_name=${r.author_name} | author_email=${r.author_email} | time=${r.time}`);
            }
        }
    }

    return { count: total, latestCommitAt };
}

/**
 * @template Item
 * @template MapperResult
 * @param {Item[]} items
 * @param {number} limit
 * @param {(item: Item, idx: number) => Promise<MapperResult>} mapper
 * @return {Promise<MapperResult[]>}
 */
async function mapWithLimit(items, limit, mapper) {
    const results = new Array(items.length);
    let i = 0;
    const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
        while (true) {
            const idx = i++;
            if (idx >= items.length) break;
            results[idx] = await mapper(items[idx], idx);
        }
    });
    await Promise.all(workers);
    return results;
}

/**
 * 批量获取作者资料 (并发受限), 返回 Map<login, profile>.
 *
 * @param {string[]} logins
 * @param {number} [concurrency=6]
 * @return {Promise<Map<UserProfile['login'], UserProfile>>}
 */
async function fetchProfilesForLogins(logins, concurrency = 6) {
    const unique = Array.from(new Set(logins)).filter(Boolean);
    const profiles = await mapWithLimit(unique, concurrency, async (login) => {
        return await getUserProfile(login);
    });
    const map = new Map();
    for (const p of profiles) {
        map.set(p.login, p);
    }
    return map;
}

/**
 * @return {Promise<Statistics[]>}
 */
export async function fetchStatistics() {
    const mergedPRs = await fetchAllMergedPRs();
    const CONCURRENCY = 6;
    const prWithStats = await mapWithLimit(mergedPRs, CONCURRENCY, async (pr) => {
        const { count, latestCommitAt } = await getPRCommitStatsByAuthor(pr);
        return { pr, count, latestCommitAt };
    });

    // 先收集所有作者, 再批量获取资料 (并发受限 + 内存缓存)
    const allLogins = prWithStats
        .map(({ pr }) => pr.user?.login)
        .filter(Boolean);
    const profileMap = await fetchProfilesForLogins(allLogins, 6);

    // 按 PR 发起者聚合
    const byAuthor = new Map();
    for (const { pr, count, latestCommitAt } of prWithStats) {
        const user = pr.user;
        if (!user?.login) continue;

        const entry = byAuthor.get(user.login) || {
            login: user.login,
            name: profileMap.get(user.login)?.name || null,
            html_url: `https://github.com/${user.login}`,
            totalCommitsInMergedPRs: 0,
            latestCommitAt: null,
        };

        entry.totalCommitsInMergedPRs += count;

        if (latestCommitAt && (!entry.latestCommitAt || new Date(latestCommitAt) > new Date(entry.latestCommitAt))) {
            entry.latestCommitAt = latestCommitAt;
        }

        byAuthor.set(user.login, entry);
    }

    // 按最近提交倒序
    const rows = Array.from(byAuthor.values()).sort((a, b) => {
        const da = a.latestCommitAt ? new Date(a.latestCommitAt).getTime() : 0;
        const db = b.latestCommitAt ? new Date(b.latestCommitAt).getTime() : 0;
        return db - da;
    });

    return rows.map(r => new Statistics(r));
}

async function main() {
    return await fetchStatistics();
}

class Statistics {
    /**
     * @param row {{
     *     login: string,
     *     name: string | null,
     *     html_url: string,
     *     totalCommitsInMergedPRs: number,
     *     latestCommitAt: string | null,
     *     prListLink: string,
     * }}
     */
    constructor(row) {
        this.login = row.login;
        this.name = row.name;
        this.html_url = row.html_url;
        this.totalCommitsInMergedPRs = row.totalCommitsInMergedPRs;
        this.latestCommitAt = row.latestCommitAt;
        this.prListLink = `https://github.com/${OWNER}/${REPO}/pulls?q=`
            + 'is' + '%3A' + 'pr' + '+'
            + 'is' + '%3A' + 'merged' + '+'
            + 'author' + '%3A' + encodeURIComponent(row.login);
    }

    /**
     * @param {string} text
     * @param {string} [style='word-break:keep-all;white-space:nowrap']
     * @returns {string}
     */
    #wrapInSpan(text, style = 'word-break:keep-all;white-space:nowrap') {
        return `<span style="${style}">${text}</span>`;
    }

    get contributorMarkdown() {
        let markdownName = `[${this.login.replace(/-/g, '&#x2011;')}](${this.html_url})`;
        if (this.name && this.name !== this.login) {
            markdownName += ` \`(${this.name})\``;
        }
        return this.#wrapInSpan(markdownName);
    }

    get commitsCountMarkdown() {
        return this.#wrapInSpan(`[${this.totalCommitsInMergedPRs}](${this.prListLink})`);
    }

    get latestCommitMarkdown() {
        if (!this.latestCommitAt) {
            return this.#wrapInSpan('`N/A`');
        }
        return this.#wrapInSpan(`\`${toYYYYMMDD(this.latestCommitAt)}\``);
    }
}

// 判断是否为直接执行该文件
if (fileURLToPath(import.meta.url) === process.argv[1]) {
    main().then((dataList) => {
        console.table(dataList.map(r => ({
            contributor: r.name && r.name !== r.login ? `${r.login} (${r.name})` : r.login,
            commits: r.totalCommitsInMergedPRs,
            recent: r.latestCommitAt ? toYYYYMMDD(r.latestCommitAt) : 'N/A',
        })));
    }).catch(err => {
        console.error(err);
        process.exit(1);
    });
}