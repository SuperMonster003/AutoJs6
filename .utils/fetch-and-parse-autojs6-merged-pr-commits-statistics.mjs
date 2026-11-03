// fetch-and-parse-autojs6-merged-pr-commits-statistics.mjs

import * as dotenv from 'dotenv';
import { fileURLToPath } from 'node:url';
import { httpFetch } from './utils/fetch.mjs';
import { toYYYYMMDD } from './utils/date.mjs';

dotenv.config({ path: '.env', quiet: true });

const REPO = 'AutoJs6';
const OWNER = 'SuperMonster003';
const BASE = `https://api.github.com/repos/${OWNER}/${REPO}`;
const GITHUB_TOKEN = process.env.GITHUB_TOKEN || '';

// Debug: Set to an author's login name to print matching details for that author.
// zh-CN: 调试: 设置为某个作者的登录名 (login) 以打印该作者的匹配细节.
const DEBUG_LOGIN = process.env.DEBUG_LOGIN || '';
const DEBUG_VERBOSE = process.env.DEBUG_VERBOSE === '1';

// List of logins to exclude (defaults to repository maintainer);
// can be overridden with EXCLUDED_LOGINS env var, comma-separated.
// zh-CN: 可排除的登录名列表 (默认排除仓库维护者); 可用 EXCLUDED_LOGINS 环境变量覆盖, 逗号分隔.
const EXCLUDED_LOGINS = (process.env.EXCLUDED_LOGINS || OWNER)
    .split(',')
    .map(s => s.trim())
    .filter(Boolean);

/**
 * Pull requests that GitHub does not show as merged, but whose changes were ported into the codebase
 * or were already in place, so they count as contributions in the same way as merged pull requests.
 * After the history rewrite on 2026-11-03 their commit lists on GitHub span the whole old history,
 * so the author's commit count and latest commit time are recorded here as they were in the pull request.<br>
 * zh-CN:<br>
 * GitHub 未显示为已合并, 但改动已移植到代码中 (或已有相同修复) 的 Pull Request, 与已合并的 Pull Request 同样计入贡献.
 * 2026-11-03 历史改写后, 它们在 GitHub 上的提交列表包含整段旧历史,
 * 因此这里按原 Pull Request 记录作者的提交数与最近提交时间.
 *
 * @type {CreditedPullRequest[]}
 */
const CREDITED_PULL_REQUESTS = [
    { number: 556, login: 'djbclark', commitCount: 2, latestCommitAt: '2026-07-14T04:15:52Z' },
    { number: 557, login: 'djbclark', commitCount: 1, latestCommitAt: '2026-07-14T04:15:14Z' },
    { number: 558, login: 'djbclark', commitCount: 2, latestCommitAt: '2026-07-14T04:42:23Z' },
    { number: 571, login: 'DeanTMaxim', commitCount: 1, latestCommitAt: '2026-08-21T06:07:45Z' },
    { number: 573, login: 'DeanTMaxim', commitCount: 1, latestCommitAt: '2026-08-21T15:29:20Z' },
];

/** @type {import('http').OutgoingHttpHeaders} */
const headers = {
    'accept': 'application/vnd.github+json',
    'user-agent': 'pr-commit-contributions',
    ...(GITHUB_TOKEN ? { 'authorization': `Bearer ${GITHUB_TOKEN}` } : {}),
};

/** @type {Map<string, UserProfile>} */
const userProfileCache = new Map();

/**
 * @param {string} login
 * @returns {Promise<UserProfile>}
 */
async function getUserProfile(login) {
    if (userProfileCache.has(login)) {
        return userProfileCache.get(login);
    }
    const url = `https://api.github.com/users/${login}`;
    /** @type {UserProfile} */
    const data = await httpFetch(url, { headers });
    const profile = {
        login: data?.login || login,
        name: data?.name || null,
    };
    userProfileCache.set(login, profile);
    return profile;
}

