// scrape-and-inject-android-studio-codename-maps.mjs

import * as fsp from 'node:fs/promises';
import * as path from 'node:path';
import { compareVersionStrings, compareVersionStringsDescending } from './utils/versioning.mjs';
import { extractLatestStableRelease, getAndroidStudioReleases, refineDownloadItemsWithRealSize } from './fetch-and-parse-android-studio-archives.mjs';
import { toUpdatedStamp, toYYYYMMDD } from './utils/date.mjs';
import { readPropertiesSync } from './utils/properties.mjs';
import { updateGradleLinesData, updateGradleMapData } from './utils/update-helper.mjs';

/**
 * Manual override for codename mappings (for resolving codename prefix conflicts).<br>
 * Default is first letter, e.g. 'Meerkat' and 'Bumblebee' gives { Meerkat: 'M', Bumblebee: 'B' }.<br>
 * When first letter conflict, conflicts are auto-resolved,
 * e.g. 'Camel' and 'Catfish' gives { Camel: 'CAM', Catfish: 'CAT' }.<br>
 * For extreme cases where conflicts cannot be auto-resolved,
 * e.g. 'Cat' and 'Catfish', manual prefix mapping is needed, like { Cat: 'CT', Catfish: 'CTF' }.<br>
 * zh-CN:<br>
 * 手动覆盖代号映射 (可用于解决代号前缀冲突).<br>
 * 默认为首字母, 如 'Meerkat' 与 'Bumblebee', 得到 { Meerkat: 'M', Bumblebee: 'B' }.<br>
 * 首字母重复时自动消解冲突, 如 'Camel' 与 'Catfish', 得到 { Camel: 'CAM', Catfish: 'CAT' }.<br>
 * 极端情况无法自动消解冲突, 如 'Cat' 与 'Catfish', 此时需要手动指定前缀, 如 { Cat: 'CT', Catfish: 'CTF' }.
 * @example Object<codename name, codename prefix>
 * {
 *   'Camel': 'CM',
 *   'Cat': 'CT',
 *   'Catfish': 'CTF',
 *   ... ...
 * }
 * @type {Object<string, string>}
 */
const manualCodenameOverrides = {};

const MIN_ANDROID_STUDIO_VERSION = (/* @IIFE */ () => {
    const props = readPropertiesSync('../version.properties');
    return props.get('MIN_SUPPORTED_ANDROID_STUDIO_IDE_VERSION');
})();

/**
 * Update common.json with the lastest stable release.<br>
 * zh-CN: 用最新稳定版更新 common.json.
 *
 * @param {AndroidStudioReleaseItem[]} releases
 * @returns {Promise<void>}
 */
