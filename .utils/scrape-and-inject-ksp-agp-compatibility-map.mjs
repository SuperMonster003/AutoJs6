// scrape-and-inject-ksp-agp-compatibility-map.mjs
// after: [ scrape-and-inject-ksp-releases-map.mjs ]

import { httpFetch } from './utils/fetch.mjs';
import { toUpdatedStamp } from './utils/date.mjs';
import { updateGradleLinesData } from './utils/update-helper.mjs';
import { compareVersionStringsDescending } from './utils/versioning.mjs';

const RELEASES_URL = 'https://api.github.com/repos/google/ksp/releases';
const SOURCE_PATH = 'gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/utils/agpUtils.kt';
const GITHUB_TOKEN = process.env.GITHUB_TOKEN;
const MAX_FETCH_ATTEMPTS = 3;
const RETRY_BASE_DELAY_MS = 500;

const HEADERS = {
    'accept': 'application/vnd.github+json',
    'user-agent': 'autojs6-ksp-agp-compat-scraper',
    ...(GITHUB_TOKEN ? { 'authorization': `Bearer ${GITHUB_TOKEN}` } : {}),
};

/**
 * @param {number} milliseconds
 * @returns {Promise<void>}
 */
function delay(milliseconds) {
    return new Promise(resolve => setTimeout(resolve, milliseconds));
}

/**
 * Retry transient network and server failures without hiding permanent HTTP
 * failures such as authentication, rate-limit, and missing-source responses.
 *
 * @param {string} url
 * @param {FetchOptions} options
 * @param {string} label
 * @returns {Promise<unknown>}
 */
async function fetchWithRetry(url, options, label) {
    let lastError;
    for (let attempt = 1; attempt <= MAX_FETCH_ATTEMPTS; attempt += 1) {
        try {
            return await httpFetch(url, options);
        } catch (error) {
            lastError = error;
            const statusMatch = String(error).match(/HTTP\s+(\d{3})/i);
            const status = statusMatch ? Number(statusMatch[1]) : null;
            const isTransient = status == null || status === 429 || status >= 500;
            if (!isTransient || attempt === MAX_FETCH_ATTEMPTS) break;

            const retryDelay = RETRY_BASE_DELAY_MS * (2 ** (attempt - 1));
            console.warn(`${label} failed (attempt ${attempt}/${MAX_FETCH_ATTEMPTS}); retrying in ${retryDelay} ms:`, error);
            await delay(retryDelay);
        }
    }
    throw lastError;
}

/**
 * KSP 2.3+ standalone tags are plain semantic versions, optionally followed by
 * a textual qualifier. Legacy tags contain a second semantic version after the
 * hyphen, for example "2.2.21-2.0.5", and are intentionally excluded here.
 *
 * @param {string} tag
 * @returns {boolean}
 */
function isStandaloneKspTag(tag) {
    return /^\d+\.\d+\.\d+(?:\.\d+)?(?:-[A-Za-z][A-Za-z0-9.-]*)?$/.test(tag);
}

/**
 * @returns {Promise<KspRelease[]>}
 */
async function fetchKspReleases() {
    const releases = [];
    for (let page = 1; ; page += 1) {
        /** @type {ReleasesData} */
        const response = await fetchWithRetry(RELEASES_URL, {
            query: { per_page: 100, page },
            headers: HEADERS,
        }, `KSP releases page ${page}`);
        if (!Array.isArray(response) || response.length === 0) break;

        for (const release of response) {
            const tag = String(release.tag_name || '').trim();
            if (release.draft || !isStandaloneKspTag(tag)) continue;
            releases.push({
                version: tag,
                name: release.name,
                publishedAt: release.published_at,
            });
        }
    }
    return releases;
}

/**
 * Parse the version expression used by KSP's official AGP guard.
 *
 * Examples:
 * - AndroidPluginVersion(8, 10, 0) -> 8.10.0
 * - AndroidPluginVersion(9, 0, 0).alpha(14) -> 9.0.0-alpha14
 *
 * @param {string} source
 * @returns {string | null}
 */
function parseMinimumAgpVersion(source) {
    const match = /MINIMUM_SUPPORTED_AGP_VERSION\s*=\s*AndroidPluginVersion\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)(?:\.([A-Za-z]+)\(\s*(\d+)\s*\))?/m.exec(source);
    if (!match) return null;

    const base = `${match[1]}.${match[2]}.${match[3]}`;
    const qualifier = match[4]?.toLowerCase();
    const qualifierNumber = match[5];
    return qualifier ? `${base}-${qualifier}${qualifierNumber}` : base;
}

/**
 * @param {string} tag
 * @returns {Promise<string | null>}
 */
async function fetchMinimumAgpVersion(tag) {
    const sourceUrl = `https://raw.githubusercontent.com/google/ksp/${encodeURIComponent(tag)}/${SOURCE_PATH}`;
    try {
        const source = await fetchWithRetry(sourceUrl, {
            headers: {
                'accept': 'text/plain',
                'user-agent': HEADERS['user-agent'],
            },
        }, `KSP ${tag} AGP guard source`);
        if (typeof source !== 'string') {
            throw new Error(`Unexpected source response for KSP ${tag}`);
        }
        return parseMinimumAgpVersion(source);
    } catch (error) {
        console.warn(`Failed to read the official AGP guard for KSP ${tag}:`, error);
        return null;
    }
}

/**
 * @param {KspRelease[]} releases
 * @returns {Promise<string[]>}
 */
async function buildCompatibilityLines(releases) {
    const sortedReleases = [ ...releases ]
        .sort((a, b) => compareVersionStringsDescending(a.version, b.version));
    const entries = [];
    for (const release of sortedReleases) {
        const minimumAgpVersion = await fetchMinimumAgpVersion(release.version);
        if (!minimumAgpVersion) continue;

        const publishedAt = release.publishedAt ? new Date(release.publishedAt) : new Date(Number.NaN);
        entries.push({
            kspVersion: release.version,
            minimumAgpVersion,
            publishedAt,
        });
    }

    const latestRelease = sortedReleases[0];
    if (latestRelease && !entries.some(entry => entry.kspVersion === latestRelease.version)) {
        throw new Error(
            `Failed to derive the minimum supported AGP version for the latest standalone KSP release ${latestRelease.version}`,
        );
    }

    return entries.flatMap(({ kspVersion, minimumAgpVersion, publishedAt }) => {
        const dateLine = Number.isNaN(publishedAt.getTime()) ? [] : [ `#${toUpdatedStamp(publishedAt)}` ];
        return [ ...dateLine, `${kspVersion}=${minimumAgpVersion}` ];
    });
}

(async function main() {
    const releases = await fetchKspReleases();
    if (releases.length === 0) {
        throw new Error('Failed to discover any standalone KSP releases');
    }

    const lines = await buildCompatibilityLines(releases);
    if (lines.length === 0) {
        throw new Error('Failed to derive any KSP/AGP compatibility entries from official KSP sources');
    }
    await updateGradleLinesData('ksp-agp-compat', lines, {
        label: 'KSP minimum supported AGP version map',
    });
})().catch((error) => {
    console.error('Failed to fetch KSP/AGP compatibility data:', error);
    process.exitCode = 1;
});
