// utils/puppeteer-helpers.mjs

/** @typedef {import('puppeteer').Page} Page */

import puppeteer from 'puppeteer';
import { sleep } from './async.mjs';

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
 * @typedef {object} FindTargetRowsOptions
 * @property {string} [tableSelector='table']
 * @property {{ [selector: string]: string | string[] }}[tableFilter={}]
 * @property {string} [tableRowSelector='tbody tr']
 * @property {string} [tableDataSelector='td']
 * @property {Array<{ [dataItemName: string]: string } | string>} [tableDataStructure=[]]
 */
/**
 * @param {Page} page
 * @param {FindTargetRowsOptions} [options={}]
 * @returns {Promise<Array<{ [dataItemName: string]: (string | null) }>>}
 */
async function findTargetRowsWithPage(page, options = {}) {
    return await page.evaluate((options) => {
        const targets = Array.from(document.querySelectorAll(options.tableSelector ?? 'table'));
        const target = targets.find(t => {
            for (const [ selector, filter ] of Object.entries(options.tableFilter ?? {})) {
                const elements = Array.from(t.querySelectorAll(selector));
                if (Array.isArray(filter)) {
                    if (!filter.some(f => elements.some(e => {
                        if (typeof f === 'string') {
                            if (!f.startsWith(':RegExp:')) {
                                return e.textContent.trim() === f;
                            }
                            const [ _, flags, pattern ] = f.match(/:RegExp:(?:(\w+):)?(.+)/);
                            const re = new RegExp(pattern, flags);
                            return re.test(e.textContent.trim());
                        }
                        throw TypeError(`Unknown type of filter (${f})`);
                    }))) {
                        return false;
                    }
                } else {
                    if (!elements.some(e => {
                        if (typeof filter === 'string') {
                            if (!filter.startsWith(':RegExp:')) {
                                return e.textContent.trim() === filter;
                            }
                            const [ _, flags, pattern ] = filter.match(/:RegExp:(?:(\w+):)?(.+)/);
                            const re = new RegExp(pattern, flags);
                            return re.test(e.textContent.trim());
                        }
                        throw TypeError(`Unknown type of filter (${filter})`);
                    })) {
                        return false;
                    }
                }
            }
            return true;
        });
        if (!target) {
            throw Error('No target table found');
        }

        const tableRows = Array.from(target.querySelectorAll(options.tableRowSelector ?? 'tbody tr'));
        const tableDataList = [];
        tableRows.forEach((tr) => {
            const tableData = {};
            const tds = Array.from(tr.querySelectorAll(options.tableDataSelector ?? 'td'));
            const tableDataStructure = options.tableDataStructure ?? [];
            if (tds.length === 0 || tableDataStructure.length === 0) return null;
            if (options.tableDataStructure.length > tds.length) {
                throw Error(`Table data size (${tds.length}) is less than table data structure (${options.tableDataStructure.length})`);
            }
            for (let i = 0; i < options.tableDataStructure.length; i++) {
                const o = options.tableDataStructure[i];
                let dataItemName = null;
                let dataItemFilter = null;
                if (typeof o === 'string') {
                    dataItemName = o;
                } else if (typeof o === 'object' && o !== null) {
                    if (Object.keys(o).length !== 1) {
                        throw Error(`Table data structure (${options.tableDataStructure}) must be a string or an object with only one key`);
                    }
                    dataItemName = Object.keys(o)[0];
                    dataItemFilter = o[dataItemName];
                } else {
                    throw Error(`Unknown type of table data structure (${options.tableDataStructure})`);
                }
                const dataItemValueRaw = tds[i].textContent.trim();
                if (dataItemFilter == null) {
                    tableData[dataItemName] = dataItemValueRaw;
                } else if (typeof dataItemFilter === 'string') {
                    if (!dataItemFilter.startsWith(':RegExp:')) {
                        tableData[dataItemName] = dataItemValueRaw === dataItemFilter ? dataItemValueRaw : null;
                    } else {
                        const [ _, flags, pattern ] = dataItemFilter.match(/:RegExp:(?:(\w+):)?(.+)/);
                        const re = new RegExp(pattern, flags);
                        tableData[dataItemName] = dataItemValueRaw.match(re)?.[0] ?? null;
                    }
                }
            }
            tableDataList.push(tableData);
        });
        return tableDataList;
    }, options);
}

/**
 * @typedef {object} PuppeteerOptions
 * @property {string} url
 * @property {number} [pageGoToTimeout=120000]
 * @property {number} [findTargetRowsTimeout=30000]
 */
/**
 * @param {FindTargetRowsOptions & PuppeteerOptions} options
 * @returns {Promise<Array<{[dataItemName: string]: string | null}>>}
 */
export async function findTargetRows(options) {
    const browser = await puppeteer.launch({ headless: true });
    const page = await browser.newPage();
    try {
        await page.goto(options.url, { waitUntil: 'networkidle0', timeout: options.pageGoToTimeout ?? 120000 });

        // 页面为懒加载: 滚动并多次尝试, 直到目标表格出现或超时
        let rows = null;
        const deadline = Date.now() + (options.findTargetRowsTimeout ?? 30000);
        while (Date.now() < deadline) {
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