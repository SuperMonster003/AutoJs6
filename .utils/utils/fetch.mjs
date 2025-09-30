// utils/fetch.mjs

import * as dotenv from 'dotenv';
import * as https from 'https';
import nodeFetch from 'node-fetch';
import { buildUrl } from './format.mjs';
import { toYYYYMMDD } from './date.mjs';

dotenv.config({ path: '../.env', quiet: true });

/**
 * Get the actual size of a remote file.<br>
 * zh-CN: 获取远程文件真实大小.
 *
 * @param {string} url
 * @param {Object} [options]
 * @param {number} [options.timeout=30000]
 * @returns {Promise<number | null>}
 */
export async function getRemoteFileSizeBytes(url, { timeout = 30000 } = {}) {
    const headers = {
        'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36',
        'accept': '*/*',
    };

    // Attempt HEAD.
    // zh-CN: 尝试 HEAD.

    try {
        const res = await nodeFetch(url, {
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

    // Attempt Range GET (bytes=0-0), parse total length from Content-Range.
    // zh-CN: 尝试 Range GET (bytes=0-0), 从 Content-Range 解析总长度.

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
            // Fallback, still try content-length.
            // zh-CN: 退化, 仍然尝试 content-length.
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
 * @returns {Promise<string>}
 */
export async function getLatestCommitDate(owner, repo) {
    const url = `https://api.github.com/repos/${owner}/${repo}/commits?per_page=1`;

    const token = process.env.GITHUB_TOKEN;
    /** @type {import('node-fetch').HeadersInit} */
    const headers = {
        accept: 'application/vnd.github+json',
        'user-agent': 'repo-last-commit-script',
        ...(token ? { authorization: `Bearer ${token}` } : {}),
    };
    const res = await fetch(url, { headers });

    if (!res.ok) {
        throw new Error(`GitHub API request failed: ${res.status} ${res.statusText}`);
    }

    const data = /** @type {CommitsData} */ await res.json();
    const latest = Array.isArray(data) ? data[0] : null;
    if (!latest?.commit) throw new Error('Failed to get latest commit');

    const iso = latest.commit.committer?.date ?? latest.commit.author?.date;
    if (!iso) throw new Error('Commit object missing date field');

    return toYYYYMMDD(iso);
}

/**
 * @param {string} url
 * @param {Object} [options={}]
 * @param {Object<string, any>} [options.query={}]
 * @param {import('http').OutgoingHttpHeaders} [options.headers={}]
 * @param {number} [options.timeout=15000]
 * @returns {Promise<*>}
 */
export function httpFetch(url, options = {}) {
    return new Promise((resolve, reject) => {
        const opts = {
            method: 'GET',
            headers: options.headers ?? {},
            timeout: options.timeout ?? 15000,
        };
        const niceUrl = options.query ? buildUrl(url, options.query) : url;
        const req = https.request(niceUrl, opts, (res) => {
            const { statusCode } = res;
            const chunks = [];
            res.on('data', (d) => chunks.push(d));
            res.on('end', () => {
                if (statusCode < 200 || statusCode >= 300) {
                    return reject(`HTTP ${statusCode}`);
                }
                let body = null;
                try {
                    body = Buffer.concat(chunks).toString('utf8');
                } catch (_) {
                    /* Ignored. */
                }
                if (body == null) {
                    throw new Error('Failed to read response body');
                }
                try {
                    resolve(JSON.parse(body));
                } catch {
                    resolve(body);
                }
            });
        });
        req.on('error', reject);
        req.on('timeout', () => {
            req.destroy(new Error('Request timed out'));
        });
        req.end();
    });
}
