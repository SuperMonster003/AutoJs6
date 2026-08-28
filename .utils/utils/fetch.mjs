// utils/fetch.mjs

import * as dotenv from 'dotenv';
import * as https from 'https';
import { buildUrl } from './format.mjs';
import { toYYYYMMDD } from './date.mjs';

dotenv.config({ path: '../.env', quiet: true });

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
 * @typedef {Object} HttpFetchOptions
 * @property {Object<string, any>} [query={}] - Query parameters to append to the URL
 * @property {import('http').OutgoingHttpHeaders} [headers={}] - HTTP headers
 * @property {number} [timeout=15000] - Request timeout in milliseconds
 * @property {'GET'|'POST'|'PUT'|'DELETE'|'HEAD'|'PATCH'} [method='GET'] - HTTP method
 */

/**
 * @param {string} url
 * @param {HttpFetchOptions} [options={}]
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
