// scrape-and-inject-android-studio-agp-version-map.mjs

import { compareVersionStrings } from './utils/versioning.mjs';
import { fetchStudioAgpTable } from './fetch-and-parse-android-studio-agp-compatibility-table.mjs';
import { readPropertiesSync } from './utils/properties.mjs';
import { updateGradleMapData } from './utils/update-helper.mjs';

const props = readPropertiesSync();

const version = {
    MIN_IDE: props.get('MIN_SUPPORTED_ANDROID_STUDIO_IDE_VERSION'),
    MIN_AGP: props.get('MIN_SUPPORTED_ANDROID_STUDIO_AGP_VERSION'),
};

(async function main() {
    const agpTable = await fetchStudioAgpTable();
    const map = new Map();

    for (const { studioVersion, agpRange } of agpTable) {
        const targetStudioVersion = studioVersion.match(/\d{2,}\.\d+\.\d+/)?.[0];
        if (!targetStudioVersion) continue;

        const [ _, targetAgpVersion ] = agpRange.split('-');
        if (!map.has(targetStudioVersion) || compareVersionStrings(map.get(targetStudioVersion), targetAgpVersion) > 0) {
            map.set(targetStudioVersion, targetAgpVersion);
        }
        if (compareVersionStrings(targetStudioVersion, version.MIN_IDE) <= 0) break;
        if (compareVersionStrings(targetAgpVersion, version.MIN_AGP) <= 0) break;
    }

    await updateGradleMapData('android-studio-agp-compat', map, {
        label: 'Android Studio and AGP compatibility map',
        sort: 'key.descending.as.version',
    });
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
