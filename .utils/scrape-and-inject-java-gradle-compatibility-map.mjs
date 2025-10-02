// scrape-and-inject-java-gradle-compatibility-map.mjs

import { findTargetRows } from './utils/puppeteer-helpers.mjs';
import { getMinSupportedJavaVersionInt } from './utils/properties.mjs';
import { updateGradleMapData } from './utils/update-helper.mjs';

const URL = 'https://docs.gradle.org/current/userguide/compatibility.html#java_runtime';

(async function main() {
    const rows = await findTargetRows({
        url: URL,
        tableSelector: 'table.tableblock',
        tableFilter: {
            caption: /java compatibility/i,
        },
        tableRowSelector: 'tbody tr',
        tableDataSelector: 'td',
        tableDataStructure: [
            { java: /^\d+$/ },
            { toolchain: /^N\/A$|\d+\.\d+/ },
            { gradle: /^N\/A$|\d+\.\d+/ },
        ],
    });
    const map = new Map();
    const minSupportedJavaVersionInt = getMinSupportedJavaVersionInt();
    for (const { java, gradle } of rows) {
        const javaInt = parseInt(java);
        if (Number.isNaN(javaInt)) {
            throw Error(`Invalid java version int: "${java}"`);
        }
        if (javaInt >= minSupportedJavaVersionInt) {
            map.set(`${javaInt}`, gradle);
        }
    }
    await updateGradleMapData('java-gradle-compat', map, {
        label: 'Java and Gradle compatibility map',
        sort: 'key.descending.as.number',
    });
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
