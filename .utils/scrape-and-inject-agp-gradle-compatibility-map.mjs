// scrape-and-inject-agp-gradle-compatibility-map.mjs

import { compareVersionStrings } from './utils/versioning.mjs';
import { findTargetRows } from './utils/puppeteer-helpers.mjs';
import { getMinSupportedAgpVersion, getMinSupportedGradleVersion } from './utils/properties.mjs';
import { updateGradleMapData } from './utils/update-helper.mjs';

// @Hint by SuperMonster003 on Jan 16, 2026.
//  ! Invalid since Jan 16, 2026.
//  ! zh-CN: 自 2026 年 1 月 16 日起无效.
//  # const URL = 'https://developer.android.com/build/releases/gradle-plugin#updating-gradle';

const URL = 'https://developer.android.com/build/releases/about-agp';

(async function main() {
    const rows = await findTargetRows({
        url: URL,
        tableSelector: '.devsite-table-wrapper table',
        tableFilter: {
            'tr th': [
                /Plugin version/i,
                /Minimum required Gradle version/i,
            ],
        },
        tableRowSelector: 'tbody tr',
        tableDataSelector: 'td',
        tableDataStructure: [
            'pluginVersion',
            'gradleVersion',
        ],
    });
    const minSupportedAgpVersion = getMinSupportedAgpVersion();
    const minSupportedGradleVersion = getMinSupportedGradleVersion();
    const map = new Map();
    for (const { pluginVersion, gradleVersion } of rows) {
        if (compareVersionStrings(pluginVersion, minSupportedAgpVersion) < 0) continue;
        if (compareVersionStrings(gradleVersion, minSupportedGradleVersion) < 0) continue;
        map.set(pluginVersion, gradleVersion);
    }
    await updateGradleMapData('agp-gradle-compat', map, {
        label: 'AGP and Gradle compatibility map',
        sort: 'key.descending.as.version',
    });
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
