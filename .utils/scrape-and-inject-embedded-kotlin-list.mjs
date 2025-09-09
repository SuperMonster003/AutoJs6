// scrape-and-inject-embedded-kotlin-list.mjs

/** @typedef {import('puppeteer').Page} Page */

import puppeteer from 'puppeteer';
import { getMinSupportedGradleVersion } from './utils/properties.mjs';
import { updateAnchoredListInFile } from './utils/anchors.mjs';
import { compareVersionStrings } from './utils/versioning.mjs';
import { autoScroll } from './utils/puppeteer-helpers.mjs';
import { sleep } from './utils/async.mjs';

const URL = 'https://docs.gradle.org/current/userguide/compatibility.html#kotlin';

const unofficialKotlinCompatibilityList = {
    '8.14': '2.1.10',
    '8.13': '2.1.10',
};

/**
 * @param {Page} page
 * @return {Promise<string[][] | null>}
 */
async function findTargetRows(page) {
    return await page.evaluate(() => {
        /** @type {HTMLTableElement[]} */
        const targets = Array.from(document.querySelectorAll('table.tableblock'));
        const target = targets.find(t => {
            const tableHeadList = t.querySelectorAll('th');
            return Array.from(tableHeadList).some(th => /Embedded Kotlin version|Minimum Gradle version|Kotlin Language version/i.test(th.textContent));
        });
        if (!target) return null;

        return Array.from(target.querySelectorAll('tbody tr'))
            .map(tr => {
                const tds = tr.querySelectorAll('td');
                if (tds.length < 3) return null;
                const kotlin = tds[0]?.querySelector('p')?.textContent?.trim();
                const gradle = tds[1]?.querySelector('p')?.textContent?.trim();
                const ktLanguage = tds[2]?.querySelector('p')?.textContent?.trim();
                return kotlin && gradle && ktLanguage ? [ kotlin, gradle, ktLanguage ] : null;
            })
            .filter(Boolean);
    });
}

(async function main() {
    const browser = await puppeteer.launch({ headless: true });
    const page = await browser.newPage();
    try {
        await page.goto(URL, { waitUntil: 'networkidle0', timeout: 120000 });

        // 页面为懒加载: 滚动并多次尝试, 直到目标表格出现或超时
        let rows = null;
        const deadline = Date.now() + 30000; // 30s 总超时
        while (Date.now() < deadline) {
            rows = await findTargetRows(page);
            if (rows && rows.length) break;
            await autoScroll(page);
            await sleep(300);
        }
        if (!rows || !rows.length) {
            throw new Error('Unable to locate target table rows (lazy-loaded content not found in time)');
        }

        /**
         * @type {{ [gradle: string]: { kotlin: string, isUnofficial: boolean } }}
         */
        const map = {};
        const minSupportedGradleVersion = getMinSupportedGradleVersion();

        for (const [ kotlin, gradle ] of rows) {
            if (compareVersionStrings(gradle, minSupportedGradleVersion) < 0) continue;
            map[gradle] = { kotlin, isUnofficial: false };
        }

        Object.entries(unofficialKotlinCompatibilityList).forEach(([ gradle, kotlin ]) => {
            if (!(gradle in map)) map[gradle] = { kotlin, isUnofficial: true };
        });

        await updateAnchoredListInFile('../settings.gradle.kts', {
            anchorTag: 'EMBEDDED_KOTLIN_LIST',
            listName: 'embeddedKotlin',
            lines: Object.entries(map)
                .sort((a, b) => compareVersionStrings(b[1]['kotlin'], a[1]['kotlin']))
                .map(([ gradle, { kotlin, isUnofficial } ]) => {
                    return isUnofficial
                        ? `"${gradle}" to "${kotlin}", /* Unofficial. */`
                        : `"${gradle}" to "${kotlin}",`;
                }),
            updatedLabel: 'Java 与 Gradle 兼容性映射',
        });
    } finally {
        await browser.close();
    }
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});