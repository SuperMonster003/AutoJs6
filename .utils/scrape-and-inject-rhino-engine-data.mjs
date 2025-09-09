// scrape-and-inject-rhino-engine-data.mjs

import { getLatestCommitDate } from './utils/fetch.mjs';
import * as fs from 'node:fs';
import * as path from 'node:path';

const URL = 'https://raw.githubusercontent.com/SuperMonster003/Rhino-For-AutoJs6/refs/heads/master/gradle.properties';

/**
 * @param {string} latestVersion
 * @return {Promise<void>}
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
        console.log('[template_readme.md] 已更新 (Rhino 徽标版本)');
        console.log(`-- ${oldVersion}`);
        console.log(`-> ${latestVersion}`);
    } else {
        // console.log('[template_readme.md] 无需更新 (Rhino 徽标版本)');
    }
}

/**
 * @param {string} latestVersion
 * @param {number} linenoOfLatestVersion
 * @return {Promise<void>}
 */
async function updateCommonJsonWithRhinoData(latestVersion, linenoOfLatestVersion) {
    const commonJsonPath = path.resolve(process.cwd(), '../.readme/common.json');
    const commonRaw = fs.readFileSync(commonJsonPath, 'utf8');
    const commonObj = JSON.parse(commonRaw);

    const addressPrefix = 'http://rhino.autojs6.com/blob/master/gradle.properties';
    const addressSuffix = linenoOfLatestVersion > 0 ? `#L${linenoOfLatestVersion}` : '';
    const addressJsonValue = `[v${latestVersion}](${addressPrefix}${addressSuffix})`;
    const latestCommitValue = await getLatestCommitDate('SuperMonster003', 'Rhino-For-AutoJs6');

    const updatedCommon = {
        ...commonObj,
        latest_rhino_engine_name_with_github_lineno_address: addressJsonValue,
        var_date_rhino_engine_latest_committed: latestCommitValue,
    };

    if (JSON.stringify(updatedCommon) !== JSON.stringify(commonObj)) {
        fs.writeFileSync(commonJsonPath, JSON.stringify(updatedCommon, null, 2), 'utf8');
        console.log('[common.json] 已更新 (Rhino 数据)');
        [ 'latest_rhino_engine_name_with_github_lineno_address', 'var_date_rhino_engine_latest_committed' ].forEach(key => {
            if (key in updatedCommon && key in commonObj && updatedCommon[key] !== commonObj[key]) {
                console.log(`## ${key}`);
                console.log(`-- ${commonObj[key]}`);
                console.log(`-> ${updatedCommon[key]}`);
            }
        });
    } else {
        // console.log('[common.json] 无需更新 (Rhino 数据)');
    }
}

async function main() {
    const response = await fetch(URL);
    const text = await response.text();
    const lines = text.split('\n');
    const latestVersion = lines.find(line => line.startsWith('version=')).split('=')[1];
    const linenoOfLatestVersion = lines.findIndex(line => line.startsWith('version=')) + 1;

    await updateTemplateReadmeRhinoBadge(latestVersion);
    await updateCommonJsonWithRhinoData(latestVersion, linenoOfLatestVersion);
}

main().catch(err => {
    console.error(err);
    process.exitCode = 1;
});