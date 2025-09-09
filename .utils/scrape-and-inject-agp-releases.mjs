// scrape-and-inject-agp-releases.mjs

import * as cheerio from 'cheerio';
import { getMinSupportedAgpVersion } from './utils/properties.mjs';
import { compareVersionStrings, compareVersionStringsDescending } from './utils/versioning.mjs';
import { updateAnchoredListInFile } from './utils/anchors.mjs';

const URL = 'https://developer.android.com/reference/tools/gradle-api';

(async function main() {
    const res = await fetch(URL);
    const $ = cheerio.load(await res.text());
    const results = new Set();
    $('table').find('a').each((_, el) => {
        const a = $(el);
        const href = a.attr('href');
        if (href.includes('reference/tools/gradle-api')) {
            results.add(a.text());
        }
    });
    const minSupportedVersion = getMinSupportedAgpVersion();
    const agpList = Array.from(results).filter(v => compareVersionStrings(v, minSupportedVersion) >= 0).sort(compareVersionStringsDescending);
    await updateAnchoredListInFile('../settings.gradle.kts', {
        anchorTag: 'ANDROID_GRADLE_PLUGIN_RELEASES_LIST',
        listName: 'agpReleases',
        lines: agpList.map(v => `"${v}",`),
        updatedLabel: 'AGP 发行版本数据',
    });
})().catch((e) => {
    console.error('Failed to fetch AGP releases:', e)
    process.exit(1);
})