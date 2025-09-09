// fetch-and-parse-android-studio-agp-compatibility-table.mjs

import fetch from 'node-fetch';
import { load } from 'cheerio';
import { fileURLToPath } from 'node:url';

const URL = 'https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility';

/**
 * @return {Promise<Array<{ studioVersion: string, agpRange: string }>>}
 */
export async function fetchStudioAgpTable() {
    const html = await fetch(URL).then(r => r.text());
    const $ = load(html);

    const targetTable = $('table').filter((_, el) => {
        let text = $(el).find('th').first().text();
        return /Android Studio version/i.test(text);
    });

    if (!targetTable.length) {
        throw new Error('未找到目标表格, 页面结构可能已变更');
    }

    const rows = [];
    targetTable.find('tbody tr').each((_, tr) => {
        const cells = $(tr).find('td').map((_, td) => {
            return $(td).text().trim().replace(/\s+/g, ' ');
        }).get();
        if (cells.length < 2) return;
        const [ studioVersion, agpRange ] = cells;
        rows.push({ studioVersion, agpRange });
    });

    return rows;
}

async function main() {
    console.table(await fetchStudioAgpTable());
}

// 判断是否为直接执行该文件
if (fileURLToPath(import.meta.url) === process.argv[1]) {
    main().catch(err => {
        console.error(err);
        process.exitCode = 1;
    });
}