/**
 * @returns {Promise<PullsData>}
 */
async function fetchAllMergedPRs() {
    const perPage = 100;
    let page = 1;
    const merged = [];

    while (true) {
        /** @type {PullsData} */
        const prs = await httpFetch(`${BASE}/pulls`, {
            headers,
            query: {
                state: 'closed',
                per_page: perPage,
                page: page,
                sort: 'created',
                direction: 'asc',
            },
        });
        if (!prs) {
            throw new Error('Failed to fetch PR list');
        }
        if (!Array.isArray(prs) || prs.length === 0) {
            break;
        }
        for (const pr of prs) {
            if (pr.merged_at) merged.push(pr);
        }
        if (prs.length < perPage) {
            break;
        }
        page += 1;
    }

    return merged;
}

/**
 * @param {string} a
 * @param {string} b
 * @returns {boolean}
 */
function equalsIgnoreCase(a, b) {
    return typeof a === 'string'
        && typeof b === 'string'
        && a.toLowerCase() === b.toLowerCase();
}

/**
 * @param {PullCommitsData[number]} c
 * @returns {string | null}
 */
function commitTimeFrom(c) {
    return c.commit?.committer?.date
        || c.commit?.author?.date
        || null;
}

/**
 * Rule: exclude `EXCLUDED_LOGINS` (when `author.login` or `committer.login` matches); include all others.<br>
 * zh-CN: 规则: 排除 `EXCLUDED_LOGINS` (`author.login` 或 `committer.login` 命中即排除); 其余一律计入.
 *
 * @param {PullCommitsData[number]} c
 * @returns {boolean}
 */
function isCommitBelongsToLogin(c) {
    const authorLogin = c.author?.login || null;
    const committerLogin = c.committer?.login || null;

    return !(authorLogin && EXCLUDED_LOGINS.some(x => equalsIgnoreCase(x, authorLogin)))
        && !(committerLogin && EXCLUDED_LOGINS.some(x => equalsIgnoreCase(x, committerLogin)));
}

/**
 * @param {PullsData[number]} pr
 * @returns {Promise<{commitCount: number, latestCommitAt: string | null}>}
 * The count of commits belonging to the PR author and the latest commit time in this PR.<br>
 * zh-CN: 该 PR 中属于 PR 作者本人的提交计数与最新提交时间.
 */
