// scrape-and-inject-latest-gradle-wrapper.mjs

import * as fs from 'node:fs';
import * as path from 'node:path';
import { compareVersionStrings } from './utils/versioning.mjs';
import { fetchGradleReleases } from './fetch-and-parse-gradle-releases.mjs';
import { readPropertiesSync, writePropertiesSyncWithMap } from './utils/properties.mjs';

const KEY = 'distributionUrl';
const URL_PREFIX = 'https://services.gradle.org/distributions';

/** @type {GradleReleaseConfig} */
const config = {
    // @Hint by SuperMonster003 on Sep 10, 2025.
    //  ! Limit major version to 8.Y.Z, to:
    //  ! - Legacy IDE/tooling compatibility
    //  ! - JDK 17 constraint; Gradle 9+ requires JDK 21
    //  ! - Align with AGP/KGP/plugins matrix
    //  ! - Mitigate deprecation removals
    //  ! Set to null/remove to track latest major.
    //  ! zh-CN:
    //  ! 将主版本限制在 8.Y.Z, 以便:
    //  ! - 兼容旧版 IDE/Tooling API
    //  ! - 满足 JDK 17 运行时约束, 避免 Gradle 9+ 需 JDK 21
    //  ! - 与 AGP/KGP/第三方插件版本矩阵匹配
    //  ! - 降低弃用 API 移除导致的构建中断风险
    //  ! 如需跟随最新主版本, 将 majorVersionLimit 设为 null 或移除此项.
    //  # majorVersionLimit: '8.Y.Z',
    majorVersionLimit: null,
    format: 'bin',
};

/**
 * @param {GradleRelease[]} releases
 * @param {string} majorVersionLimit
 * @returns {GradleRelease}
 */
function getLatestRelease(releases, majorVersionLimit) {
    const limitedRelease = releases
        .sort((a, b) => compareVersionStrings(b.versionName, a.versionName))
        .find((release) => compareVersionStrings(release.versionName, majorVersionLimit) <= 0);
    if (!limitedRelease) {
        throw new Error(`No release found for major version limit: "${majorVersionLimit}"`);
    }
    return limitedRelease;
}

/**
 * @param {string} latestGradleVersion
 * @returns {string}
 */
function getLatestGradleUrl(latestGradleVersion) {
    const format = config.format ?? 'bin';
    return `${URL_PREFIX}/gradle-${latestGradleVersion}-${format}.zip`;
}

/**
 * @param {Object} data
 * @param {string} [data.fileName='gradle-wrapper.properties']
 * @param {string} [data.dirName='../gradle/wrapper']
 * @param {string} data.latestGradleVersion
 * @param {string} data.latestGradleUrl
 * @param {string|number} data.rawMajorVersionLimit
 * @returns {Promise<void>}
 */
