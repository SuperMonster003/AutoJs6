// scrape-and-update-readme-template-contributors-table.mjs

import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';
import { fetchStatistics } from './fetch-and-parse-autojs6-merged-pr-commits-statistics.mjs';
import { objectToLines } from './utils/format.mjs';
import { printLinesDiffs } from './utils/print.mjs';
import { toYYYYMMDD } from './utils/date.mjs';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.resolve(SCRIPT_DIR, '..');
const README_TEMPLATE_PATH = path.join(PROJECT_ROOT, '.readme', 'template_readme.md');
const COMMON_JSON_PATH = path.join(PROJECT_ROOT, '.readme', 'common.json');
const CONTRIBUTION_TABLE_REGEX = /(\{\{\s*p_contribution_table_thank_all_contributors\s*\}\}\.\r?\n\r?\n)(\|[^\r\n]*table_header_contribution_contributors[^\r\n]*\r?\n)(\|[\s|:\-]+\r?\n)((?:\|[^\r\n]*\r?\n)+)(\r?\n\{\{\s*p_contribution_table_data_updated_on\s*\}\}\.)/i;

const updateCommonJsonFile = () => {
    const commonRaw = fs.readFileSync(COMMON_JSON_PATH, 'utf8');
    const commonObj = JSON.parse(commonRaw);

    const updatedCommon = {
        ...commonObj,
        var_date_contribution_table_data_updated: toYYYYMMDD(),
    };

    const from = JSON.stringify(commonObj);
    const to = JSON.stringify(updatedCommon);
    if (from !== to) {
        fs.writeFileSync(COMMON_JSON_PATH, JSON.stringify(updatedCommon, null, 2), 'utf8');
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
    const filename = path.basename(README_TEMPLATE_PATH);

    const stats = await fetchStatistics();
    const newMarkdownRows = stats.map(stat => `| ${stat.contributorMarkdown} | ${stat.commitsCountMarkdown} | ${stat.latestCommitMarkdown} |`);
    let oldMarkdown = null;
    let matchedContributionTable = false;

    const raw = fs.readFileSync(README_TEMPLATE_PATH, { encoding: 'utf-8' });
    const updated = raw.replace(CONTRIBUTION_TABLE_REGEX, (_, leadingText, headerLine, separatorLine, markdown, trailingText) => {
        matchedContributionTable = true;
        oldMarkdown = markdown;
        const eol = headerLine.includes('\r\n') ? '\r\n' : '\n';
        return `${leadingText}${headerLine}${separatorLine}${newMarkdownRows.join(eol)}${eol}${trailingText}`;
    });
    if (!matchedContributionTable) {
        throw new Error(`Failed to locate contribution table in ${README_TEMPLATE_PATH}`);
    }

    const from = raw.replace(/\s+/g, '');
    const to = updated.replace(/\s+/g, '');
    if (from !== to) {
        fs.writeFileSync(README_TEMPLATE_PATH, updated, { encoding: 'utf-8' });
        console.log(`[${filename}] Updated (contribution statistics list)`);
        if (oldMarkdown) {
            printLinesDiffs(markdownToLines(oldMarkdown), markdownToLines(newMarkdownRows.join('\n')));
        }
        updateCommonJsonFile();
    } else {
        // console.log('[${filename}] No update needed (contribution statistics list)');
    }
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