async function updateCommonDataByLatestRelease(releases) {
    const latest = extractLatestStableRelease(releases);
    const latestDownload = await refineDownloadItemsWithRealSize(latest.download, /windows|linux/i);

    /**
     * @param {RegExp} suffix
     * @returns {{ name: string, link: string, size: string }}
     */
    const pickWinItem = (suffix) => {
        const aim = latestDownload.find(l => suffix.test(l.link));
        if (!aim) throw new Error(`Matched archive entry missing Windows EXE/ZIP or Linux TAR download information (suffix: ${suffix})`);
        return { name: aim.link.split('/').pop(), link: aim.link, size: aim.size };
    };
    const exeItem = pickWinItem(/\.exe$/);
    const zipItem = pickWinItem(/\.zip$/);
    const tarItem = pickWinItem(/\.tar(\.gz)?$/);

    // Prepare fields needed to write back to common.json.
    // zh-CN: 准备写回 common.json 所需字段.

    const latestVersionName = latest.name; // e.g. "Android Studio Narwhal Feature Drop | 2025.1.2"
    const latestVersionDate = toYYYYMMDD(latest.date) || ''; // e.g. "2025/07/31"

    // Read and update common.json (only update keys containing "android_studio_latest_" fragment and version name date key).
    // zh-CN: 读取并更新 common.json (仅更新带有 "android_studio_latest_" 片段的键与版本名日期键).

    const commonJsonPath = path.resolve(process.cwd(), '../.readme/common.json');
    const commonRaw = await fsp.readFile(commonJsonPath, 'utf8');
    const commonObj = JSON.parse(commonRaw);

    const updatedCommon = {
        ...commonObj,
        android_studio_latest_recommended_version_name: latestVersionName,
        var_date_android_studio_latest_recommended_version_name: latestVersionDate,
        android_studio_latest_recommended_file_name_of_exe: exeItem.name,
        android_studio_latest_recommended_download_address_of_exe: exeItem.link,
        android_studio_latest_recommended_file_size_of_exe: exeItem.size,
        android_studio_latest_recommended_file_name_of_zip: zipItem.name,
        android_studio_latest_recommended_download_address_of_zip: zipItem.link,
        android_studio_latest_recommended_file_size_of_zip: zipItem.size,
        android_studio_latest_recommended_file_name_of_tar: tarItem.name,
        android_studio_latest_recommended_download_address_of_tar: tarItem.link,
        android_studio_latest_recommended_file_size_of_tar: tarItem.size,
    };

    if (JSON.stringify(updatedCommon) !== JSON.stringify(commonObj)) {
        await fsp.writeFile(commonJsonPath, JSON.stringify(updatedCommon, null, 2), 'utf8');
        console.log('[common.json] Updated (Android Studio information)');
        const from = commonObj.android_studio_latest_recommended_version_name;
        const to = updatedCommon.android_studio_latest_recommended_version_name;
        if (from !== to) {
            const maxLength = Math.max(...[ from, to ].map(s => s.length + 5));
            const SEP_EQ = '='.repeat(maxLength);
            console.log(SEP_EQ);
            console.log(`-- "${from}"`);
            console.log(`-> "${to}"`);
            console.log(SEP_EQ);
        }
    } else {
        // console.log('[common.json] No update needed (Android Studio information)');
    }
}

/**
 * Summarize codename-to-version mappings and codename first release dates.<br>
 * zh-CN: 汇总代号版本映射以及代号的首发日期.
 *
 * @param {AndroidStudioReleaseItem[]} releases
 */