async function updateGradleWrapperFileContent(
    {
        fileName = 'gradle-wrapper.properties',
        dirName = '../gradle/wrapper',
        latestGradleVersion,
        latestGradleUrl,
        rawMajorVersionLimit,
    },
) {
    const path = `${dirName.replace(/\/?$/, '/')}${fileName}`;
    const majorVersionLimit = parseMajorVersionLimit(rawMajorVersionLimit);
    const messages = [];
    const props = readPropertiesSync(path);
    let propUrl = props.get(KEY);
    if (!propUrl) {
        propUrl = latestGradleUrl;
        messages.push(`Append: ${KEY}=${propUrl}`);
    }
    const re = /(gradle-)(.+)(-(?:bin|all)\.zip)/;
    let propVersion = propUrl.match(re)?.[2];
    if (!propVersion) {
        messages.push(`-- ${propUrl}\n-> ${latestGradleUrl}`);
        propUrl = latestGradleUrl;
        propVersion = latestGradleVersion;
    }

    if (compareVersionStrings(propVersion, majorVersionLimit) > 0) {
        const suffix = ` (downgrade, limited by "${rawMajorVersionLimit}")`;
        messages.push(`-- ${propUrl}\n-> ${latestGradleUrl}${suffix}`);
        props.set(KEY, propUrl.replace(re, `$1${latestGradleVersion}$3`));
    } else if (compareVersionStrings(propVersion, latestGradleVersion) < 0) {
        const suffix = ` (upgrade)`;
        messages.push(`-- ${propUrl}\n-> ${latestGradleUrl}${suffix}`);
        props.set(KEY, propUrl.replace(re, `$1${latestGradleVersion}$3`));
    }

    if (messages.length > 0) {
        writePropertiesSyncWithMap(path, props);
        const maxLength = Math.max(...messages.join('\n').split('\n').map(s => s.length));
        const SEP_EQ = '='.repeat(maxLength);
        const SEP_DASH = '-'.repeat(maxLength);
        console.log(`[${fileName}] Updated (Gradle version)`);
        console.log(SEP_EQ);
        messages.forEach((msg, idx) => {
            console.log(msg);
            if (idx !== messages.length - 1) {
                console.log(SEP_DASH);
            }
        });
        console.log(SEP_EQ);
    } else {
        // console.log(`[${fileName}] No update needed (Gradle version)`);
    }
}

/**
 * @param {string|number} rawMajorVersionLimit
 * @returns {string}
 */
function parseMajorVersionLimit(rawMajorVersionLimit) {
    const majorVersionLimit = rawMajorVersionLimit ?? 'x.y.z';
    const limits = String(majorVersionLimit).split('.');
    for (let i = 0; i < limits.length; i++) {
        let limit = limits[i];
        if (limit.match(/([xyz*?])(\.\1)*/i)) {
            limits[i] = limit = '9'.repeat(9);
        }
        if (isNaN(parseInt(limit))) {
            throw new Error(`Invalid major version limit: ${majorVersionLimit}`);
        }
    }
    return limits.join('.');
}

/**
 * @param {string} wrappersPath
 * @returns {Array<{dirName: string, rawMajorVersionLimit: string}>}
 */
function listMatchingSubdirectories(wrappersPath) {
    const pattern = /^g(\d+)$/;
    const fullWrappersPath = path.resolve(wrappersPath);

    if (!fs.existsSync(fullWrappersPath)) {
        console.warn(`Wrappers directory not found: ${fullWrappersPath}`);
        return [];
    }

    const results = [];
    const entries = fs.readdirSync(fullWrappersPath, { withFileTypes: true });
    entries.forEach(entry => {
        if (!entry.isDirectory()) return;
        const matched = pattern.exec(entry.name);
        const version = matched?.[1] ?? null;
        if (version === null) return;
        results.push({
            dirName: `${wrappersPath.replace(/\/?$/, '/')}${entry.name}/gradle/wrapper`,
            rawMajorVersionLimit: `${version}.y.z`,
        });
    });
    return results;
}

(async function main() {
    const releases = await fetchGradleReleases();
    if (!releases || releases.length === 0) {
        throw new Error('No Gradle releases found');
    }
    const wrapperDir = '../gradle/wrapper';
    const modernWrappersDir = '../gradle/wrappers';

    const candidates = [ {
        dirName: wrapperDir,
        rawMajorVersionLimit: config.majorVersionLimit,
    } ].concat(listMatchingSubdirectories(modernWrappersDir));

    for (const { dirName, rawMajorVersionLimit } of candidates) {
        const latestRelease = getLatestRelease(releases, parseMajorVersionLimit(rawMajorVersionLimit));
        const latestGradleVersion = latestRelease.versionName;
        const latestGradleUrl = getLatestGradleUrl(latestGradleVersion);
        await updateGradleWrapperFileContent({
            dirName, latestGradleVersion, latestGradleUrl, rawMajorVersionLimit,
        });
    }
})().catch(err => {
    console.error('Failed to scrape or inject latest Gradle wrapper:', err);
    process.exit(1);
});
