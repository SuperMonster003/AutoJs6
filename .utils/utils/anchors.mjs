// utils/anchors.mjs

import * as fsp from 'node:fs/promises';
import * as path from 'node:path';
import { toUpdatedStamp } from './date.mjs';

/**
 * @param {string} s
 * @returns {string}
 */
const normalize = s => String(s).replace(/\s+/g, '');

/**
 * 在指定 Anchor 块中, 用给定的替换函数生成新块内容.
 *
 * @param {string} src
 * @param {string} anchorTag
 * @param {(block: string) => { newBlock: string, changed: boolean }} replaceBlockFn
 * @returns {{ src: string, changed: boolean }} - 返回 { src: 新源码, changed: 是否发生变更 }. 若找不到锚点, 原样返回.
 */
export function replaceInAnchoredBlock(src, anchorTag, replaceBlockFn) {
    const beginTag = `// @AnchorBegin ${anchorTag}`;
    const endTag = `// @AnchorEnd ${anchorTag}`;

    const beginIdx = src.indexOf(beginTag);
    if (beginIdx === -1) return { src, changed: false };

    const endIdx = src.indexOf(endTag, beginIdx + beginTag.length);
    if (endIdx === -1) return { src, changed: false };

    const before = src.slice(0, beginIdx);
    const block = src.slice(beginIdx, endIdx); // 不包含 endTag
    const after = src.slice(endIdx);

    const { newBlock, changed } = replaceBlockFn(block) || {};
    if (!changed || !newBlock) return { src, changed: false };

    return { src: before + newBlock + after, changed };
}

/**
 * 替换锚点块中的某个 map 声明 (如 mapOf(...)), 并在变更时自动刷新 @Updated 日期.
 *
 * @param {string} src
 * @param {Object} options
 * @param {string} options.anchorTag - 块的锚点名
 * @param {string} options.mapName - 变量名, 如 agpVersionMap
 * @param {string[]} options.lines - map 体内的每行 (不含缩进, 由函数自动缩进)
 * @param {number} [options.linesIndent=4]
 * @param {(date?: Date) => string} [options.toUpdatedStamp=toUpdatedStamp] - 自定义时间戳函数 (可选)
 * @returns {{ src: string, changed: boolean }}}
 */
export function replaceAnchoredMapBlock(src, {
    anchorTag,
    mapName,
    lines,
    linesIndent = 4,
    toUpdatedStamp: toStamp = toUpdatedStamp,
}) {
    return replaceInAnchoredBlock(src, anchorTag, (block) => {
        let changed = false;

        const re = new RegExp(`([\\t\\x20]*)(va[lr]\\s+)?${mapName}\\s*=\\s*mapOf\\([\\s\\S]*?\\)(,?)`, 'm');
        let updatedBlock = block.replace(re, (/** @type {string} */ original, /** @type {string} */ indent, /** @type {string} */ keyword, /** @type {string} */ comma) => {
            const kw = keyword ?? '';
            const body = lines.map(l => `${' '.repeat(linesIndent)}${indent}${l}`).join('\n');
            const next = `${indent}${kw}${mapName} = mapOf(\n${body}\n${indent})${comma}`;
            if (normalize(original) !== normalize(next)) changed = true;
            return next;
        });
        if (changed) {
            updatedBlock = updatedBlock.replace(
                /(@Updated[^\n]*?\son\s)([A-Z][a-z]{2}\s\d{1,2},\s\d{4})(\.?)/,
                (_, p1, _old, p3) => `${p1}${(toStamp())}${p3}`,
            );
        }

        return { newBlock: updatedBlock, changed };
    });
}

/**
 * 替换锚点块中的某个 list 声明 (如 listOf(...)), 并在变更时自动刷新 @Updated 日期.
 *
 * @param {string} src
 * @param {Object} options
 * @param {string} options.anchorTag - 块的锚点名
 * @param {string} options.listName - 变量名, 如 modules 或 libs
 * @param {string[]} options.lines - list 体内的每行 (不含缩进, 由函数自动缩进)
 * @param {number} [options.linesIndent=4]
 * @param {(date?: Date) => string} [options.toUpdatedStamp=toUpdatedStamp] - 自定义时间戳函数 (可选)
 * @returns {{ src: string, changed: boolean }}}
 */
export function replaceAnchoredListBlock(src, {
    anchorTag,
    listName,
    lines,
    linesIndent = 4,
    toUpdatedStamp: toStamp = toUpdatedStamp,
}) {
    return replaceInAnchoredBlock(src, anchorTag, (block) => {
        let changed = false;

        const re = new RegExp(`([\\t\\x20]*)(va[lr]\\s+)?${listName}\\s*=\\s*listOf\\([\\s\\S]*?\\)(,?)`, 'm');
        let updatedBlock = block.replace(re, (original, indent, keyword, comma) => {
            const kw = keyword ?? '';
            const body = lines.map(l => `${' '.repeat(linesIndent)}${indent}${l}`).join('\n');
            const next = `${indent}${kw}${listName} = listOf(\n${body}\n${indent})${comma}`;
            if (normalize(original) !== normalize(next)) changed = true;
            return next;
        });
        if (changed) {
            updatedBlock = updatedBlock.replace(
                /(@Updated[^\n]*?\son\s)([A-Z][a-z]{2}\s\d{1,2},\s\d{4})(\.?)/,
                (_, p1, _old, p3) => `${p1}${(toStamp())}${p3}`,
            );
        }

        return { newBlock: updatedBlock, changed };
    });
}

