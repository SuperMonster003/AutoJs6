// fetch-and-parse-android-studio-agp-compatibility-table.mjs

import { fileURLToPath } from 'node:url';
import { findTargetRows } from './utils/puppeteer-helpers.mjs';

const URL = 'https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility';

/**
 * @param {string} s
 * @returns {string}
 */
const normalize = (s) => s.trim().replace(/\s+/g, ' ');

/**
 * @returns {Promise<Array<{ studioVersion: string, agpRange: string }>>}
 */
export async function fetchStudioAgpTable() {
    // @ts-ignore
    return findTargetRows({
        url: URL,
        expandBeforeFinding: {
            toggleSelector: 'a.exw-control',
        },
        tableFilter: {
            th: /Android Studio version/i,
        },
        tableDataStructure: [
            { 'studioVersion': normalize },
            { 'agpRange': normalize },
        ],
    });
}

async function main() {
    console.table(await fetchStudioAgpTable());
}

// Determine if this file is being run directly.
// zh-CN: 判断是否为直接执行该文件.
if (fileURLToPath(import.meta.url) === process.argv[1]) {
    main().catch(err => {
        console.error(err);
        process.exitCode = 1;
    });
}
