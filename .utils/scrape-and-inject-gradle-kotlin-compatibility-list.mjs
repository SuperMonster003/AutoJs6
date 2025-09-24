// scrape-and-inject-gradle-kotlin-compatibility-list.mjs

/** @typedef {import('puppeteer').Page} Page */

import { findTargetRows } from './utils/puppeteer-helpers.mjs';
import { compareVersionStrings } from './utils/versioning.mjs';
import { getMinSupportedGradleVersion } from './utils/properties.mjs';
import { updateAnchoredListInFile } from './utils/anchors.mjs';

const URL = 'https://docs.gradle.org/current/userguide/compatibility.html#kotlin';

const unofficialKotlinCompatibilityList = {
    // '8.14': '2.1.10',
    // '8.13': '2.1.10',
};

(async function main() {
    const rows = await findTargetRows({
        url: URL,
        tableSelector: 'table.tableblock',
        tableFilter: {
            th: /Embedded Kotlin version|Minimum Gradle version|Kotlin Language version/i,
        },
        tableDataStructure: [
            'kotlin',
            'gradle',
            'ktLanguage',
        ],
    });
    /**
     * @type {{ [gradle: string]: { kotlin: string, isUnofficial: boolean } }}
     */
    const map = {};
    const minSupportedGradleVersion = getMinSupportedGradleVersion();

    for (const { kotlin, gradle } of rows) {
        if (compareVersionStrings(gradle, minSupportedGradleVersion) < 0) continue;
        map[gradle] = { kotlin, isUnofficial: false };
    }

    Object.entries(unofficialKotlinCompatibilityList).forEach(([ gradle, kotlin ]) => {
        if (!(gradle in map)) map[gradle] = { kotlin, isUnofficial: true };
    });

    await updateAnchoredListInFile('../settings.gradle.kts', {
        anchorTag: 'GRADLE_KOTLIN_COMPATIBILITY_LIST',
        listName: 'gradleKotlinCompatibility',
        lines: Object.entries(map)
            .sort((a, b) => compareVersionStrings(b[1]['kotlin'], a[1]['kotlin']))
            .map(([ gradle, { kotlin, isUnofficial } ]) => {
                return isUnofficial
                    ? `"${gradle}" to "${kotlin}", /* Unofficial. */`
                    : `"${gradle}" to "${kotlin}",`;
            }),
        updatedLabel: 'Gradle and Kotlin compatibility list',
    });
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
