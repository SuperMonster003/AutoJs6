// utils/fetch.mjs

/** @typedef {import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/commits']['response']['data']} CommitsData */

import fetch from 'node-fetch';
import * as dotenv from 'dotenv';
import { toYYYYMMDD } from './date.mjs';

dotenv.config({ path: '../.env', quiet: true });

/**
 * 获取远程文件真实大小.
 *
 * @param {string} url
 * @param {{timeout?: number}} [options]
 * @returns {Promise<number | null>}
 */
export async function getRemoteFileSizeBytes(url, { timeout = 30000 } = {}) {
    const headers = {
        'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36',
        'accept': '*/*',
    };

    // 1) 尝试 HEAD

    try {
        const res = await fetch(url, {
            method: 'HEAD',
            redirect: 'follow',
            headers,
        });
        if (res.ok) {
            const len = res.headers.get('content-length');
            if (len && /^\d+$/.test(len)) return Number(len);
        }
    } catch (_) {
        /* Ignored. */
    }

    // 2) 尝试 Range GET (bytes=0-0), 从 Content-Range 解析总长度

    try {
        const ac = new AbortController();
        const t = setTimeout(() => ac.abort(), timeout);
        const res = await fetch(url, {
            method: 'GET',
            redirect: 'follow',
            headers: { ...headers, range: 'bytes=0-0' },
            signal: ac.signal,
        }).finally(() => clearTimeout(t));

        if (res.ok || res.status === 206) {
            // Content-Range: bytes 0-0/123456789
            const cr = res.headers.get('content-range');
            if (cr) {
                const m = /bytes\s+\d+-\d+\/(\d+)/i.exec(cr);
                if (m) return Number(m[1]);
            }
            // 退化: 仍然尝试 content-length
            const len = res.headers.get('content-length');
            if (len && /^\d+$/.test(len)) return Number(len);
        }
    } catch (_) {
        /* Ignored. */
    }

    return null;
}

/**
 * @param {string} owner
 * @param {string} repo
 * @return {Promise<string>}
 */
export async function getLatestCommitDate(owner, repo) {
    const token = process.env.GITHUB_TOKEN; // 可选：避免频繁请求受限
    const url = `https://api.github.com/repos/${owner}/${repo}/commits?per_page=1`;

    /** @type {import('node-fetch').HeadersInit} */
    const headers = {
        accept: 'application/vnd.github+json',
        'user-agent': 'repo-last-commit-script',
        ...(token ? { authorization: `Bearer ${token}` } : {}),
    };
    const res = await fetch(url, { headers });

    if (!res.ok) {
        throw new Error(`GitHub API 请求失败: ${res.status} ${res.statusText}`);
    }

    const data = /** @type {CommitsData} */ await res.json();
    const latest = Array.isArray(data) ? data[0] : null;
    if (!latest?.commit) throw new Error('未获取到最新提交');

    // 优先使用 committer 的提交时间，fallback 到 author
    const iso = latest.commit.committer?.date ?? latest.commit.author?.date;
    if (!iso) throw new Error('提交对象缺少日期字段');

    return toYYYYMMDD(iso);
}
