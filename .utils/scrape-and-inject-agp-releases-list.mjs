// scrape-and-inject-agp-releases-list.mjs

import * as cheerio from 'cheerio';
import { compareVersionStrings } from './utils/versioning.mjs';
import { getMinSupportedAgpVersion } from './utils/properties.mjs';
import { updateGradleListData } from './utils/update-helper.mjs';

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
    const agpList = new Set(Array.from(results).filter(v => compareVersionStrings(v, minSupportedVersion) >= 0));
    await updateGradleListData('agp-releases', agpList, {
        label: 'AGP releases list',
        sort: 'descending.as.version',
    });
})().catch(err => {
    console.error('Failed to fetch AGP releases:', err);
    process.exit(1);
});
