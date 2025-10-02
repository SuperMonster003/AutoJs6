// scrape-and-update-readme-template-contributors-table.mjs

import * as fs from 'node:fs';
import * as path from 'node:path';
import { fetchStatistics } from './fetch-and-parse-autojs6-merged-pr-commits-statistics.mjs';
import { objectToLines } from './utils/format.mjs';
import { printLinesDiffs } from './utils/print.mjs';
import { toYYYYMMDD } from './utils/date.mjs';

const updateCommonJsonFile = () => {
    const commonJsonPath = path.resolve(process.cwd(), '../.readme/common.json');
    const commonRaw = fs.readFileSync(commonJsonPath, 'utf8');
    const commonObj = JSON.parse(commonRaw);

    const updatedCommon = {
        ...commonObj,
        var_date_contribution_table_data_updated: toYYYYMMDD(),
    };

    const from = JSON.stringify(commonObj);
    const to = JSON.stringify(updatedCommon);
    if (from !== to) {
        fs.writeFileSync(commonJsonPath, JSON.stringify(updatedCommon, null, 2), 'utf8');
        console.log('[common.json] Updated (contribution statistics date)');
        printLinesDiffs(objectToLines(commonObj), objectToLines(updatedCommon), { regexForKeyMatching: /"\w+"(?=:)/ });
    }
};

/**
 * @param {string} md
 * @returns {string}
 */
const markdownToLines = (md) => {
    const rawLines = md
        .replaceAll('&#x2011;', '-')
        .replace(/<span.+?>(.+?)<\/span>/g, '$1')
        .replace(/\[(.+?)]\(http.+?\)(?:\s+`\((.+?)\)`)?/g, '$1|$2')
        .replaceAll('`', '')
        .split('\n')
        .map(line => line.split('|').map(s => s.trim()).filter(Boolean).join('\uffef'))
        .filter(Boolean)
        .join('\n');
    const lines = [];
    rawLines.split('\n').forEach((line) => {
        const data = line.split('\uffef');
        if (!data.length) {
            return;
        }
        if (data.length === 3) {
            data.splice(1, 0, 'null');
        }
        if (data.length !== 4) {
            throw new Error(`Invalid line: [ ${data.join(', ')} ]`);
        }
        const [ name, nickname, commitsCount, latestCommit ] = data;
        lines.push(`${name} { nickname: ${nickname ? `"${nickname}"` : 'null'}, commitsCount: ${commitsCount}, latestCommit: "${latestCommit}" }`);
    });
    return lines.join('\n');
};

(async function main() {
    const filePath = '../.readme/template_readme.md';
    const filename = path.basename(filePath);

    const stats = await fetchStatistics();
    const newMarkdown = stats.map(stat => `| ${stat.contributorMarkdown} | ${stat.commitsCountMarkdown} | ${stat.latestCommitMarkdown} |`).join('\n');
    let oldMarkdown = null;

    const raw = fs.readFileSync(filePath, { encoding: 'utf-8' });
    const contributionSectionRegex = /(table_header_contribution_\w+.+\r?\n)([\s|:\-]+\r?\n)((?:\|\s*<span style=".+(\r?\n)+)+)/i;
    const updated = raw.replace(contributionSectionRegex, (_, headerLine, separatorLine, markdown, eol) => {
        oldMarkdown = markdown
        return `${headerLine}${separatorLine}${newMarkdown}${eol}`;
    });

    const from = raw.replace(/\s+/g, '');
    const to = updated.replace(/\s+/g, '');
    if (from !== to) {
        fs.writeFileSync(filePath, updated, { encoding: 'utf-8' });
        console.log(`[${filename}] Updated (contribution statistics list)`);
        if (oldMarkdown) {
            printLinesDiffs(markdownToLines(oldMarkdown), markdownToLines(newMarkdown));
        }
        updateCommonJsonFile();
    } else {
        // console.log('[${filename}] No update needed (contribution statistics list)');
    }
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
