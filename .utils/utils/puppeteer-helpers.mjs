// utils/puppeteer-helpers.mjs

import puppeteer from 'puppeteer';
import { sleep } from './async.mjs';

const REGEX_ID_FOR_PAGE_EVALUATE = ':RegExp:';
const FUNCTION_ID_FOR_PAGE_EVALUATE = ':Function:';

/**
 * @param {RegExp} regex
 * @returns {string}
 */
function encodeRegexTag(regex) {
    const flags = regex.flags.split('').sort().join('');
    return `${REGEX_ID_FOR_PAGE_EVALUATE}${flags ? `${flags}:` : ''}${regex.source}`;
}

/**
 * @param {FindTargetRowsOptions} options
 * @returns {FindTargetRowsOptionsForPageEvaluate}
 */
function toEncodedRegexTagOptions(options) {
    /**
     * @param {Object} obj
     * @returns {Object}
     */
    function traverse(obj) {
        if (!obj) return obj;
        if (typeof obj !== 'object' && typeof obj !== 'function') return obj;

        for (const key in obj) {
            const value = obj[key];

            if (value instanceof RegExp) {
                obj[key] = encodeRegexTag(value);
            } else if (typeof value === 'function') {
                obj[key] = `${FUNCTION_ID_FOR_PAGE_EVALUATE}${value.toString()}`;
            } else if (Array.isArray(value)) {
                obj[key] = value.map(item => {
                    if (item instanceof RegExp) {
                        return encodeRegexTag(item);
                    }
                    if (typeof item === 'function') {
                        return `${FUNCTION_ID_FOR_PAGE_EVALUATE}${item.toString()}`;
                    }
                    return traverse(item);
                });
            } else if (typeof value === 'object') {
                traverse(value);
            }
        }
        return obj;
    }

    return structuredClone(traverse(options));
}

/**
 * Try to click the "Expand" control before searching for the table.
 * zh-CN: 在查找表格前尝试点击 "展开" 控件.
 *
 * @param {import('puppeteer').Page} page
 * @param {FindTargetRowsOptions & PuppeteerOptions} options
 * @returns {Promise<number>} Actual number of clicks. (zh-CN: 实际点击次数.)
 */
async function expandContentIfNeeded(page, options) {
    const configs = options.expandBeforeFinding;
    if (!configs) return 0;

    /** @type {ExpandBeforeFindingOptions[]} */
    const list = Array.isArray(configs) ? configs : [ configs ];

    let totalClicked = 0;
    for (const cfg of list) {
        if (!cfg || !cfg.toggleSelector) continue;
        const {
            containerSelector,
            toggleSelector,
            expandedAttr = 'aria-expanded',
            expandedValue = 'true',
            maxClicks = Infinity,
        } = cfg;

        const containerHandle = containerSelector ? await page.$(containerSelector) : null;
        const toggles = containerHandle
            ? await containerHandle.$$(toggleSelector)
            : await page.$$(toggleSelector);

        let clicked = 0;
        for (const toggle of toggles) {
            if (clicked >= maxClicks) break;
            const isExpanded = await toggle.evaluate((el, attr, val) => {
                const cur = el.getAttribute(attr);
                return cur === val;
            }, expandedAttr, expandedValue);
            if (!isExpanded) {
                try {
                    await toggle.click({ delay: 10 });
                    clicked++;
                    totalClicked++;
                    await sleep(200);
                } catch {
                    /* Ignored. */
                }
            }
        }
        if (containerHandle) {
            try {
                await containerHandle.dispose();
            } catch {
                /* Ignored. */
            }
        }
    }
    return totalClicked;
}

/**
 * @param {Page} page
 * @returns {Promise<void>}
 */
export async function autoScroll(page) {
    await page.evaluate(async () => {
        await new Promise(resolve => {
            let total = 0;
            const step = 400;
            const timer = setInterval(() => {
                window.scrollBy(0, step);
                total += step;
                if (total >= document.body.scrollHeight) {
                    clearInterval(timer);
                    resolve();
                }
            }, 100);
        });
    });
}

/**
 * @param {Page} page
 * @param {FindTargetRowsOptions} [options={}]
 * @returns {Promise<Array<{ [dataItemName: string]: (string | null) }>>}
 */
