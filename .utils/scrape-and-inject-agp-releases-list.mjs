// scrape-and-inject-agp-releases-list.mjs

import { compareVersionStrings, compareVersionStringsDescending } from './utils/versioning.mjs';
import { getMinSupportedAgpVersion } from './utils/properties.mjs';
import { sleep } from './utils/async.mjs';
import { updateGradleListData } from './utils/update-helper.mjs';

// @Hint by JetBrains AI Assistant (GPT-5.2) on Feb 23, 2026.
//  ! Use Google Maven metadata instead of scraping developer.android.com to avoid frequent network timeouts.
//  ! zh-CN: 使用 Google Maven 元数据替代抓取 developer.android.com, 以避免频繁的网络超时.
//  # const URL = 'https://developer.android.com/reference/tools/gradle-api';
const MAVEN_METADATA_URL = 'https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml';

/**
 * Fetch text with retry/backoff.
 * zh-CN: 带重试/退避的文本拉取.
 *
 * @param {string | URL | Request} url
 * @param attempts
 * @param timeoutMs
 */
async function fetchTextWithRetry(url, { attempts = 5, timeoutMs = 60000 } = {}) {
    let lastErr;
    for (let i = 0; i < attempts; i++) {
        const controller = new AbortController();
        const t = setTimeout(() => controller.abort(new Error(`Request timed out after ${timeoutMs}ms`)), timeoutMs);
        try {
            const res = await fetch(url, {
                signal: controller.signal,
                headers: {
                    // Some CDNs behave better with a UA.
                    // zh-CN: 某些 CDN 在带 UA 的情况下表现更稳定.
                    'User-Agent': 'agp-releases-list-updater/1.0 (+nodejs)',
                    'Accept': 'application/xml,text/xml,text/plain,*/*',
                },
            });
            if (!res.ok) {
                // noinspection ExceptionCaughtLocallyJS
                throw new Error(`HTTP ${res.status} ${res.statusText}`);
            }
            return await res.text();
        } catch (e) {
            lastErr = e;
            // Exponential backoff with jitter.
            // zh-CN: 带抖动的指数退避.
            const backoff = Math.min(2000 * (2 ** i), 15000) + Math.floor(Math.random() * 250);
            await sleep(backoff);
        } finally {
            clearTimeout(t);
        }
    }
    throw lastErr;
}

/**
 * Minimal parser: extract <version>...</version>.
 * zh-CN: 最小解析器: 提取 <version>...</version>.
 *
 * @param {string} xml
 */
function extractVersionsFromMavenMetadataXml(xml) {
    const versions = [];
    const re = /<version>\s*([^<\s]+)\s*<\/version>/g;
    let m;
    while ((m = re.exec(xml)) !== null) {
        versions.push(m[1]);
    }
    return versions;
}

(async function main() {
    const xml = await fetchTextWithRetry(MAVEN_METADATA_URL);

    const versions = extractVersionsFromMavenMetadataXml(xml);

    const minSupportedVersion = getMinSupportedAgpVersion();
    const agpListAll = new Set(versions.filter(v => compareVersionStrings(v, minSupportedVersion) >= 0));
    const agpListMajor = new Map();

    const rex = /^(\d+\.\d+)\.(\d+.*)$/
    for (let v of agpListAll) {
        const matched = rex.exec(v);
        if (!matched) {
            continue;
        }
        const major = matched[1];
        if (!agpListMajor.has(major)) {
            agpListMajor.set(major, v);
        } else {
            if (compareVersionStrings(v, agpListMajor.get(major)) > 0) {
                agpListMajor.set(major, v);
            }
        }
    }

    const agpListFinal = new Set([...agpListMajor.values()].sort(compareVersionStringsDescending));

    await updateGradleListData('agp-releases', agpListFinal, {
        label: 'AGP releases list',
        sort: 'descending.as.version',
    });
})().catch(err => {
    console.error('Failed to fetch AGP releases:', err);
    process.exit(1);
});