/**
 * 高层封装: 读取文件 -> 替换锚点 map -> 若有变更则写回 -> 打印日志.
 *
 * @param {string} filePath
 * @param {Object} options
 * @param {string} options.anchorTag - 块的锚点名
 * @param {string} options.mapName - 变量名, 如 agpVersionMap
 * @param {string[]} options.lines - map 体内的每行 (不含缩进, 由函数自动缩进)
 * @param {number} [options.linesIndent=4]
 * @param {string} [options.updatedLabel='']
 * @param {(date?: Date) => string} [options.toUpdatedStamp=toUpdatedStamp] - 自定义时间戳函数 (可选)
 * @param {Console} [options.logger=console]
 * @returns {Promise<{ changed: boolean, content: string }>}
 */
export async function updateAnchoredMapInFile(filePath, {
    anchorTag,
    mapName,
    lines,
    linesIndent = 4,
    updatedLabel = '',
    toUpdatedStamp: toStamp = toUpdatedStamp,
    logger = console,
}) {
    const filename = path.basename(filePath);
    const raw = await fsp.readFile(filePath, 'utf8');
    const { src: updated, changed } = replaceAnchoredMapBlock(raw, { anchorTag, mapName, lines, linesIndent, toUpdatedStamp: toStamp });

    if (changed) {
        await fsp.writeFile(filePath, updated, 'utf8');
        logger.log(`[${filename}] 已更新` + (updatedLabel ? ` (${updatedLabel})` : ''));
    } else {
        // logger.log(`[${filename}] 无需更新` + (updatedLabel ? ` (${updatedLabel})` : ''));
    }
    return { changed, content: updated };
}

/**
 * 高层封装: 读取文件 -> 替换锚点 list -> 若有变更则写回 -> 打印日志.
 *
 * @param {string} filePath
 * @param {Object} options
 * @param {string} options.anchorTag - 块的锚点名
 * @param {string} options.listName - 变量名, 如 modules 或 libs
 * @param {string[]} options.lines - list 体内的每行 (不含缩进, 由函数自动缩进)
 * @param {number} [options.linesIndent=4]
 * @param {string} [options.updatedLabel='']
 * @param {(date?: Date) => string} [options.toUpdatedStamp=toUpdatedStamp] - 自定义时间戳函数 (可选)
 * @param {Console} [options.logger=console]
 * @returns {Promise<{ changed: boolean, content: string }>}
 */
export async function updateAnchoredListInFile(filePath, {
    anchorTag,
    listName,
    lines,
    linesIndent = 4,
    updatedLabel = '',
    toUpdatedStamp: toStamp = toUpdatedStamp,
    logger = console,
}) {
    const filename = path.basename(filePath);
    const raw = await fsp.readFile(filePath, 'utf8');
    const { src: updated, changed } = replaceAnchoredListBlock(raw, { anchorTag, listName, lines, linesIndent, toUpdatedStamp: toStamp });

    if (changed) {
        await fsp.writeFile(filePath, updated, 'utf8');
        logger.log(`[${filename}] 已更新` + (updatedLabel ? ` (${updatedLabel})` : ''));
    } else {
        // logger.log(`[${filename}] 无需更新` + (updatedLabel ? ` (${updatedLabel})` : ''));
    }
    return { changed, content: updated };
}

/**
 * @typedef {Object} AnchoredBlockUpdateOption
 * @property {'map' | 'list' | 'custom'} type
 * @property {string} anchorTag
 * @property {string} [mapName]
 * @property {string} [listName]
 * @property {string[]} lines
 * @property {number} [linesIndent=4]
 * @property {string} [updatedLabel]
 * @property {(srcInBlock: string, options: { toUpdatedStamp?: (date?: Date) => string }) => { newBlock: string, changed: boolean }} [replacer]
 */
/**
 * 批量在同一文件内进行多锚点替换 (map 与 list 都支持, 读一次/写一次).
 *
 * @param {string} filePath
 * @param {AnchoredBlockUpdateOption[]} optionList
 * @param {Object} [extraOptions={}]
 * @param {(date?: Date) => string} [extraOptions.toUpdatedStamp=toUpdatedStamp] - 自定义时间戳函数 (可选)
 * @param {Console} [extraOptions.logger=console]
 * @returns {Promise<{ changed: boolean, content: string }>}
 */
export async function batchUpdateAnchoredBlocks(filePath, optionList, {
    toUpdatedStamp: toStamp = toUpdatedStamp,
    logger = console,
} = {}) {
    const filename = path.basename(filePath);
    let raw = await fsp.readFile(filePath, 'utf8');
    let changedAny = false;

    for (const op of optionList) {
        let res = { src: raw, changed: false };

        if (op.type === 'map') {
            res = replaceAnchoredMapBlock(raw, {
                anchorTag: op.anchorTag,
                mapName: op.mapName,
                lines: op.lines,
                linesIndent: op.linesIndent,
                toUpdatedStamp: toStamp,
            });
        } else if (op.type === 'list') {
            res = replaceAnchoredListBlock(raw, {
                anchorTag: op.anchorTag,
                listName: op.listName,
                lines: op.lines,
                linesIndent: op.linesIndent,
                toUpdatedStamp: toStamp,
            });
        } else if (op.type === 'custom' && typeof op.replacer === 'function') {
            res = replaceInAnchoredBlock(raw, op.anchorTag, (block) => op.replacer(block, { toUpdatedStamp: toStamp }));
        } else {
            logger.warn(`[${filename}] 未知操作类型或缺少参数:`, op);
            continue;
        }

        if (res.changed) {
            changedAny = true;
            raw = res.src;
            logger.log(`[${filename}] 已更新 (${op['updatedLabel'] ?? op.anchorTag})`);
        } else {
            // logger.log(`[${filename}] 无需更新 (${op['updatedLabel'] ?? op.anchorTag})`);
        }
    }

    if (changedAny) {
        await fsp.writeFile(filePath, raw, 'utf8');
    }
    return { changed: changedAny, content: raw };
}
