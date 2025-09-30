// fetch-and-parse-gradle-releases.mjs

import * as cheerio from 'cheerio';
import { fileURLToPath } from 'node:url';

const LINK_PREFIX = 'https://gradle.org';
const URL = `${LINK_PREFIX}/releases`;

/**
 * @returns {Promise<GradleRelease[]>}
 */
export async function fetchGradleReleases() {
    const $ = cheerio.load(await fetch(URL).then(r => r.text()));

    const contents = $('.resources-contents').filter((_, el) => {
        return $(el).find('.u-text-with-icon').length > 0;
    });

    if (contents.length === 0) {
        throw new Error('Could not find an element with its class named "resources-contents"');
    }

    const rows = [];
    contents.find('a[name]').each((_, a) => {
        const versionName = $(a).attr('name');
        const releaseDate = $(a).nextUntil('div.indent').find('span').filter((_, span) => {
            return /[A-Z][a-z]{2}\s+\d{1,2},\s*\d{4}/.test($(span).text());
        }).first().text();
        const link = {};
        $(a).nextUntil('div.indent').next().find('a').each((_, a) => {
            const href = $(a).attr('href');
            if (href.includes('version=') && href.includes('format=')) {
                const format = href.match(/format=(\w+)/)?.[1] ?? null;
                if (format !== null) {
                    link[format] = href.match(/https?:\/\//) ? href : `${LINK_PREFIX}${href}`;
                }
            } else if (/-(bin|all)\.\w+/.test(href)) {
                const format = href.match(/-(bin|all)\.\w+/)?.[1] ?? null;
                if (format !== null) {
                    link[format] = href.match(/https?:\/\//) ? href : `${LINK_PREFIX}${href}`;
                }
            } else if (href.includes('checksums')) {
                link.checksums = href.match(/https?:\/\//) ? href : `${LINK_PREFIX}${href}`;
            }
        });
        rows.push({ versionName, releaseDate, link });
    });

    return rows;
}

async function main() {
    console.table((await fetchGradleReleases()).map(({ versionName, releaseDate, link }) => ({
        versionName, releaseDate, 'link.bin': link.bin, 'link.all': link.all, 'link.checksums': link.checksums,
    })));
}

// Determine if this file is being run directly.
// zh-CN: 判断是否为直接执行该文件.
if (fileURLToPath(import.meta.url) === process.argv[1]) {
    main().catch(err => {
        console.error(err);
        process.exitCode = 1;
    });
}
