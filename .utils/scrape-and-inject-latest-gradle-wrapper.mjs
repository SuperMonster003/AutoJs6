// scrape-and-inject-latest-gradle-wrapper.mjs

/**
 * @typedef {Object} Config
 * @property {number | string | null} [majorVersionLimit=null]
 * @property {'bin' | 'all'} [format='bin']
 */
/** @typedef {import('./fetch-and-parse-gradle-releases.mjs').GradleRelease} GradleRelease */

import { fetchGradleReleases } from './fetch-and-parse-gradle-releases.mjs';
import { compareVersionStrings } from './utils/versioning.mjs';
import { readPropertiesSync, writePropertiesSync } from './utils/properties.mjs';

const KEY = 'distributionUrl';
const URL_PREFIX = 'https://services.gradle.org/distributions';

/** @type {Config} */
const config = {
    // @Hint by SuperMonster003 on Sep 10, 2025.
    //  ! Limit major version to 8.x.x, to:
    //  ! - Legacy IDE/tooling compatibility
    //  ! - JDK 17 constraint; Gradle 9+ requires JDK 21
    //  ! - Align with AGP/KGP/plugins matrix
    //  ! - Mitigate deprecation removals
    //  ! Set to null/remove to track latest major.
    //  ! zh-CN:
    //  ! 将主版本限制在 8.x.x, 以便:
    //  ! - 兼容旧版 IDE/Tooling API
    //  ! - 满足 JDK 17 运行时约束, 避免 Gradle 9+ 需 JDK 21
    //  ! - 与 AGP/KGP/第三方插件版本矩阵匹配
    //  ! - 降低弃用 API 移除导致的构建中断风险
    //  ! 如需跟随最新主版本, 将 majorVersionLimit 设为 null 或移除此项.
    majorVersionLimit: '8.x.x',
    format: 'bin',
};

function isVersionLimited() {
    return !config.majorVersionLimit || String(config.majorVersionLimit).match(/([?x])(\.\1)*/i);
}

/**
 * @param {GradleRelease[]} releases
 * @param {string} majorVersionLimit
 * @return {GradleRelease}
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
 * @return {string}
 */
function getLatestGradleUrl(latestGradleVersion) {
    const format = config.format ?? 'bin';
    return `${URL_PREFIX}/gradle-${latestGradleVersion}-${format}.zip`;
}

/**
 * @param {Object} data
 * @param {string} data.latestGradleVersion
 * @param {string} data.latestGradleUrl
 * @param {string} data.majorVersionLimit
 * @return {Promise<void>}
 */
async function updateGradleWrapperFileContent({ latestGradleVersion, latestGradleUrl, majorVersionLimit }) {
    const path = '../gradle/wrapper/gradle-wrapper.properties';
    const messages = [];
    const props = readPropertiesSync(path);
    let propUrl = props[KEY];
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
        const suffix = ` (降级, 受限于 "${config.majorVersionLimit}")`;
        messages.push(`-- ${propUrl}\n-> ${latestGradleUrl}${suffix}`);
        props[KEY] = propUrl.replace(re, `$1${latestGradleVersion}$3`);
    } else if (compareVersionStrings(propVersion, latestGradleVersion) < 0) {
        const suffix = isVersionLimited() ? ` (升级, 但受限于 "${config.majorVersionLimit}")` : ` (升级)`;
        messages.push(`-- ${propUrl}\n-> ${latestGradleUrl}${suffix}`);
        props[KEY] = propUrl.replace(re, `$1${latestGradleVersion}$3`);
    }

    if (messages.length > 0) {
        writePropertiesSync(path, props);
        console.log('[gradle-wrapper.properties] 已更新 (Gradle 版本)');
        messages.forEach((message) => console.log(message));
    } else {
        // console.log('[gradle-wrapper.properties] 无需更新 (Gradle 版本)');
    }
}

function parseMajorVersionLimit() {
    const majorVersionLimit = config.majorVersionLimit ?? 'x.x.x';
    const limits = String(majorVersionLimit).split('.');
    for (let i = 0; i < limits.length; i++) {
        let limit = limits[i];
        if (limit.match(/([?x])(\.\1)*/i)) {
            limits[i] = limit = '9'.repeat(9);
        }
        if (isNaN(parseInt(limit))) {
            throw new Error(`Invalid major version limit: ${majorVersionLimit}`);
        }
    }
    return limits.join('.');
}

(async function main() {
    const releases = await fetchGradleReleases();
    if (!releases || releases.length === 0) {
        throw new Error('No Gradle releases found');
    }
    const majorVersionLimit = parseMajorVersionLimit();
    const latestRelease = getLatestRelease(releases, majorVersionLimit);
    const latestGradleVersion = latestRelease.versionName;
    const latestGradleUrl = getLatestGradleUrl(latestGradleVersion);
    await updateGradleWrapperFileContent({
        latestGradleVersion, latestGradleUrl, majorVersionLimit,
    });
})().catch((e) => {
    console.error('Failed to scrape or inject latest Gradle wrapper:', e);
    process.exit(1);
});