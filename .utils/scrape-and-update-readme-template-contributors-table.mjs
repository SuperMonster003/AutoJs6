// scrape-and-update-readme-template-contributors-table.mjs

import { fetchStatistics } from './fetch-and-parse-autojs6-merged-pr-commits-statistics.mjs';
import * as fs from 'node:fs';
import { toYYYYMMDD } from './utils/date.mjs';
import * as path from 'node:path';

function updateCommonJsonFile() {
    const commonJsonPath = path.resolve(process.cwd(), '../.readme/common.json');
    const commonRaw = fs.readFileSync(commonJsonPath, 'utf8');
    const commonObj = JSON.parse(commonRaw);

    const updatedCommon = {
        ...commonObj,
        var_date_contribution_table_data_updated: toYYYYMMDD(),
    };

    if (JSON.stringify(updatedCommon) !== JSON.stringify(commonObj)) {
        fs.writeFileSync(commonJsonPath, JSON.stringify(updatedCommon, null, 2), 'utf8');
        console.log('[common.json] 已更新 (贡献参与数据统计日期)');
    }
}

(async function main() {
    const path = '../.readme/template_readme.md';

    const stats = await fetchStatistics();
    const newMarkdown = stats.map(stat => `| ${stat.contributorMarkdown} | ${stat.commitsCountMarkdown} | ${stat.latestCommitMarkdown} |`).join('\n');

    const text = fs.readFileSync(path, { encoding: 'utf-8' });
    const newText = text.replace(/((?:table_header_contribution_contributors|table_header_contribution_number_of_commits|table_header_contribution_recent_submissions).+\r?\n)([\s|:\-]+\r?\n)(\|\s*<span style=".+\r?\n)+/i, ($0, $1, $2, $3) => {
        const cr = $3.match(/\|\s*<span style=".+(\r?\n)/)[1];
        return `${$1}${$2}${newMarkdown}${cr}`;
    });

    if (text.replace(/\s+/g, '') !== newText.replace(/\s+/g, '')) {
        fs.writeFileSync(path, newText, { encoding: 'utf-8' });
        console.log('[template_readme.md] 已更新 (贡献参与统计列表)');
        updateCommonJsonFile();
    } else {
        // console.log('[template_readme.md] 无需更新 (贡献参与统计列表)');
    }
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});