function getCodenameMapLinesInfo(releases) {

    /**
     * Parse "codename" from name,
     * zh-CN: 从名称中解析 "代号",
     *
     * @example string
     * 'Android Studio Meerkat Feature Drop | 2024.3.2 RC 1' -> 'Meerkat'
     *
     * @param {string} name
     * @returns {string | null}
     */
    const codenameFromName = (name) => {
        // Capture "Android Studio <Codename> [Feature Drop]".
        // zh-CN: 捕获 "Android Studio <Codename> [Feature Drop]".
        const m = /Android\s+Studio\s+(.+?)(\s+\d+)?(\s+Feature Drop)?(?=\s*\|)/i.exec(name);
        return m?.[1]?.trim();
    };

    /**
     * Generate unique codes: sequentially, uniformly increase length for conflict groups;
     * override mappings take precedence.<br>
     * zh-CN:<br>
     * 生成唯一代码: 按顺序, 出现冲突则对冲突组统一递增长度; 覆盖映射优先考虑.
     *
     * @example Map<name, prefixCode>
     * Map(14) {
     *   'Arctic Fox' => 'A',
     *   'Bumblebee' => 'B',
     *   ... ...
     *   'Narwhal' => 'N'
     * }
     *
     * @param {string[]} names
     * Original codenames (preserves case and spaces, e.g. "Arctic Fox", "Bumblebee", "Narwhal" etc).<br>
     * zh-CN: 原始代号 (保留大小写与空格, 如 "Arctic Fox", "Bumblebee", "Narwhal" 等).
     * @param {Object<string, string>} overrides
     * Manual override mappings.<br>
     * zh-CN: 手动覆盖映射.
     * @returns {Map<string, string>}
     */
    const buildUniquePrefixes = (names, overrides) => {

        /**
         * @param {string} s
         * @returns {string}
         */
        const normalize = (s) => s.replace(/\s+/g, '');

        /**
         * @param {string} s
         * @param {string} def
         * @returns {string}
         */
        const normalizeOrDefault = (s, def) => s && normalize(s) || normalize(def);

        /**
         * @param {string }s
         * @param {number} def
         * @returns {number}
         */
        const normalizeLength = (s, def) => s && normalize(s).length || def;

        const entries = names.map(name => {
            const override = overrides[name]?.toUpperCase();
            const base = normalize(name);
            const len = normalizeLength(override, 1);
            const code = normalizeOrDefault(override, base.slice(0, len)).toUpperCase();
            const locked = Boolean(override);
            return { name, base, len, code, locked };
        });

        // Detect and resolve conflicts. (zh-CN: 检测并解决冲突.)
        const maxLenByName = new Map(entries.map(e => [ e.name, e.base.length ]));
        // Loop limit protection to prevent infinite loops in extreme cases.
        // zh-CN: 循环上限保护, 防止极端情况下死循环.
        for (let step = 0; step < 1 << 10; step++) {
            /**
             * Count conflict groups. (zh-CN: 统计冲突组.)
             * @example Map<code, indices>
             * Map(14) {
             *   'N' => [ 0 ],
             *   'M' => [ 1 ],
             *   ... ...
             *   'B' => [ 12 ],
             *   'A' => [ 13 ]
             * }
             * @type {Map<string, number[]>}
             */
            const bucket = new Map();
            entries.forEach((e, idx) => {
                const key = e.code;
                if (!bucket.has(key)) bucket.set(key, []);
                bucket.get(key).push(idx);
            });

            const conflicts = Array.from(bucket.entries()).filter(([ _, indices ]) => indices.length >= 2);
            if (conflicts.length === 0) {
                // No conflicts, or all conflicts have been resolved.
                // zh-CN: 没有冲突, 或冲突已全部解决.
                break;
            }

            for (const [ code, indices ] of conflicts) {
                const lockedIndices = indices.filter(i => entries[i].locked);
                if (lockedIndices.length >= 2) {
                    const groupNames = indices.map(i => entries[i].name);
                    throw new Error(`[CodenameMap] Manual override mapping conflict: "${code}" -> [ ${groupNames.join(', ')} ]`);
                }
                for (const i of indices) {
                    const e = entries[i];
                    if (e.locked) continue;
                    const maxLen = maxLenByName.get(e.name);
                    if (e.len >= maxLen) {
                        const groupNames = indices.map(i => entries[i].name);
                        throw new Error(`[CodenameMap] Unable to auto-resolve conflicts: [ ${groupNames.join(', ')} ]`);
                    }
                    e.len += 1;
                    e.code = e.base.slice(0, e.len);
                }
            }
        }

        return new Map(entries.map(e => [ e.name, e.code ]));
    };

    const codenames = [ ...new Set(releases.map(o => codenameFromName(o.name)).filter(Boolean)) ];
    const nameToCode = buildUniquePrefixes(codenames, manualCodenameOverrides);

    /**
     * @example Map<version, prefixCode>
     * Map(22) {
     *   ... ...
     *   '2025.1.4.7' => 'N',
     *   '2025.1.4.6' => 'N',
     *   '2025.1.4.5' => 'N',
     *   '2025.1.4.4' => 'N',
     *   '2025.1.3.7' => 'N',
     *   '2025.1.4.3' => 'N',
     *   '2025.1.3.6' => 'N',
     *   '2025.1.2.13' => 'N',
     *   '2025.1.4.2' => 'N',
     *   '2025.1.2.12' => 'N',
     *   '2025.1.4.1' => 'N',
     *   '2025.1.3.5' => 'N',
     *   '2025.1.3.4' => 'N',
     *   ... ...
     * }
     * @type {Map<string, string>}
     */
    const versionToLetter = new Map();

    /**
     * @example Map<prefixCode, { name, born }>
     * Map(14) {
     *   'N' => { name: 'Narwhal', born: 2025-03-18T16:00:00.000Z },
     *   'M' => { name: 'Meerkat', born: 2024-11-11T16:00:00.000Z },
     *   ... ...
     *   'B' => { name: 'Bumblebee', born: 2021-05-17T16:00:00.000Z },
     *   'A' => { name: 'Arctic Fox', born: 2021-01-25T16:00:00.000Z }
     * }
     * @type {Map<string, { name: string, born: Date } >}
     */
    const letterBorn = new Map();

    const buildVersionMap = new Map();

    for (const release of releases) {
        if (!release.version) continue;

        if (compareVersionStrings(release.version, MIN_ANDROID_STUDIO_VERSION) >= 0) {
            buildVersionMap.set(release.build.replace(/[^\d.]*/g, ''), release.version);
        }

        const cname = codenameFromName(release.name);
        if (!cname) continue;

        const code = nameToCode.get(cname);
        if (!code) continue;

        if (versionToLetter.has(release.version)) {
            if (versionToLetter.get(release.version) !== code) {
                throw new Error(`[CodenameMap] Duplicate version: ${release.version}`);
            }
        } else {
            versionToLetter.set(release.version, code);
        }

        const d = new Date(release.date);
        const existed = letterBorn.get(code);
        if (!existed || d < existed.born) {
            letterBorn.set(code, { name: cname, born: d });
        }
    }

    const sortedVersions = Array.from(versionToLetter.keys()).sort(compareVersionStringsDescending);

    /**
     * @example Array<[version, prefixCode]>
     * [
     *   ... ..
     *   [ '2025.1.4.7', 'N' ],
     *   [ '2025.1.4.6', 'N' ],
     *   [ '2025.1.4.5', 'N' ],
     *   [ '2025.1.4.4', 'N' ],
     *   [ '2025.1.4.3', 'N' ],
     *   [ '2025.1.4.2', 'N' ],
     *   [ '2025.1.4.1', 'N' ],
     *   [ '2025.1.3.7', 'N' ],
     *   [ '2025.1.3.6', 'N' ],
     *   [ '2025.1.3.5', 'N' ],
     *   [ '2025.1.3.4', 'N' ],
     *   [ '2025.1.3.3', 'N' ],
     *   [ '2025.1.3.2', 'N' ],
     *   [ '2025.1.3.1', 'N' ],
     *   ... ...
     * ]
     * @type {Array<[string, string]>}
     */
    const versionLetterList = sortedVersions.map(v => [ v, versionToLetter.get(v) ]);

    /**
     * @example { majorSeries: { revision: letter } }
     * {
     *   ... ...
     *   '2025.1.4': { '1': 'N', '2': 'N', '3': 'N', ..., '6': 'N', '7': 'N' },
     *   '2025.1.3': { '1': 'N', '2': 'N', '3': 'N', ..., '6': 'N', '7': 'N' },
     *   '2025.1.2': { '1': 'N', '2': 'N', '3': 'N', ..., '6': 'N', '7': 'N', ..., '13': 'N' },
     *   '2025.1.1': { '1': 'N', '2': 'N', '3': 'N', ..., '6': 'N', '7': 'N', ..., '14': 'N' },
     *   '2024.3.2': { '1': 'M', '2': 'M', '3': 'M', ..., '6': 'M', '7': 'M', ..., '15': 'M' },
     *   '2024.3.1': { '1': 'M', '2': 'M', '3': 'M', ..., '6': 'M', '7': 'M', ..., '15': 'M' },
     *   '2024.2.2': { '1': 'L', '2': 'L', '3': 'L', ..., '6': 'L', '7': 'L', ..., '15': 'L' },
     *   '2024.2.1': { '1': 'L', '2': 'L', '3': 'L', ..., '6': 'L', '7': 'L', ..., '12': 'L' },
     *   '2024.1.3': { '1': 'L', '2': 'L', '3': 'L' },
     *   '2024.1.2': { '1': 'K', '2': 'K', '3': 'K', ..., '6': 'K', '7': 'K', ..., '13': 'K' },
     *   '2024.1.1': { '1': 'K', '2': 'K', '3': 'K', ..., '6': 'K', '7': 'K', ..., '13': 'K' },
     *   '2023.3.2': { '1': 'J', '2': 'K' },
     *   '2023.3.1': { '1': 'J', '2': 'J', '3': 'J', ..., '6': 'J', '7': 'J', ..., '20': 'J' },
     *   '2023.2.1': { '1': 'I', '2': 'I', '3': 'I', ..., '6': 'I', '7': 'I', ..., '25': 'I' },
     *   '2023.1.1': { '1': 'H', '2': 'H', '3': 'H', ..., '6': 'H', '7': 'H', ..., '28': 'H' },
     *   '2022.3.1': { '1': 'G', '2': 'G', '3': 'G', ..., '6': 'G', '7': 'G', ..., '22': 'G' },
     *   ... ...
     * }
     * @type {Object<string, { [revision: number]: string }>}
     */
    const rawFirstlyVersionLetterMap = {};
    for (let i = 0; i < versionLetterList.length; i++) {
        const [ v, letter ] = versionLetterList[i];
        const matched = v.match(/(^\d+\.\d+\.\d+)\.(\d+)/);
        if (!matched) continue;
        const [ , majorSeries, revision ] = matched;
        if (majorSeries in rawFirstlyVersionLetterMap) {
            rawFirstlyVersionLetterMap[majorSeries][revision] = letter;
        } else {
            rawFirstlyVersionLetterMap[majorSeries] = { [revision]: letter };
        }
    }

    /**
     * When all versions with the same major series prefix (like `2025.1.x.x`)
     * point to the same codename prefix (like `'N'`),
     * they can be merged (like `{ '2025.1.4' : 'N' }`, where `2025.1.4` is the major series prefix),
     * otherwise retain the original split form (like `2023.3.2.x` cannot be merged).<br>
     * zh-CN:<br>
     * 当主版本系列相同的版本 (如 `2025.1.x.x`) 全部指向同一个代号前缀 (如 `'N'`) 时,
     * 可进行合并 (如 `{ '2025.1.4' : 'N' }`, 其中 `2025.1.4` 为主版本系列),
     * 否则保留原始的拆分形式 (如 `2023.3.2.x` 不可合并).
     * @example { version: letter }
     * {
     *   ... ...
     *   '2025.1.4': 'N',
     *   '2025.1.3': 'N',
     *   '2025.1.2': 'N',
     *   '2025.1.1': 'N',
     *   '2024.3.2': 'M',
     *   '2024.3.1': 'M',
     *   '2024.2.2': 'L',
     *   '2024.2.1': 'L',
     *   '2024.1.3': 'L',
     *   '2024.1.2': 'K',
     *   '2024.1.1': 'K',
     *   '2023.3.2.1': 'J',
     *   '2023.3.2.2': 'K',
     *   '2023.3.1': 'J',
     *   '2023.2.1': 'I',
     *   '2023.1.1': 'H',
     *   '2022.3.1': 'G',
     *   ... ...
     * }
     * @type {Object<[version: string], string>}
     */
    const combinedFirstlyVersionLetterMap = {};

    Object.entries(rawFirstlyVersionLetterMap).forEach(([ majorSeries, revisionToLetter ]) => {
        const letterValues = Object.values(revisionToLetter);
        if (new Set(letterValues).size === 1) {
            /* Combine. (zh-CN: 合并.) */
            combinedFirstlyVersionLetterMap[majorSeries] = letterValues[0];
        } else {
            /* Keep expanded. (zh-CN: 保持展开.) */
            Object.entries(revisionToLetter).forEach(([ revision, letter ]) => {
                combinedFirstlyVersionLetterMap[`${majorSeries}.${revision}`] = letter;
            });
        }
    });

    /**
     * @example { majorSeries: { patch: letter } }
     * {
     *   ... ...
     *   '2025.1': { '1': 'N', '2': 'N', '3': 'N', '4': 'N' },
     *   '2024.3': { '1': 'M', '2': 'M' },
     *   '2024.2': { '1': 'L', '2': 'L' },
     *   '2024.1': { '1': 'K', '2': 'K', '3': 'L' },
     *   '2023.2': { '1': 'I' },
     *   '2023.1': { '1': 'H' },
     *   '2022.3': { '1': 'G' },
     *   ... ...
     * }
     * @type {Object<string, { [patch: number]: string }>}
     */
    const rawVersionLetterMap = {};
    /**
     * @example
     * Set(1) { '2023.3' }
     * @type {Set<string>}
     */
    const excludedVersionSet = new Set();
    /**
     * @example
     * {
     *   '2023.3.2.1': 'J',
     *   '2023.3.2.2': 'K',
     *   '2023.3.1': 'J',
     * }
     * @type {Object<string, string>}
     */
    const excludedVersionLetterMap = {};
    const entries = Object.entries(combinedFirstlyVersionLetterMap).filter(([ v, letter ]) => {
        if (v.split('.').length > 3) {
            excludedVersionLetterMap[v] = letter;
            excludedVersionSet.add(v.split('.').slice(0, 2).join('.'));
            return false;
        }
        return true;
    });
    for (let i = 0; i < entries.length; i++) {
        const [ v, letter ] = entries[i];
        if (excludedVersionSet.has(v.split('.').slice(0, 2).join('.'))) {
            excludedVersionLetterMap[v] = letter;
            continue;
        }
        const matched = v.match(/(^\d+\.\d+)\.(\d+)/);
        if (!matched) continue;
        const [ , majorSeries, patch ] = matched;
        if (majorSeries in rawVersionLetterMap) {
            rawVersionLetterMap[majorSeries][patch] = letter;
        } else {
            rawVersionLetterMap[majorSeries] = { [patch]: letter };
        }
    }

    /**
     * When all versions with the same major series prefix (like `2025.1.x`)
     * point to the same codename prefix (like `'N'`),
     * they can be merged (like `{ '2025.1' : 'N' }`, where `2025.1` is the major series prefix),
     * otherwise retain the original split form (like `2024.1` cannot be merged).<br>
     * zh-CN:<br>
     * 当主版本系列相同的版本 (如 `2025.1.x`) 全部指向同一个代号前缀 (如 `'N'`) 时,
     * 可进行合并 (如 `{ '2025.1' : 'N' }`, 其中 `2025.1` 为主版本系列),
     * 否则保留原始的拆分形式 (如 `2024.1` 不可合并).
     * @example { version: letter }
     * {
     *   ... ...
     *   '2025.1.4': 'N',
     *   '2025.1.3': 'N',
     *   '2025.1.2': 'N',
     *   '2025.1.1': 'N',
     *   '2024.3.2': 'M',
     *   '2024.3.1': 'M',
     *   '2024.2.2': 'L',
     *   '2024.2.1': 'L',
     *   '2024.1.3': 'L',
     *   '2024.1.2': 'K',
     *   '2024.1.1': 'K',
     *   '2023.3.2.1': 'J',
     *   '2023.3.2.2': 'K',
     *   '2023.3.1': 'J',
     *   '2023.2.1': 'I',
     *   '2023.1.1': 'H',
     *   '2022.3.1': 'G',
     *   ... ...
     * }
     * @type {Object<[version: string], string>}
     */
    const combinedVersionLetterMap = structuredClone(excludedVersionLetterMap);

    Object.entries(rawVersionLetterMap).forEach(([ majorSeries, patchToLetter ]) => {
        const letterValues = Object.values(patchToLetter);
        if (new Set(letterValues).size === 1) {
            /* Combine. (zh-CN: 合并.) */
            combinedVersionLetterMap[majorSeries] = letterValues[0];
        } else {
            /* Keep expanded. (zh-CN: 保持展开.) */
            Object.entries(patchToLetter).forEach(([ patch, letter ]) => {
                combinedVersionLetterMap[`${majorSeries}.${patch}`] = letter;
            });
        }
    });

    const codenameVersionMap = new Map(Object.entries(combinedVersionLetterMap));

    const codenameLines = Array.from(letterBorn.entries())
        .sort((a, b) => {
            return new Date(b[1].born).getTime() - new Date(a[1].born).getTime();
        })
        .map(([ code, { name, born } ]) => {
            const bornStr = toUpdatedStamp(born);
            // e.g. [ "#Born on Nov 12, 2024", "M=Meerkat" ]
            return [ `#Born on ${bornStr}`, `${code}=${name}` ];
        })
        .flat(1);

    return { buildVersionMap, codenameVersionMap, codenameLines };
}

(async function main() {
    const releases = await getAndroidStudioReleases();
    await updateCommonDataByLatestRelease(releases);

    const { buildVersionMap, codenameVersionMap, codenameLines } = getCodenameMapLinesInfo(releases);

    await updateGradleMapData('android-studio-build-version', buildVersionMap, {
        label: 'Android Studio build version map',
        sort: 'value.descending.as.version',
    });

    await updateGradleMapData('android-studio-codename-version', codenameVersionMap, {
        label: 'Android Studio codename version map',
        sort: 'key.descending.as.version',
    });

    await updateGradleLinesData('android-studio-codename', codenameLines, {
        label: 'Android Studio codename map',
    });
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
