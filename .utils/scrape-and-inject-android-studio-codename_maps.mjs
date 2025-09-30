// scrape-and-inject-android-studio-codename_maps.mjs

import * as fsp from 'node:fs/promises';
import * as path from 'node:path';
import { batchUpdateAnchoredBlocks } from './utils/anchors.mjs';
import { bytes2GiB } from './utils/format.mjs';
import { compareVersionStrings } from './utils/versioning.mjs';
import { getAndroidStudioArchives } from './fetch-and-parse-android-studio-archives.mjs';
import { getLatestStableArchives } from './fetch-and-parse-android-studio-latest-stable-version.mjs';
import { getRemoteFileSizeBytes } from './utils/fetch.mjs';
import { toUpdatedStamp, toYYYYMMDD } from './utils/date.mjs';

/**
 * Manual override for codename mappings (for resolving codename prefix conflicts).<br>
 * Default is first letter, e.g. 'Meerkat' and 'Bumblebee' gives { Meerkat: 'M', Bumblebee: 'B' }.<br>
 * When first letters conflict, conflicts are auto-resolved,
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

/**
 * Use the latest stable version's checksum/filename to locate the entry in archives,
 * complete and update common.json.<br>
 * zh-CN: 用 "最新稳定版" 的校验和/文件名, 在归档中定位条目, 补全并更新 common.json.
 *
 * @param {AndroidStudioArchiveItem[]} archives
 * @returns {Promise<void>}
 */
