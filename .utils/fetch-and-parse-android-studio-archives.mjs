import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer';
import { sleep } from './utils/async.mjs';
import { compareVersionStrings, isVersionStable } from './utils/versioning.mjs';
import { readPropertiesSync } from './utils/properties.mjs';

/** @typedef {import('puppeteer').Page} Page */
/** @typedef {import('puppeteer').Frame} Frame */
/**
 * @template {Node} T
 * @typedef {import('puppeteer').ElementHandle<T>} ElementHandle
 */
/**
 * @typedef {Object} ArchiveItem
 * @property {string} title
 * @property {string} date
 * @property {string | null} version
 * @property {{ text: string, href: string }[]} links
 * @property {{ [filename: string]: string }} checksums
 */

const URL = 'https://developer.android.com/studio/archive?hl=en';

/**
 * 在所有 frame (含主文档) 中查找 "同意" 按钮.
 *
 * @param {Page} page
 * @param {number} [timeoutMs=30000]
 * @returns {Promise<{handle: ElementHandle<HTMLButtonElement>, frame: Frame}>}
 */
async function waitAndFindAgreeButton(page, timeoutMs = 30000) {
    const deadline = Date.now() + timeoutMs;
    const selector = 'button.button-primary';

    while (Date.now() < deadline) {

        // 1) 先尝试主文档

        const mainBtn = await page.$$(selector);
        for (const h of mainBtn) {
            const txt = await page.evaluate(el => (el.textContent || '').trim().toLowerCase(), h);
            if (txt.includes('agree')) return { handle: h, frame: page.mainFrame() };
        }

        // 2) 再查所有子 frame

        const frames = page.frames();
        for (const f of frames) {
            /** @type {ElementHandle<HTMLButtonElement>} */
            const btn = await f.$(selector);
            if (!btn) continue;
            const txt = await f.evaluate(el => (el.textContent || '').trim().toLowerCase(), btn);
            if (txt.includes('i agree') || txt.includes('agree to the terms') || txt === 'agree') {
                return { handle: btn, frame: f };
            }
        }

        // 3) 触发懒加载: 轻微滚动几次

        await page.evaluate(() => window.scrollBy(0, 600));
        await sleep(250);
    }
    throw new Error('未在任何文档中找到 "同意" 按钮 (超时)');
}

/**
 * 在所有 frame 中等待某个选择器出现, 并返回该 frame.
 *
 * @param {Page} page
 * @param {string} selector
 * @param [timeoutMs=30000]
 * @returns {Promise<Frame>}
 */
async function waitForFrameWithSelector(page, selector, timeoutMs = 30000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        for (const f of page.frames()) {
            const el = await f.$(selector);
            if (el) return f;
        }
        await sleep(250);
    }
    throw new Error(` 未在任何 frame 中找到选择器: ${selector}`);
}

/**
 * @return {Promise<ArchiveItem[]>}
 */
