// scrape-and-inject-android-studio-codename_maps.mjs

/** @typedef {import('puppeteer').Page} Page */
/** @typedef {import('puppeteer').Frame} Frame */
/** @typedef {import('./fetch-and-parse-android-studio-latest-stable-version.mjs').Row} Row */
/** @typedef {import('./fetch-and-parse-android-studio-archives.mjs').ArchiveItem} ArchiveItem */
/**
 * @template {Node} T
 * @typedef {import('puppeteer').ElementHandle<T>} ElementHandle
 */

import { getLatestStableWindows } from './fetch-and-parse-android-studio-latest-stable-version.mjs';
import { batchUpdateAnchoredBlocks } from './utils/anchors.mjs';
import { compareVersionStrings } from './utils/versioning.mjs';
import { toUpdatedStamp, toYYYYMMDD } from './utils/date.mjs';
import { bytes2GiB } from './utils/format.mjs';
import { getRemoteFileSizeBytes } from './utils/fetch.mjs';
import { getAndroidStudioArchives } from './fetch-and-parse-android-studio-archives.mjs';

async function main() {
    // 1) 用 "最新稳定版" 的校验和/文件名, 在归档中定位条目, 补全并更新 common.json

    const archives = await getAndroidStudioArchives();
    const latestRows = await getLatestStableWindows(); // [{kind, filename, sha256, url, size, ...}]
    const latestExe = latestRows.find(x => x.kind === 'exe');
    const latestZip = latestRows.find(x => x.kind === 'zip');
    const latestTar = latestRows.find(x => x.kind === 'tar');
    if (!latestExe || !latestZip || !latestTar) {
        throw new Error('最新稳定版条目缺少 Windows EXE/ZIP 或 TAR');
    }

    /**
     * 在归档中查找: 优先用 sha256 命中, 其次用文件名.
     *
     * @param {ArchiveItem[]} rows
     * @param {Row} target
     * @return {ArchiveItem | null}
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
        throw new Error('未能在归档中定位到与最新版本对应的条目 (按 sha256/文件名均未命中)');
    }

    /**
     * 从匹配到的 expandable 中抽取 Windows EXE/ZIP 的链接/文件名.
     *
     * @param {string} suffix
     * @return {{ filename: string, url: string, sizeGiB?: string | null } | null}
     */
    const pickWinItem = suffix => {
        // suffix: "-windows.exe" | "-windows.zip"
        const link = matchedArc.links.find(l => l.text.endsWith(suffix));
        if (!link) return null;
        return {
            filename: link.text,
            url: link.href,
        };
    };
    const exeItem = pickWinItem('-windows.exe');
    const zipItem = pickWinItem('-windows.zip');
    const tarItem = pickWinItem('-linux.tar.gz');

    // 查询真实文件大小 (并发获取), 格式化为 GiB
    const [ exeBytes, zipBytes, tarBytes ] = await Promise.all([
        exeItem ? getRemoteFileSizeBytes(exeItem.url) : Promise.resolve(null),
        zipItem ? getRemoteFileSizeBytes(zipItem.url) : Promise.resolve(null),
        tarItem ? getRemoteFileSizeBytes(tarItem.url) : Promise.resolve(null),
    ]);
    if (exeItem) exeItem.sizeGiB = bytes2GiB(exeBytes);
    if (zipItem) zipItem.sizeGiB = bytes2GiB(zipBytes);
    if (tarItem) tarItem.sizeGiB = bytes2GiB(tarBytes);

    // 准备写回 common.json 所需字段
    const latestVersionName = matchedArc.title.trim(); // 例: "Android Studio Narwhal Feature Drop | 2025.1.2"
    const latestVersionDate = toYYYYMMDD(matchedArc.date) || ''; // 例: "2025/07/31"

    if (!exeItem || !zipItem) {
        throw new Error('匹配到的归档条目缺少 Windows EXE 或 ZIP 下载信息');
    }

    // 读取并更新 common.json (仅更新带有 "android_studio_latest_" 片段的键与版本名日期键)
    const fs = await import('node:fs/promises');
    const path = await import('node:path');

    const commonJsonPath = path.resolve(process.cwd(), '../.readme/common.json');
    const commonRaw = await fs.readFile(commonJsonPath, 'utf8');
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
        await fs.writeFile(commonJsonPath, JSON.stringify(updatedCommon, null, 2), 'utf8');
        console.log('[common.json] 已更新 (Android Studio 数据)');
        console.log(`-- '${commonObj.android_studio_latest_recommended_version_name}'`);
        console.log(`-> '${updatedCommon.android_studio_latest_recommended_version_name}'`);
    } else {
        // console.log('[common.json] 无需更新 (Android Studio 数据)');
    }

    // 2) 汇总代号 - 版本映射与代号首发日期, 更新 settings.gradle.kts 两个锚点块
    // 2.1 从标题中解析 "代号与版本号"
    // 标题模式示例: "Android Studio Meerkat Feature Drop | 2024.3.2 RC 1"
    /**
     * @param {string} t
     * @return {string | null}
     */
    const codenameFromTitle = t => {
        // 捕获 "Android Studio <Codename> [Feature Drop] |"
        const m = /Android Studio\s+(.+?)\s*(?:(\s+\d+\s+)?Feature Drop)?\s*\|/i.exec(t);
        return m ? m[1].trim() : null;
    };

    // 用户手动覆盖映射 (可按需填写或从外部读取)
    /** @type {Object<string, string>} */
    const manualCodenameOverrides = {
        /* e.g. 'Meerkat': 'Mkt' */
    };

    // 生成唯一代码: 按顺序, 出现冲突则对冲突组统一递增长度; 覆盖项优先生效且不可被自动改动
    /**
     * @param {string[]} names 原始代号 (保留大小写与空格, 顺序与站点一致)
     * @param {Object<string, string>} overrides 手动覆盖映射
     * @returns {Map<string, string>} name -> code
     */
    function buildUniquePrefixes(names, overrides) {
        // 代码不包含空格; 按名称去空格后逐字符递增长度
        const entries = names.map(n => ({
            name: n,
            base: n.replace(/\s+/g, ''), // 去空格用于截取
            len: Math.max(1, overrides[n] ? overrides[n].replace(/\s+/g, '').length : 1),
            code: overrides[n] ? overrides[n].replace(/\s+/g, '') : null,
            locked: !!overrides[n],
        }));

        // 先赋初值
        for (const e of entries) {
            if (!e.code) e.code = e.base.slice(0, e.len);
        }

        // 检测并解决冲突
        const maxLenByName = new Map(entries.map(e => [ e.name, e.base.length ]));
        // 循环上限保护, 防止极端情况下死循环
        for (let step = 0; step < 1024; step++) {
            // 统计冲突组: code -> indices
            /** @type {Map<string, number[]>} */
            const bucket = new Map();
            entries.forEach((e, idx) => {
                const key = e.code;
                if (!bucket.has(key)) bucket.set(key, []);
                bucket.get(key).push(idx);
            });

            // 找到有冲突的组 (size >= 2)
            const conflicts = Array.from(bucket.entries()).filter(([ , indices ]) => indices.length >= 2);
            if (conflicts.length === 0) break; // 已无冲突

            // 逐组处理
            for (const [ code, indices ] of conflicts) {
                // 若组内存在 >=2 个锁定项且 code 相同 -> 直接报错
                const lockedIndices = indices.filter(i => entries[i].locked);
                if (lockedIndices.length >= 2) {
                    const letter = code[0]?.toUpperCase() || '?';
                    const groupNames = indices.map(i => entries[i].name);
                    throw new Error(`[CodenameMap] 手动覆盖映射发生冲突: "${code}" -> ${groupNames.join(', ')}. 请调整覆盖映射. 冲突首字母组: ${letter}*`);
                }

                // 让组内所有 "未锁定项" 统一递增长度
                for (const i of indices) {
                    const e = entries[i];
                    if (e.locked) continue; // 覆盖项不改
                    const maxLen = maxLenByName.get(e.name);
                    if (e.len >= maxLen) {
                        // 已经用到全长仍冲突 -> 无法自动消解
                        const letter = e.base[0]?.toUpperCase() || '?';
                        const groupNames = indices.map(ii => entries[ii].name);
                        throw new Error(`[CodenameMap] 无法自动消解冲突 ("${e.name}" 与同组名称完全相同或为彼此前缀到尽头). 请为该首字母组手动指定覆盖映射.\n- 组首字母: ${letter}\n- 组成员: ${groupNames.join(', ')}`);
                    }
                    e.len += 1;
                    e.code = e.base.slice(0, e.len);
                }
            }
        }

        return new Map(entries.map(e => [ e.name, e.code ]));
    }

    // 提取按页面顺序的代号列表 (去重, 保留第一次出现顺序)
    const codenamesOrdered = [];
    const seen = new Set();
    for (const arc of archives) {
        const cname = codenameFromTitle(arc.title);
        if (!cname) continue;
        if (!seen.has(cname)) {
            seen.add(cname);
            codenamesOrdered.push(cname);
        }
    }

    // 构建 name->code 映射 (按规则自动消解冲突; 支持手动覆盖)
    const nameToCode = buildUniquePrefixes(codenamesOrdered, manualCodenameOverrides);

    // 收集每版本 (yyyy.m.patch) 对应的代号集合, 以及每个代号的首发日期
    /** @type {Map<string, Set<string>>} */
    const versionToLetters = new Map();
    /** @type {Map<string, { name:string, born: Date } >} */
    const letterBorn = new Map();

    for (const arc of archives) {
        if (!arc.version) continue; // 版本号 (yyyy.m.patch)
        const cname = codenameFromTitle(arc.title);
        if (!cname) continue;

        const code = nameToCode.get(cname);
        if (!code) continue;

        // 映射 version -> codes
        if (!versionToLetters.has(arc.version)) versionToLetters.set(arc.version, new Set());
        versionToLetters.get(arc.version).add(code);

        // 记录代号首次出现日期
        const d = new Date(arc.date);
        const existed = letterBorn.get(code);
        if (!existed || d < existed.born) {
            letterBorn.set(code, { name: cname, born: d });
        }
    }

    // 生成 codenameVersionMap 文本 (按版本倒序排列, 值用 "A|B" 连接)
    const sortedVersions = Array.from(versionToLetters.keys()).sort((a, b) => {
        const [ ay, am, ap ] = a.split('.').map(Number);
        const [ by, bm, bp ] = b.split('.').map(Number);
        return by - ay || bm - am || bp - ap;
    });

    const versionLettersList = sortedVersions.map(v => [ v, Array.from(versionToLetters.get(v)).sort().join('|') ]);

    /* e.g. { "2023.3": {1: "J", 2: "J|K"} }. */
    /** @type {Object<string, { [patch: number]: string }>} */
    const rawVersionLettersMap = {};
    for (let i = 0; i < versionLettersList.length; i++) {
        const [ v, letters ] = versionLettersList[i];
        const matched = v.match(/(^\d+\.\d+)(?:\.(\d+))?/);
        if (!matched) continue;
        const [ , major, patch ] = matched;
        if (major in rawVersionLettersMap) {
            rawVersionLettersMap[major][patch] = letters;
        } else {
            rawVersionLettersMap[major] = { [patch]: letters };
        }
    }

    /* e.g. { "2024.3": "M", "2024.1.2": "K" }. */
    const combinedVersionLettersMap = {};

    Object.entries(rawVersionLettersMap).forEach(([ major, patchToLetters ]) => {
        const letterValues = Object.values(patchToLetters);
        if (new Set(letterValues).size === 1) {
            combinedVersionLettersMap[major] = letterValues[0];
        } else {
            Object.entries(patchToLetters).forEach(([ patch, letters ]) => {
                combinedVersionLettersMap[`${major}.${patch}`] = letters;
            });
        }
    });

    const versionMapLines = Object.entries(combinedVersionLettersMap)
        .sort((a, b) => compareVersionStrings(b[0], a[0]))
        .map(([ v, letters ]) => `"${v}" to "${letters}",`);

    // 生成 codenameMap 文本 (按 born 日期倒序; 注释: Born on Mon d, yyyy.)
    // 这里 key 为自动生成的 code (可能为一到多字符), value 为完整代号
    const sortedLetters = Array.from(letterBorn.entries())
        .sort((a, b) => b[1].born.getTime() - a[1].born.getTime());

    const codenameMapLines = sortedLetters.map(([ code, { name, born } ]) => {
        const bornStr = toUpdatedStamp(born);
        return `"${code}" to "${name}", /* Born on ${bornStr}. */`;
    });

    await batchUpdateAnchoredBlocks('../settings.gradle.kts', [ {
        type: 'map',
        anchorTag: 'ANDROID_STUDIO_CODENAME_VERSION_MAP',
        mapName: 'codenameVersionMap',
        lines: versionMapLines,
        updatedLabel: 'Android Studio 代号版本映射',
    }, {
        type: 'map',
        anchorTag: 'ANDROID_STUDIO_CODENAME_MAP',
        mapName: 'codenameMap',
        lines: codenameMapLines,
        updatedLabel: 'Android Studio 代号名称映射',
    } ]);
}

main().catch(err => {
    console.error(err);
    process.exitCode = 1;
});