async function updateLatestArchiveInfo(archives) {
    const latestRows = await getLatestStableArchives(); // [ { kind, filename, sha256, url, size, ... } ]
    const latestExe = latestRows.find(x => x.kind === 'exe');
    const latestZip = latestRows.find(x => x.kind === 'zip');
    const latestTar = latestRows.find(x => x.kind === 'tar');
    if (!latestExe || !latestZip || !latestTar) {
        throw new Error('Latest stable archives missing required "kind" info: exe, zip, or tar');
    }

    /**
     * Search the archive: first try to match by sha256, then by filename.<br>
     * zh-CN: 在归档中查找: 优先用 sha256 命中, 其次用文件名.
     *
     * @param {AndroidStudioArchiveItem[]} rows
     * @param {AndroidStudioStableArchiveItem} target
     * @returns {AndroidStudioArchiveItem | null}
     */
    const matchArchive = (rows, target) => {
        for (const arc of rows) {
            const bySha = target.sha256 && arc.checksums[target.filename] === target.sha256;
            const byName = arc.links.some(l => l.text === target.filename);
            if (bySha || byName) return arc;
        }
        return null;
    };
    const matchedArc = matchArchive(archives, latestExe) || matchArchive(archives, latestZip);
    if (!matchedArc) {
        throw new Error('Could not locate entries in the archive corresponding to the latest version (neither by sha256 nor filename)');
    }

    /**
     * @param {string} suffix
     * @returns {{ filename: string, url: string, sizeGiB?: string | null } | null}
     */
    const pickWinItem = (suffix) => {
        const link = matchedArc.links.find(l => l.text.endsWith(suffix));
        if (!link) return null;
        return { filename: link.text, url: link.href };
    };
    const exeItem = pickWinItem('-windows.exe');
    const zipItem = pickWinItem('-windows.zip');
    const tarItem = pickWinItem('-linux.tar.gz');

    if (!exeItem || !zipItem || !tarItem) {
        throw new Error('Matched archive entry missing Windows EXE/ZIP or Linux TAR download information');
    }

    // Query real file size (concurrent fetch) and format to GiB.
    // zh-CN: 查询真实文件大小 (并发获取), 格式化为 GiB.

    const [ exeBytes, zipBytes, tarBytes ] = await Promise.all([
        exeItem ? getRemoteFileSizeBytes(exeItem.url) : Promise.resolve(null),
        zipItem ? getRemoteFileSizeBytes(zipItem.url) : Promise.resolve(null),
        tarItem ? getRemoteFileSizeBytes(tarItem.url) : Promise.resolve(null),
    ]);
    if (exeItem) exeItem.sizeGiB = bytes2GiB(exeBytes);
    if (zipItem) zipItem.sizeGiB = bytes2GiB(zipBytes);
    if (tarItem) tarItem.sizeGiB = bytes2GiB(tarBytes);

    // Prepare fields needed to write back to common.json.
    // zh-CN: 准备写回 common.json 所需字段.

    const latestVersionName = matchedArc.title.trim(); // e.g. "Android Studio Narwhal Feature Drop | 2025.1.2"
    const latestVersionDate = toYYYYMMDD(matchedArc.date) || ''; // e.g. "2025/07/31"

    // Read and update common.json (only update keys containing "android_studio_latest_" fragment and version name date key).
    // zh-CN: 读取并更新 common.json (仅更新带有 "android_studio_latest_" 片段的键与版本名日期键).

    const commonJsonPath = path.resolve(process.cwd(), '../.readme/common.json');
    const commonRaw = await fsp.readFile(commonJsonPath, 'utf8');
    const commonObj = JSON.parse(commonRaw);

    const updatedCommon = {
        ...commonObj,
        android_studio_latest_recommended_version_name: latestVersionName,
        var_date_android_studio_latest_recommended_version_name: latestVersionDate,
        android_studio_latest_recommended_file_name_of_exe: exeItem.filename,
        android_studio_latest_recommended_download_address_of_exe: exeItem.url,
        android_studio_latest_recommended_file_size_of_exe: exeItem.sizeGiB ?? commonObj.android_studio_latest_recommended_file_size_of_exe,
        android_studio_latest_recommended_file_name_of_zip: zipItem.filename,
        android_studio_latest_recommended_download_address_of_zip: zipItem.url,
        android_studio_latest_recommended_file_size_of_zip: zipItem.sizeGiB ?? commonObj.android_studio_latest_recommended_file_size_of_zip,
        android_studio_latest_recommended_file_name_of_tar: tarItem.filename,
        android_studio_latest_recommended_download_address_of_tar: tarItem.url,
        android_studio_latest_recommended_file_size_of_tar: tarItem.sizeGiB ?? commonObj.android_studio_latest_recommended_file_size_of_tar,
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
 * @param {AndroidStudioArchiveItem[]} archives
 */
function getCodenameMapLinesInfo(archives) {

    /**
     * Parse "codename and version number" from title,
     * zh-CN: 从标题中解析 "代号与版本号",
     *
     * @example string
     * 'Android Studio Meerkat Feature Drop | 2024.3.2 RC 1'
     *
     * @param {string} title
     * @returns {string | null}
     */
    const codenameFromTitle = title => {
        // Capture "Android Studio <Codename> [Feature Drop]".
        // zh-CN: 捕获 "Android Studio <Codename> [Feature Drop]".
        const m = /Android Studio\s+(.+?)\s*(?:(\s+\d+\s+)?Feature Drop)?(?=\s*\|)/i.exec(title);
        return m ? m[1].trim() : null;
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

    const codenames = [ ...new Set(archives.map(o => codenameFromTitle(o.title)).filter(Boolean)) ];
    const nameToCode = buildUniquePrefixes(codenames, manualCodenameOverrides);

    /**
     * @example Map<version, Set<prefixCode>>
     * Map(22) {
     *   ... ...
     *   '2024.2.1' => Set(1) { 'L' },
     *   '2024.1.3' => Set(1) { 'L' },
     *   '2024.1.2' => Set(1) { 'K' },
     *   '2024.1.1' => Set(1) { 'K' },
     *   '2023.3.2' => Set(2) { 'K', 'J' },
     *   '2023.3.1' => Set(1) { 'J' },
     *   '2023.2.1' => Set(1) { 'I' },
     *   ... ...
     * }
     * @type {Map<string, Set<string>>}
     */
    const versionToLetters = new Map();

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

    for (const arc of archives) {
        if (!arc.version) continue;
        const cname = codenameFromTitle(arc.title);
        if (!cname) continue;

        const code = nameToCode.get(cname);
        if (!code) continue;

        if (!versionToLetters.has(arc.version)) versionToLetters.set(arc.version, new Set());
        versionToLetters.get(arc.version).add(code);

        const d = new Date(arc.date);
        const existed = letterBorn.get(code);
        if (!existed || d < existed.born) {
            letterBorn.set(code, { name: cname, born: d });
        }
    }

    const sortedVersions = Array.from(versionToLetters.keys()).sort((a, b) => {
        // Split version string into [ y: year, m: minor, p: patch ].
        // zh-CN: 将版本字符串拆分为 [ y: 年份, m: 次版本号, p: 补丁版本号 ].
        // e.g. "2024.3.2" -> [ y: 2024, m: 3, p: 2 ].
        const [ ay, am, ap ] = a.split('.').map(Number);
        const [ by, bm, bp ] = b.split('.').map(Number);
        return by - ay || bm - am || bp - ap;
    });

    /**
     * @example Array<[version, jointPrefixCode]>
     * [
     *   ... ..
     *   [ '2024.2.2', 'L' ],
     *   [ '2024.2.1', 'L' ],
     *   [ '2024.1.3', 'L' ],
     *   [ '2024.1.2', 'K' ],
     *   [ '2024.1.1', 'K' ],
     *   [ '2023.3.2', 'J|K' ],
     *   [ '2023.3.1', 'J' ],
     *   [ '2023.2.1', 'I' ],
     *   ... ...
     * ]
     * @type {Array<[string, string]>}
     */
    const versionLettersList = sortedVersions.map(v => [ v, Array.from(versionToLetters.get(v)).sort().join('|') ]);

    /**
     * @example { minorSeries: { patch: letters } }
     * {
     *   ... ...
     *   '2025.1': { '1': 'N', '2': 'N', '3': 'N', '4': 'N' },
     *   '2024.3': { '1': 'M', '2': 'M' },
     *   '2024.2': { '1': 'L', '2': 'L' },
     *   '2024.1': { '1': 'K', '2': 'K', '3': 'L' },
     *   '2023.3': { '1': 'J', '2': 'J|K' },
     *   '2023.2': { '1': 'I' },
     *   '2023.1': { '1': 'H' },
     *   '2022.3': { '1': 'G' },
     *   ... ...
     * }
     * @type {Object<string, { [patch: number]: string }>}
     */
    const rawVersionLettersMap = {};
    for (let i = 0; i < versionLettersList.length; i++) {
        const [ v, letters ] = versionLettersList[i];
        const matched = v.match(/(^\d+\.\d+)(?:\.(\d+))?/);
        if (!matched) continue;
        const [ , minorSeries, patch ] = matched;
        if (minorSeries in rawVersionLettersMap) {
            rawVersionLettersMap[minorSeries][patch] = letters;
        } else {
            rawVersionLettersMap[minorSeries] = { [patch]: letters };
        }
    }

    /**
     * When all versions with the same minor series prefix (like `2025.1.x`)
     * point to the same codename prefix (like `'N'`),
     * they can be merged (like `{ '2025.1' : 'N' }`, where `2025.1` is the minor series prefix),
     * otherwise retain the original split form (like `2024.1.x` cannot be merged).<br>
     * zh-CN:<br>
     * 当次版本系列相同的版本 (如 `2025.1.x`) 全部指向同一个代号前缀 (如 `'N'`) 时,
     * 可进行合并 (如 `{ '2025.1' : 'N' }`, 其中 `2025.1` 为次版本系列),
     * 否则保留原始的拆分形式 (如 `2024.1.x` 不可合并).
     * @example { version: letters }
     * {
     *   ... ...
     *   '2025.1': 'N',
     *   '2024.3': 'M',
     *   '2024.2': 'L',
     *   '2024.1.1': 'K',
     *   '2024.1.2': 'K',
     *   '2024.1.3': 'L',
     *   '2023.3.1': 'J',
     *   '2023.3.2': 'J|K',
     *   '2023.2': 'I',
     *   '2023.1': 'H',
     *   '2022.3': 'G',
     *   ... ...
     * }
     * @type {Object<[version: string], string>}
     */
    const combinedVersionLettersMap = {};

    Object.entries(rawVersionLettersMap).forEach(([ minorSeries, patchToLetters ]) => {
        const letterValues = Object.values(patchToLetters);
        if (new Set(letterValues).size === 1) {
            /* Combine. (zh-CN: 合并.) */
            combinedVersionLettersMap[minorSeries] = letterValues[0];
        } else {
            /* Keep expanded. (zh-CN: 保持展开.) */
            Object.entries(patchToLetters).forEach(([ patch, letters ]) => {
                combinedVersionLettersMap[`${minorSeries}.${patch}`] = letters;
            });
        }
    });

    const versionMapLines = Object.entries(combinedVersionLettersMap)
        .sort((a, b) => compareVersionStrings(b[0], a[0]))
        .map(([ v, letters ]) => `"${v}" to "${letters}",`);

    const sortedLetters = Array.from(letterBorn.entries())
        .sort((a, b) => b[1].born.getTime() - a[1].born.getTime());

    const codenameMapLines = sortedLetters.map(([ code, { name, born } ]) => {
        const bornStr = toUpdatedStamp(born);
        // e.g. `"M" to "Meerkat", /* Born on Nov 12, 2024. */`.
        return `"${code}" to "${name}", /* Born on ${bornStr}. */`;
    });

    return { versionMapLines, codenameMapLines };
}

(async function main() {
    const archives = await getAndroidStudioArchives();
    await updateLatestArchiveInfo(archives);

    const { versionMapLines, codenameMapLines } = getCodenameMapLinesInfo(archives);
    await batchUpdateAnchoredBlocks('../settings.gradle.kts', [ {
        type: 'map',
        anchorTag: 'ANDROID_STUDIO_CODENAME_VERSION_MAP',
        mapName: 'codenameVersionMap',
        lines: versionMapLines,
        updatedLabel: 'Android Studio codename version map',
    }, {
        type: 'map',
        anchorTag: 'ANDROID_STUDIO_CODENAME_MAP',
        mapName: 'codenameMap',
        lines: codenameMapLines,
        updatedLabel: 'Android Studio codename map',
    } ]);
})().catch(err => {
    console.error(err);
    process.exitCode = 1;
});
