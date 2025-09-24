// scrape-android-studio-agp_version_maps.mjs

import { compareVersionStrings } from './utils/versioning.mjs';
import { fetchStudioAgpTable } from './fetch-and-parse-android-studio-agp-compatibility-table.mjs';
import { readPropertiesSync } from './utils/properties.mjs';
import { updateAnchoredMapInFile } from './utils/anchors.mjs';

const props = readPropertiesSync();

const version = {
    MIN_IDE: props['MIN_SUPPORTED_ANDROID_STUDIO_IDE_VERSION'],
    MIN_AGP: props['MIN_SUPPORTED_ANDROID_STUDIO_AGP_VERSION'],
};

(async function main() {
    const agpTable = await fetchStudioAgpTable();
    /** @type {{ [targetStudioVersion: string]: string }} */
    const agpMap = {};

    for (const { studioVersion, agpRange } of agpTable) {
        const targetStudioVersion = studioVersion.match(/\d{2,}\.\d+\.\d+/)?.[0];
        if (!targetStudioVersion) continue;

        const [ _, targetAgpVersion ] = agpRange.split('-');
        if (!(targetStudioVersion in agpMap) || compareVersionStrings(agpMap[targetStudioVersion], targetAgpVersion) > 0) {
            agpMap[targetStudioVersion] = targetAgpVersion;
        }
        if (compareVersionStrings(targetStudioVersion, version.MIN_IDE) <= 0) break;
        if (compareVersionStrings(targetAgpVersion, version.MIN_AGP) <= 0) break;
    }

    await updateAnchoredMapInFile('../settings.gradle.kts', {
        anchorTag: 'ANDROID_STUDIO_AGP_VERSION_MAP',
        mapName: 'agpVersionMap',
        lines: Object.entries(agpMap).map(([ studioVer, agpVer ]) => `"${studioVer}" to "${agpVer}",`),
        updatedLabel: 'AGP version map',
    });
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
