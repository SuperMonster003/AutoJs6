// scrape-and-inject-java-gradle-compatibility-list.mjs

import { getMinSupportedJavaVersionInt } from './utils/properties.mjs';
import { updateAnchoredListInFile } from './utils/anchors.mjs';
import { findTargetRows } from './utils/puppeteer-helpers.mjs';

const URL = 'https://docs.gradle.org/current/userguide/compatibility.html#java_runtime';

const unofficialGradleCompatibilityList = {
    25: '9.0',
};

(async function main() {
    const rows = await findTargetRows({
        url: URL,
        tableSelector: 'table.tableblock',
        tableFilter: {
            'caption': `:RegExp:i:${/java compatibility/.source}`,
        },
        tableRowSelector: 'tbody tr',
        tableDataSelector: 'td',
        tableDataStructure: [
            { 'java': `:RegExp:${/^\d+$/.source}` },
            { 'toolchain': `:RegExp:${/^N\/A$|\d+\.\d+/.source}` },
            { 'gradle': `:RegExp:${/^N\/A$|\d+\.\d+/.source}` },
        ],
    });
    /**
     * @type {{ [javaInt: string]: string }}
     */
    const map = {};
    const minSupportedJavaVersionInt = getMinSupportedJavaVersionInt();
    for (const { java, gradle } of rows) {
        const javaInt = parseInt(java);
        if (Number.isNaN(javaInt)) {
            throw Error(`Invalid java version int: ${java}`);
        }
        if (javaInt >= minSupportedJavaVersionInt) {
            map[javaInt] = gradle;
        }
    }

    await updateAnchoredListInFile('../settings.gradle.kts', {
        anchorTag: 'JAVA_GRADLE_COMPATIBILITY_LIST',
        listName: 'javaGradleCompatibility',
        lines: Object.entries(map)
            .sort((a, b) => Number(b[0]) - Number(a[0]))
            .map(([ java, gradle ]) => {
                if (gradle === 'N/A') {
                    const unofficialGradleVersion = unofficialGradleCompatibilityList[java];
                    if (unofficialGradleVersion) {
                        return `${java} to "${unofficialGradleVersion}", /* Unofficial. */`;
                    }
                }
                return `${java} to "${gradle}",`;
            }),
        updatedLabel: 'Java 与 Gradle 兼容性映射',
    });
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});