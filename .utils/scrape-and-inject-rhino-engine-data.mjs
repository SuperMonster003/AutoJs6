// scrape-and-inject-rhino-engine-data.mjs

import * as fs from 'node:fs';
import * as path from 'node:path';
import { getLatestCommitDate } from './utils/fetch.mjs';

const URL = 'https://raw.githubusercontent.com/SuperMonster003/Rhino-For-AutoJs6/refs/heads/master/gradle.properties';

/**
 * @param {string} latestVersion
 * @returns {Promise<void>}
 */
async function updateTemplateReadmeRhinoBadge(latestVersion) {
    const templateReadmePath = path.resolve(process.cwd(), '../.readme/template_readme.md');
    const fileContent = fs.readFileSync(templateReadmePath, 'utf8');
    const rhinoBadgeRegex = /(href=.*?https:\/\/github\.com\/mozilla\/rhino.+?img\.shields\.io\/badge\/Rhino-)(.+)(-[a-f\d]{6}\b)/;
    const matched = fileContent.match(rhinoBadgeRegex);
    const oldVersion = matched[2].replaceAll('--', '-');
    if (oldVersion !== latestVersion) {
        const updatedFileContent = fileContent.replace(rhinoBadgeRegex, `$1${latestVersion.replaceAll('-', '--')}$3`);
        fs.writeFileSync(templateReadmePath, updatedFileContent, 'utf8');
        const from = oldVersion;
        const to = latestVersion;
        const maxLength = Math.max(...[ from, to ].map(s => s.length + 5));
        const SEP_EQ = '='.repeat(maxLength);
        console.log('[template_readme.md] Updated (Rhino badge version)');
        console.log(SEP_EQ);
        console.log(`-- "${from}"`);
        console.log(`-> "${to}"`);
        console.log(SEP_EQ);
    } else {
        // console.log('[template_readme.md] No update needed (Rhino badge version)');
    }
}

/**
 * @param {string} latestVersion
 * @param {number} linenoOfLatestVersion
 * @returns {Promise<void>}
 */
async function updateCommonJsonWithRhinoData(latestVersion, linenoOfLatestVersion) {
    const commonJsonPath = path.resolve(process.cwd(), '../.readme/common.json');
    const commonRaw = fs.readFileSync(commonJsonPath, 'utf8');
    const commonObj = JSON.parse(commonRaw);

    const addressPrefix = 'http://rhino.autojs6.com/blob/master/gradle.properties';
    const addressSuffix = linenoOfLatestVersion > 0 ? `#L${linenoOfLatestVersion}` : '';
    const addressJsonValue = `[v${latestVersion}](${addressPrefix}${addressSuffix})`;
    const latestCommitValue = await getLatestCommitDate('SuperMonster003', 'Rhino-For-AutoJs6');

    const toUpdateKeys = {
        address: 'latest_rhino_engine_name_with_github_lineno_address',
        date: 'var_date_rhino_engine_latest_committed',
    };
    const updatedCommon = {
        ...commonObj,
        [toUpdateKeys.address]: addressJsonValue,
        [toUpdateKeys.date]: latestCommitValue,
    };

    if (JSON.stringify(updatedCommon) !== JSON.stringify(commonObj)) {
        fs.writeFileSync(commonJsonPath, JSON.stringify(updatedCommon, null, 2), 'utf8');
        const toPrint = Object.values(toUpdateKeys).map((key) => {
            if (key in updatedCommon && key in commonObj && updatedCommon[key] !== commonObj[key]) {
                return [
                    `## ${key}`,
                    `-- "${commonObj[key]}"`,
                    `-> "${updatedCommon[key]}"`,
                ].join('\n');
            }
            return null;
        }).filter(Boolean);
        const maxLength = Math.max(...toPrint.join('\n').split('\n').map(s => s.length));
        const SEP_EQ = '='.repeat(maxLength);
        const SEP_DASH = '-'.repeat(maxLength);
        console.log('[common.json] Updated (Rhino information)');
        console.log(SEP_EQ);
        toPrint.forEach((s, i) => {
            console.log(s);
            if (i !== toPrint.length - 1) {
                console.log(SEP_DASH);
            }
        });
        console.log(SEP_EQ);
    } else {
        // console.log('[common.json] No update needed (Rhino information)');
    }
}

(async function main() {
    const response = await fetch(URL);
    const text = await response.text();
    const lines = text.split('\n');
    const latestVersion = lines.find(line => line.startsWith('version=')).split('=')[1];
    const linenoOfLatestVersion = lines.findIndex(line => line.startsWith('version=')) + 1;

    await updateTemplateReadmeRhinoBadge(latestVersion);
    await updateCommonJsonWithRhinoData(latestVersion, linenoOfLatestVersion);
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
