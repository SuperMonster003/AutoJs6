// scrape-and-inject-agp-gradle-compatibility-list.mjs

import { compareVersionStrings } from './utils/versioning.mjs';
import { findTargetRows } from './utils/puppeteer-helpers.mjs';
import { getMinSupportedAgpVersion, getMinSupportedGradleVersion } from './utils/properties.mjs';
import { updateAnchoredListInFile } from './utils/anchors.mjs';

const URL = 'https://developer.android.com/build/releases/gradle-plugin#updating-gradle';

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
    const map = {};
    for (const { pluginVersion, gradleVersion } of rows) {
        if (compareVersionStrings(pluginVersion, minSupportedAgpVersion) < 0) continue;
        if (compareVersionStrings(gradleVersion, minSupportedGradleVersion) < 0) continue;
        map[pluginVersion] = gradleVersion;
    }

    await updateAnchoredListInFile('../settings.gradle.kts', {
        anchorTag: 'AGP_GRADLE_COMPATIBILITY_LIST',
        listName: 'agpGradleCompatibility',
        lines: Object.entries(map)
            .sort((a, b) => compareVersionStrings(b[0], a[0]))
            .map(([ pluginVersion, gradleVersion ]) => `"${pluginVersion}" to "${gradleVersion}",`),
        updatedLabel: 'AGP and Gradle compatibility list',
    });
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
