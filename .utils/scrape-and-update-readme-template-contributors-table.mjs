// scrape-and-update-readme-template-contributors-table.mjs

import * as fs from 'node:fs';
import * as path from 'node:path';
import { fetchStatistics } from './fetch-and-parse-autojs6-merged-pr-commits-statistics.mjs';
import { toYYYYMMDD } from './utils/date.mjs';

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
        console.log('[common.json] Updated (contribution statistics date)');
    }
}

(async function main() {
    const path = '../.readme/template_readme.md';

    const stats = await fetchStatistics();
    const newMarkdown = stats.map(stat => `| ${stat.contributorMarkdown} | ${stat.commitsCountMarkdown} | ${stat.latestCommitMarkdown} |`).join('\n');

    const text = fs.readFileSync(path, { encoding: 'utf-8' });
    const contributionHeaderRegex = /(table_header_contribution_\w+.+\r?\n)([\s|:\-]+\r?\n)(?:\|\s*<span style=".+(\r?\n))+/i;
    const newText = text.replace(contributionHeaderRegex, (_, headerLine, separatorLine, eol) => {
        return `${headerLine}${separatorLine}${newMarkdown}${eol}`;
    });

    if (text.replace(/\s+/g, '') !== newText.replace(/\s+/g, '')) {
        fs.writeFileSync(path, newText, { encoding: 'utf-8' });
        console.log('[template_readme.md] Updated (contribution statistics list)');
        updateCommonJsonFile();
    } else {
        // console.log('[template_readme.md] No update needed (contribution statistics list)');
    }
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