export async function getAndroidStudioArchives() {
    const browser = await puppeteer.launch({
        headless: true,
        args: [
            '--no-sandbox',
            '--disable-setuid-sandbox',
        ],
    });

    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36');
    await page.goto(URL, { waitUntil: 'networkidle2', timeout: 60000 });

    // 滚动到下载区域, 促发懒加载 (有助于注入承载 "同意" 按钮的 iframe)
    await page.evaluate(() => {
        const anchor = document.querySelector('#downloads')
            || Array.from(document.querySelectorAll('h2, h3'))
                .find(h => /download|archive/i.test(h.textContent || ''));
        if (anchor) anchor.scrollIntoView({ behavior: 'instant', block: 'start' });
    });
    await sleep(500);

    // 等待并点击 "同意" 按钮
    try {
        const { handle, frame } = await waitAndFindAgreeButton(page, 30000);
        await frame.waitForSelector('button.button-primary', { visible: true, timeout: 15000 }).catch(() => {
        });
        await handle.click();
    } catch (e) {
        console.log('未检测到协议或已同意, 继续解析...');
    }

    // 同意后不要在主文档等待; 改为在包含内容的 frame 里等待 devsite-expandable
    // 若首次未出现, 尝试轻微滚动以触发懒加载, 再次检查
    /** @type {Frame} */
    let contentFrame;
    try {
        contentFrame = await waitForFrameWithSelector(page, 'devsite-expandable', 20000);
    } catch {
        // 尝试滚动触发
        for (let i = 0; i < 8; i++) {
            await page.evaluate(() => window.scrollBy(0, 800));
            await sleep(250);
        }
        // 再次寻找
        contentFrame = await waitForFrameWithSelector(page, 'devsite-expandable', 20000);
    }

    /**
     * @type {ArchiveItem[]}
     */
    const archives = await contentFrame.$$eval('devsite-expandable', nodes => {

        // 从内容 frame 中直接抽取 devsite-expandable 数据

        /**
         * @param {Node | null} el
         * @returns {string}
         */
        const pickText = el => (el?.textContent || '').trim();

        return nodes.map(n => {
            /** @type {Node} */
            const titleEl = n.querySelector('.expand-control');
            const title = pickText(titleEl?.childNodes?.[0]); // 不含日期的主标题
            const date = pickText(n.querySelector('.expand-control span'))
                .replace(/^([A-Z][a-z]{2})[a-z]*( \d+, \d+)$/, '$1$2');
            /** @type {Element[]} */
            const linkEls = Array.from(n.querySelectorAll('.downloads a[href]'));
            const links = linkEls.map(a => ({
                text: pickText(a),
                href: a.getAttribute('href') || '',
            }));

            // 收集 checksums (在 .downloads 文本中)
            /** @type {HTMLElement} */
            const downloadsElement = n.querySelector('.downloads');
            const bodyText = (downloadsElement?.innerText || '').trim();
            /** @type {{[filename: string]: string}} */
            const checksums = {};
            // 行格式: <sha256> <filename>
            bodyText.split('\n').forEach(line => {
                const m = /^\s*([a-f0-9]{64})\s+(.+?)\s*$/.exec(line);
                if (m) {
                    const [ _, sha256, filename ] = m;
                    checksums[filename] = sha256;
                }
            });

            // 解析版本号 (2025.1.2 等), 优先从标题中提取
            let version = null;
            const vm = title.match(/\d{2,}\.\d+(?:\.\d+)?/);
            if (vm) version = vm[0];

            return { title, date, version, links, checksums };
        });
    });

    await browser.close();

    return archives;
}

async function main() {
    const props = readPropertiesSync();
    const minSupportedAndroidStudioVersion = props['MIN_SUPPORTED_ANDROID_STUDIO_IDE_VERSION'];
    const getVersionFromTitle = (/** @type {string} */ title) => title.split('|')[1].trim();
    const archives = await getAndroidStudioArchives();
    const results = archives.filter(archive => {
        if (!archive.title.includes('|')) return false;
        return compareVersionStrings(archive.version ?? '0', minSupportedAndroidStudioVersion) >= 0;
    }).map(({ title, date, version, links }) => {
        const windowsZipUrl = links.find(link => link.text.match(/-windows(-exe)?\.zip/i))?.href;
        if (!windowsZipUrl) {
            console.log('Unable to find Windows zip link for:');
            console.log(links.map(link => link.text).join('\n'));
        }
        const stable = isVersionStable(getVersionFromTitle(title)) || '-';
        return { title, date, version, stable, 'link for Windows (zip)': windowsZipUrl };
    }).sort((a, b) => {
        return compareVersionStrings(getVersionFromTitle(b.title), getVersionFromTitle(a.title));
    });
    console.table(results);
}

// 判断是否为直接执行该文件
if (fileURLToPath(import.meta.url) === process.argv[1]) {
    await main().catch((err) => {
        console.error(err);
        process.exitCode = 1;
    });
}