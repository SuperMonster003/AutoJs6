// scrape-and-inject-kotlin-r8-compatibility-map.mjs
// after: [ scrape-and-inject-gradle-kotlin-compatibility-map.mjs ]

import * as cheerio from 'cheerio';
import { httpFetch } from './utils/fetch.mjs';
import { updateGradleMapData } from './utils/update-helper.mjs';

const URL = 'https://developer.android.com/build/kotlin-support';

/**
 * @param {string} value
 * @returns {string | null}
 */
function parseKotlinCompatibilityLine(value) {
    return String(value || '').match(/\b(\d+\.\d+)\b/)?.[1] ?? null;
}

/**
 * @param {string} value
 * @returns {string | null}
 */
function parseR8Version(value) {
    return String(value || '').match(/\b(\d+\.\d+\.\d+)\b/)?.[1] ?? null;
}

/**
 * @param {string} html
 * @returns {Map<string, string>}
 */
function parseCompatibilityMap(html) {
    const $ = cheerio.load(html);
    const result = new Map();

    $('table').each((_, table) => {
        const headers = $(table).find('tr th').map((__, th) => $(th).text().trim()).get();
        const isTarget = headers.some(text => /Kotlin version/i.test(text))
            && headers.some(text => /Required R8 version/i.test(text));
        if (!isTarget) return;

        $(table).find('tbody tr').each((__, row) => {
            const cells = $(row).find('td').map((___, td) => {
                const cell = $(td).clone();
                cell.find('sup').remove();
                return cell.text().trim();
            }).get();
            if (cells.length < 3) return;
            const kotlinLine = parseKotlinCompatibilityLine(cells[0]);
            const r8Version = parseR8Version(cells[2]);
            if (!kotlinLine || !r8Version) return;

            const previous = result.get(kotlinLine);
            if (previous && previous !== r8Version) {
                throw new Error(
                    'Conflicting R8 requirements for Kotlin ' + kotlinLine + ': ' + previous + ' vs ' + r8Version,
                );
            }
            result.set(kotlinLine, r8Version);
        });
    });

    return result;
}

(async function main() {
    const html = await httpFetch(URL, {
        headers: {
            accept: 'text/html,application/xhtml+xml',
            'user-agent': 'autojs6-kotlin-r8-compat-scraper',
        },
        timeout: 30000,
    });
    if (typeof html !== 'string') {
        throw new Error('Unexpected Android compatibility page response');
    }

    const map = parseCompatibilityMap(html);
    if (map.size < 5) {
        throw new Error('Parsed only ' + map.size + ' Kotlin/R8 compatibility rows');
    }

    await updateGradleMapData('kotlin-r8-compat', map, {
        label: 'Kotlin minimum required R8 version map',
        sort: 'key.descending.as.version',
    });
})().catch(error => {
    console.error('Failed to fetch Kotlin/R8 compatibility data:', error);
    process.exitCode = 1;
});
