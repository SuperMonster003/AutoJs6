// scrape-and-update-foojay-resolver-version.mjs
// noinspection CssInvalidHtmlTagReference

import * as cheerio from 'cheerio';
import * as fsp from 'node:fs/promises';
import * as path from 'node:path';
import fetch from 'node-fetch';
import { compareVersionStrings, isVersionStable } from './utils/versioning.mjs';

const PLUGIN_ID = 'org.gradle.toolchains.foojay-resolver-convention';
const ARTIFACT_ID = `foojay-resolver`;

/**
 * Convert plugin ID to marker artifact metadata URL on Plugin Portal.<br>
 * zh-CN: 将插件 ID 转为 Plugin Portal 上的 marker artifact 元数据地址.
 *
 * @param {string} pluginId
 * @returns {string}
 */
function buildMetadataUrl(pluginId) {
    const lastDot = pluginId.lastIndexOf('.');
    if (lastDot < 0) {
        throw new Error(`Invalid plugin ID: ${pluginId}`);
    }
    const groupId = pluginId.slice(0, lastDot); // e.g. org.gradle.toolchains
    const groupPath = groupId.replace(/\./g, '/'); // e.g. org/gradle/toolchains
    return `https://plugins.gradle.org/m2/${groupPath}/${ARTIFACT_ID}/maven-metadata.xml`;
}

/**
 * @param {string} pluginId
 * @returns {Promise<string>}
 */
async function fetchLatestVersion(pluginId) {
    const url = buildMetadataUrl(pluginId);
    const res = await fetch(url, { redirect: 'follow' });
    if (!res.ok) {
        throw new Error(`Failed to fetch: ${res.status} ${res.statusText} (${url})`);
    }
    const xml = await res.text();
    const $ = cheerio.load(xml, { xmlMode: true });

    return $('metadata > versioning > latest').first().text().trim()
        || $('metadata > versioning > release').first().text().trim()
        || ( /* @IIFE(getByVersionList) */ () => {
            const versions = $('metadata > versioning > versions > version')
                .map((_, el) => $(el).text().trim())
                .get()
                .filter(Boolean)
                .filter(isVersionStable)
                .sort(compareVersionStrings);
            if (versions.length === 0) {
                throw new Error('No version found in metadata');
            }
            return versions.at(-1);
        })();
}

/**
 * @param {string} newVersion
 * @returns {Promise<void>}
 */
async function updateVersionInGradleSettings(newVersion) {
    const pluginLabel = ARTIFACT_ID.split(/\W+/).map(s => s[0].toUpperCase() + s.slice(1)).join(' ');
    const updatedLabel = `${pluginLabel} plugin version`;
    const filePath = '../gradle/libs.versions.toml';
    const filename = path.basename(filePath);
    const raw = await fsp.readFile(filePath, 'utf8');

    // e.g. `foojay-resolver-convention = "0.9.0"`.
    const re = /(foojay.resolver.convention\s*=\s*)"(\d+(?:\.\d+)+)(?=")/;

    const matched = raw.match(re);
    if (!matched) {
        throw new Error(`Cannot determine the location of ${pluginLabel} plugin information`);
    }
    const from = matched[2]; // oldVersion
    const to = newVersion;
    if (from !== to) {
        const updated = raw.replace(re, `$1${to}`);
        await fsp.writeFile(filePath, updated, 'utf8');
        const maxLength = Math.max(...[ from, to ].map(s => s.length + 5));
        const SEP_EQ = '='.repeat(maxLength);
        console.log(`[${filename}] Updated (${updatedLabel})`);
        console.log(SEP_EQ);
        console.log(`-- ${matched[1]}"${from}"`);
        console.log(`-> ${matched[1]}"${to}"`);
        console.log(SEP_EQ);
    } else {
        // console.log(`[${filename}] No update needed (${updatedLabel})`);
    }
}

(async function main() {
    try {
        await updateVersionInGradleSettings(await fetchLatestVersion(PLUGIN_ID));
    } catch (e) {
        console.error(`Failed to get latest version of Foojay Resolver: ${e.message}`);
        process.exit(1);
    }
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
