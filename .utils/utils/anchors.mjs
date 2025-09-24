// utils/anchors.mjs

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

import * as fsp from 'node:fs/promises';
import * as path from 'node:path';
import { escapeRegExp } from './format.mjs';
import { toUpdatedStamp } from './date.mjs';

/**
 * @param {string} s
 * @returns {string}
 */
const normalize = (s) => String(s).replace(/\s+/g, '');

/**
 * Generate new block content with the given replacement function in the specified Anchor block.<br>
 * zh-CN: 在指定 Anchor 块中, 用给定的替换函数生成新块内容.
 *
 * @param {string} src
 * @param {string} anchorTag
 * @param {(block: string) => { newBlock: string, changed: boolean }} replaceBlockFn
 * @returns {{ src: string, changed: boolean }}
 */
export function replaceInAnchoredBlock(src, anchorTag, replaceBlockFn) {
    const beginTag = `// @AnchorBegin ${anchorTag}`;
    const endTag = `// @AnchorEnd ${anchorTag}`;

    const beginIdx = src.indexOf(beginTag);
    if (beginIdx === -1) throw new Error(`Anchor tag "${anchorTag}" not found in the source code`);

    const endIdx = src.indexOf(endTag, beginIdx + beginTag.length);
    if (endIdx === -1) throw new Error(`Anchor tag "${anchorTag}" not found in the source code`);

    const before = src.slice(0, beginIdx);
    const block = src.slice(beginIdx, endIdx);
    const after = src.slice(endIdx);

    const { newBlock, changed } = replaceBlockFn(block) || {};
    if (!changed || !newBlock) return { src, changed: false };

    return { src: before + newBlock + after, changed };
}

/**
 * Replace a map declaration (like mapOf(...)) in an anchor block
 * and automatically refresh the @Updated date when changed.<br>
 * zh-CN: 替换锚点块中的某个 map 声明 (如 mapOf(...)), 并在变更时自动刷新 @Updated 日期.
 *
 * @param {string} src
 * @param {Object} options
 * @param {string} options.anchorTag
 * @param {string} options.mapName
 * @param {string[]} options.lines
 * @param {number} [options.linesIndent=4]
 * @param {(date?: Date) => string} [options.toUpdatedStamp=toUpdatedStamp]
 * @returns {{ src: string, changed: boolean }}
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

        const re = new RegExp(String.raw`([\t ]*)(va[lr]\s+)?${escapeRegExp(mapName)}\s*=\s*mapOf\(.*?\)(,?)`, 's');

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
 * Replace a list declaration (like listOf(...)) in an anchor block
 * and automatically refresh the @Updated date when changed.<br>
 * zh-CN:<br>
 * 替换锚点块中的某个 list 声明 (如 listOf(...)), 并在变更时自动刷新 @Updated 日期.
 *
 * @param {string} src
 * @param {Object} options
 * @param {string} options.anchorTag
 * @param {string} options.listName
 * @param {string[]} options.lines
 * @param {number} [options.linesIndent=4]
 * @param {(date?: Date) => string} [options.toUpdatedStamp=toUpdatedStamp]
 * @returns {{ src: string, changed: boolean }}
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
 * @param {string} filePath
 * @param {Object} options
 * @param {string} options.anchorTag
 * @param {string} options.mapName
 * @param {string[]} options.lines
 * @param {number} [options.linesIndent=4]
 * @param {string} [options.updatedLabel='']
 * @param {(date?: Date) => string} [options.toUpdatedStamp=toUpdatedStamp]
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
        logger.log(`[${filename}] Updated` + (updatedLabel ? ` (${updatedLabel})` : ''));
    } else {
        // logger.log(`[${filename}] No update needed` + (updatedLabel ? ` (${updatedLabel})` : ''));
    }
    return { changed, content: updated };
}

/**
 * @param {string} filePath
 * @param {Object} options
 * @param {string} options.anchorTag
 * @param {string} options.listName
 * @param {string[]} options.lines
 * @param {number} [options.linesIndent=4]
 * @param {string} [options.updatedLabel='']
 * @param {(date?: Date) => string} [options.toUpdatedStamp=toUpdatedStamp]
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
        logger.log(`[${filename}] Updated` + (updatedLabel ? ` (${updatedLabel})` : ''));
    } else {
        // logger.log(`[${filename}] No update needed` + (updatedLabel ? ` (${updatedLabel})` : ''));
    }
    return { changed, content: updated };
}

/**
 * Batch replace multiple anchors within the same file
 * (supports both map and list, requiring only one read/write operation).<br>
 * zh-CN: 批量在同一文件内进行多锚点替换 (同时支持 map 与 list, 读写仅需一次).
 *
 * @param {string} filePath
 * @param {AnchoredBlockUpdateOption[]} optionList
 * @param {Object} [extraOptions={}]
 * @param {(date?: Date) => string} [extraOptions.toUpdatedStamp=toUpdatedStamp]
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

    for (const opt of optionList) {
        let res = { src: raw, changed: false };

        if (opt.type === 'map') {
            res = replaceAnchoredMapBlock(raw, {
                anchorTag: opt.anchorTag,
                mapName: opt.mapName,
                lines: opt.lines,
                linesIndent: opt.linesIndent,
                toUpdatedStamp: toStamp,
            });
        } else if (opt.type === 'list') {
            res = replaceAnchoredListBlock(raw, {
                anchorTag: opt.anchorTag,
                listName: opt.listName,
                lines: opt.lines,
                linesIndent: opt.linesIndent,
                toUpdatedStamp: toStamp,
            });
        } else if (opt.type === 'custom' && typeof opt.replacer === 'function') {
            res = replaceInAnchoredBlock(raw, opt.anchorTag, (block) => opt.replacer(block, { toUpdatedStamp: toStamp }));
        } else {
            logger.warn(`[${filename}] Unknown operation type or missing parameters:`, opt);
            continue;
        }

        if (res.changed) {
            changedAny = true;
            raw = res.src;
            logger.log(`[${filename}] Updated (${opt.updatedLabel ?? opt.anchorTag})`);
        } else {
            // logger.log(`[${filename}] No update needed (${op['updatedLabel'] ?? op.anchorTag})`);
        }
    }

    if (changedAny) {
        await fsp.writeFile(filePath, raw, 'utf8');
    }
    return { changed: changedAny, content: raw };
}