async function getPRCommitStatsByAuthor(pr) {
    const perPage = 100;
    let page = 1;
    let total = 0;
    let latestCommitAt = null;

    const login = pr.user?.login;
    if (!login) return { commitCount: 0, latestCommitAt: null };

    /**
     * Only collect detailed information when debugging this author.<br>
     * zh-CN: 仅在调试该作者时收集详细信息.
     * @type {DebugRecord[]}
     */
    const debugRecords = [];

    while (true) {
        const url = `${BASE}/pulls/${pr.number}/commits?per_page=${perPage}&page=${page}`;
        /** @type {PullCommitsData} */
        const commits = await httpFetch(url, { headers });
        if (!commits) {
            throw new Error('获取 PR #${pr.number} 的 commits 失败');
        }
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

    // Print debug info: only for target author;
    // print when PR statistics result is 0 or DEBUG_VERBOSE is enabled.
    // zh-CN: 打印调试: 仅针对目标作者; 在该 PR 统计结果为 0 或开启 DEBUG_VERBOSE 时打印.
    if (DEBUG_LOGIN && equalsIgnoreCase(login, DEBUG_LOGIN) && (DEBUG_VERBOSE || total === 0)) {
        const matched = debugRecords.filter(r => r.belongs).length;
        const unmatched = debugRecords.length - matched;
        console.log(`\n[DEBUG] PR #${pr.number} by ${login}: commits=${debugRecords.length}, included=${matched}, excluded=${unmatched}`);
        for (const r of debugRecords) {
            if (DEBUG_VERBOSE || !r.belongs) {
                console.log('[DEBUG] ' + [
                    r.sha,
                    `included=${r.belongs}`,
                    `author_login=${r.author_login}`,
                    `committer_login=${r.committer_login}`,
                    `author_name=${r.author_name}`,
                    `author_email=${r.author_email}`,
                    `time=${r.time}`,
                ].join(' | '));
            }
        }
    }

    return { commitCount: total, latestCommitAt };
}

/**
 * A concurrency-limited async map: runs `mapper`
 * with at most `limit` concurrent tasks and preserves input order.
 * - Use when you need to control request/task concurrency
 * (e.g. network requests/IO operations)
 * to avoid overwhelming services or triggering rate limits.
 * - Guarantees output array order matches input items order,
 * even if task completion times differ.<br>
 * zh-CN:<br>
 * 并发受限的异步映射: 以不超过 `limit` 的并发度执行 `mapper`, 并按输入顺序返回结果.
 * - 在需要控制请求/任务并发量 (如网络请求/IO 操作) 时使用, 避免压垮服务或触发限流.
 * - 保证输出数组与输入 items 的顺序一致, 即使各任务完成时间不同.
 *
 * @example Promise<MapperResult[]>
 * // Throttled fetching. (zh-CN: 限流抓取.)
 * const urls = [ 'https://a.com', 'https://b.com', 'https://c.com' ];
 * const res = await mapWithLimit(urls, 2, async (url, i) => {
 *   const r = await fetch(url);
 *   return { i, url, status: r.status };
 * });
 * console.log(res);
 *
 * @example Promise<MapperResult[]>
 * // Throttled processing. (zh-CN: 限流处理.)
 * const tasks = [ 1, 2, 3, 4, 5 ];
 * const out = await mapWithLimit(tasks, 3, async (n) => {
 *   await new Promise(r => setTimeout(r, 100 * n));
 *   return n * 2;
 * });
 * console.log(out); // [ 2, 4, 6, 8, 10 ]
 *
 * @template Item
 * @template MapperResult
 * @param {Item[]} items
 * The input items to process.<br>
 * zh-CN: 要处理的输入项列表.
 * @param {number} limit
 * Maximum concurrency (>=1).<br>
 * zh-CN: 最大并发数 (>=1).
 * @param {(item: Item, idx: number) => Promise<MapperResult>} mapper
 * Async function to process one item.<br>
 * zh-CN: 处理单个项的异步函数.
 * @returns {Promise<MapperResult[]>}
 * Results in the same order as input.<br>
 * zh-CN: 按输入顺序排列的结果数组.
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
 * Bulk fetch author profiles (with concurrency limit).<br>
 * zh-CN: 批量获取作者资料 (并发受限).
 *
 * @param {string[]} logins
 * @param {number} [concurrency=6]
 * @returns {Promise<Map<UserProfile['login'], UserProfile>>}
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
 * @param {number} [concurrency=6]
 * @returns {Promise<Statistics[]>}
 */
export async function fetchStatistics(concurrency = 6) {
    const mergedPRs = await fetchAllMergedPRs();
    const prWithStats = await mapWithLimit(mergedPRs, concurrency, async (pr) => {
        const { commitCount, latestCommitAt } = await getPRCommitStatsByAuthor(pr);
        return { pr, commitCount, latestCommitAt };
    });

    // Collect all authors first, then fetch profiles in bulk (with concurrency limit + memory cache).
    // zh-CN: 先收集所有作者, 再批量获取资料 (并发受限 + 内存缓存).
    // A credited pull request that has since been merged on GitHub is counted once, from GitHub.
    // zh-CN: 已在 GitHub 上合并的记录项只按 GitHub 数据计数一次.
    const mergedNumbers = new Set(mergedPRs.map(pr => pr.number));
    const creditedPRs = CREDITED_PULL_REQUESTS.filter(c => !mergedNumbers.has(c.number));

    const allLogins = prWithStats
        .map(({ pr }) => pr.user?.login)
        .concat(creditedPRs.map(c => c.login))
        .filter(Boolean);
    const profileMap = await fetchProfilesForLogins(allLogins, concurrency);


    /**
     * Group by PR creators. (zh-CN: 按 PR 发起者聚合.)
     * @type {Map<string, StatisticsEntry>}
     */
    const byAuthor = new Map();
    const addContribution = (login, commitCount, latestCommitAt, credited) => {
        /** @type {StatisticsEntry} */
        const entry = byAuthor.get(login) || {
            login: login,
            name: profileMap.get(login)?.name || null,
            htmlUrl: `https://github.com/${login}`,
            totalCommitsInMergedPRs: 0,
            latestCommitAt: null,
            hasCreditedPRs: false,
        };

        entry.totalCommitsInMergedPRs += commitCount;
        entry.hasCreditedPRs ||= credited;

        if (latestCommitAt && (!entry.latestCommitAt || new Date(latestCommitAt) > new Date(entry.latestCommitAt))) {
            entry.latestCommitAt = latestCommitAt;
        }

        byAuthor.set(login, entry);
    };
    for (const { pr, commitCount, latestCommitAt } of prWithStats) {
        if (!pr.user?.login) continue;
        addContribution(pr.user.login, commitCount, latestCommitAt, false);
    }
    for (const c of creditedPRs) {
        addContribution(c.login, c.commitCount, c.latestCommitAt, true);
    }

    // Sort by latest commit in descending order.
    // zh-CN: 按最近提交倒序.
    const entries = Array.from(byAuthor.values()).sort((a, b) => {
        const da = a.latestCommitAt ? new Date(a.latestCommitAt).getTime() : 0;
        const db = b.latestCommitAt ? new Date(b.latestCommitAt).getTime() : 0;
        return db - da;
    });

    return entries.map(e => new Statistics(e));
}

class Statistics {
    /**
     * @param {StatisticsEntry} entry
     */
    constructor(entry) {
        this.login = entry.login;
        this.name = entry.name;
        this.htmlUrl = entry.htmlUrl;
        this.totalCommitsInMergedPRs = entry.totalCommitsInMergedPRs;
        this.latestCommitAt = entry.latestCommitAt;
        // Credited pull requests are closed rather than merged, so the list for such an author is not limited to merged ones.
        // zh-CN: 计入贡献的 Pull Request 处于关闭而非合并状态, 因此这类作者的列表不限定为已合并.
        this.prListLink = `https://github.com/${OWNER}/${REPO}/pulls?q=`
            + 'is' + '%3A' + 'pr' + '+'
            + (entry.hasCreditedPRs ? '' : 'is' + '%3A' + 'merged' + '+')
            + 'author' + '%3A' + encodeURIComponent(entry.login);
    }

    /**
     * @param {string} text
     * @param {string | null} [style='word-break:keep-all;white-space:nowrap']
     * @returns {string}
     */
    #wrapInSpan(text, style = 'word-break:keep-all;white-space:nowrap') {
        return style ? `<span style="${style}">${text}</span>` : `<span>${text}</span>`;
    }

    get contributorMarkdown() {
        let markdownName = `[${this.login.replace(/-/g, '&#x2011;')}](${this.htmlUrl})`;
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

async function main() {
    const dataList = await fetchStatistics();
    console.table(dataList.map(e => ({
        contributor: e.name && e.name !== e.login ? `${e.login} (${e.name})` : e.login,
        commits: e.totalCommitsInMergedPRs,
        recent: e.latestCommitAt ? toYYYYMMDD(e.latestCommitAt) : 'N/A',
    })));
}

// Determine if this file is being run directly.
// zh-CN: 判断是否为直接执行该文件.
if (fileURLToPath(import.meta.url) === process.argv[1]) {
    main().catch(err => {
        console.error(err);
        process.exit(1);
    });
}