async function findTargetRowsWithPage(page, options = {}) {
    /** @type {FindTargetRowsOptionsForPageEvaluate} */
    const opts = toEncodedRegexTagOptions(options);
    return await page.evaluate((options, consts) => {
        const regex = new RegExp(consts.regexId + String.raw`(?:(\w+):)?(.+)`);
        const targets = Array.from(document.querySelectorAll(options.tableSelector ?? 'table'));
        const filteredTargets = targets.filter(t => {
            for (const [ selector, filter ] of Object.entries(options.tableFilter ?? {})) {
                const elements = Array.from(t.querySelectorAll(selector));
                if (Array.isArray(filter)) {
                    if (!filter.some(f => elements.some(e => {
                        if (typeof f === 'string') {
                            if (f.startsWith(consts.regexId)) {
                                const [ _, flags, pattern ] = f.match(regex);
                                const re = new RegExp(pattern, flags);
                                return re.test(e.textContent.trim());
                            }
                            if (f.startsWith(consts.functionId)) {
                                const src = f.replace(consts.functionId, '');
                                const fn = new Function(`return ${src}`)();
                                return Boolean(fn(e.textContent.trim()));
                            }
                            return e.textContent.trim() === f;
                        }
                        throw TypeError(`Unknown type of filter (${f})`);
                    }))) {
                        return false;
                    }
                } else if (!elements.some(e => {
                    if (typeof filter !== 'string') {
                        throw TypeError(`Unknown type of filter (${filter})`);
                    }
                    if (filter.startsWith(consts.regexId)) {
                        const [ _, flags, pattern ] = filter.match(regex);
                        const re = new RegExp(pattern, flags);
                        return re.test(e.textContent.trim());
                    }
                    if (filter.startsWith(consts.functionId)) {
                        const src = filter.replace(consts.functionId, '');
                        const fn = new Function(`return ${src}`)();
                        return Boolean(fn(e.textContent.trim()));
                    }
                    return e.textContent.trim() === filter;
                })) {
                    return false;
                }
            }
            return true;
        });
        if (!filteredTargets.length) {
            throw new Error('No target table found');
        }

        const tableRows = filteredTargets.map(target => Array.from(target.querySelectorAll(options.tableRowSelector ?? 'tbody tr'))).flat(1);
        const tableDataList = [];
        tableRows.forEach((tr) => {
            const tableData = {};
            const tds = Array.from(tr.querySelectorAll(options.tableDataSelector ?? 'td'));
            const tableDataStructure = options.tableDataStructure ?? [];
            if (tds.length === 0 || tableDataStructure.length === 0) return null;
            if (tableDataStructure.length > tds.length) {
                throw new Error(`Table data size (${tds.length}) is less than table data structure (${tableDataStructure.length})`);
            }
            for (let i = 0; i < tableDataStructure.length; i++) {
                const o = tableDataStructure[i];
                let dataItemName = null;
                let dataItemChecker = null;
                if (typeof o === 'string') {
                    dataItemName = o;
                } else if (typeof o === 'object' && o !== null) {
                    if (Object.keys(o).length !== 1) {
                        throw new Error(`Table data structure (${tableDataStructure}) must be a string or an object with only one key`);
                    }
                    dataItemName = Object.keys(o)[0];
                    dataItemChecker = o[dataItemName];
                } else {
                    throw new Error(`Unknown type of table data structure (${o})`);
                }
                const dataItemValueRaw = tds[i].textContent.trim();
                if (!dataItemChecker) {
                    tableData[dataItemName] = dataItemValueRaw;
                    continue;
                }
                if (typeof dataItemChecker === 'string') {
                    if (dataItemChecker.startsWith(consts.regexId)) {
                        const [ _, flags, pattern ] = dataItemChecker.match(regex);
                        const re = new RegExp(pattern, flags);
                        tableData[dataItemName] = dataItemValueRaw.match(re)?.[0] ?? null;
                        continue;
                    }
                    if (dataItemChecker.startsWith(consts.functionId)) {
                        const src = dataItemChecker.replace(consts.functionId, '');
                        const fn = new Function(`return ${src}`)();
                        const result = fn(dataItemValueRaw);
                        if (typeof result === 'boolean') {
                            tableData[dataItemName] = result ? dataItemValueRaw : null;
                            continue;
                        }
                        if (typeof result === 'string') {
                            tableData[dataItemName] = result;
                            continue;
                        }
                        throw new Error(`Function ${fn.name} must return a boolean or a string`);
                    }
                }
                throw new Error(`Unknown type of data item checker (${dataItemChecker})`);
            }
            tableDataList.push(tableData);
        });
        return tableDataList;
    }, opts, {
        regexId: REGEX_ID_FOR_PAGE_EVALUATE,
        functionId: FUNCTION_ID_FOR_PAGE_EVALUATE,
    });
}

/**
 * @param {FindTargetRowsOptions & PuppeteerOptions} options
 * @returns {Promise<Array<{ [dataItemName: string]: (string | null) }>>}
 */
export async function findTargetRows(options) {
    const browser = await puppeteer.launch({ headless: true });
    const page = await browser.newPage();
    try {
        await page.goto(options.url, { waitUntil: 'networkidle0', timeout: options.pageGoToTimeout ?? 120000 });
        let rows = null;
        const deadline = Date.now() + (options.findTargetRowsTimeout ?? 30000);
        while (Date.now() < deadline) {
            await expandContentIfNeeded(page, options);

            rows = await findTargetRowsWithPage(page, options);
            if (rows && rows.length) break;

            await autoScroll(page);
            await sleep(300);
        }
        if (rows && rows.length > 0) {
            return rows;
        }
        throw new Error('Unable to locate target table rows (lazy-loaded content not found in time)');
    } finally {
        await browser.close();
    }
}
