// scrape-and-inject-gradle-kotlin-compatibility-map.mjs

import { findTargetRows } from './utils/puppeteer-helpers.mjs';
import { compareVersionStrings } from './utils/versioning.mjs';
import { getMinSupportedGradleVersion } from './utils/properties.mjs';
import { updateGradleMapData } from './utils/update-helper.mjs';

const URL = 'https://docs.gradle.org/current/userguide/compatibility.html#kotlin';

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
     * @example Map<kotlin,gradle>
     * Map(10) {
     *   ... ...
     *   "8.12" => "2.0.21",
     *   "8.11" => "2.0.20",
     *   ... ...
     * }
     * @type {Map<string, string>}
     */
    const map = new Map();
    const minSupportedGradleVersion = getMinSupportedGradleVersion();

    for (const { kotlin, gradle } of rows) {
        if (compareVersionStrings(gradle, minSupportedGradleVersion) < 0) continue;
        map.set(gradle, kotlin);
    }

    await updateGradleMapData('gradle-kotlin-compat', map, {
        label: 'Gradle and Kotlin compatibility map',
        sort: 'value.descending.as.version',
    });
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
