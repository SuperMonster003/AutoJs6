// fetch-and-parse-android-studio-archives.mjs

import puppeteer from 'puppeteer';
import { compareVersionStrings, isVersionStable } from './utils/versioning.mjs';
import { fileURLToPath } from 'node:url';
import { readPropertiesSync } from './utils/properties.mjs';
import { sleep } from './utils/async.mjs';

const URL = 'https://developer.android.com/studio/archive?hl=en';
const SELECTOR_PRIMARY_BUTTON = 'button.button-primary';
const SELECTOR_DEVSITE_EXPANDABLE = 'devsite-expandable';

/**
 * Find "agree" button in frame (including main document).<br>
 * zh-CN: 在所有 frame (含主文档) 中查找 "同意" 按钮.
 *
 * @param {Page} page
 * @param {number} [timeoutMs=30000]
 * @returns {Promise<{ handle: ElementHandle<HTMLButtonElement>, frame: Frame }>}
 */
async function waitAndFindAgreeButton(page, timeoutMs = 30000) {
    /**
     * @param {string | null} s
     * @returns {boolean}
     */
    const containsAgreementText = (s) => s && /\b(?:i\s*agree|agree\s*to\s*the\s*terms|^agree$)/i.test(s.trim());

    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {

        // Attempt main document.
        // zh-CN: 尝试主文档.

        const btnList = await page.$$(SELECTOR_PRIMARY_BUTTON);
        for (const btn of btnList) {
            const txt = await page.evaluate(el => el.textContent, btn);
            if (containsAgreementText(txt)) {
                return { handle: btn, frame: page.mainFrame() };
            }
        }

        // Check all sub-frame.
        // zh-CN: 检查所有子 frame.

        const frames = page.frames();
        for (const f of frames) {
            /** @type {ElementHandle<HTMLButtonElement>} */
            const btn = await f.$(SELECTOR_PRIMARY_BUTTON);
            if (!btn) continue;
            const txt = await f.evaluate(el => el.textContent, btn);
            if (containsAgreementText(txt)) {
                return { handle: btn, frame: f };
            }
        }

        // Trigger lazy-loading: scroll slightly several times.
        // zh-CN: 触发懒加载, 轻微滚动几次.

        await page.evaluate(() => window.scrollBy(0, 600));
        await sleep(300);
    }
    throw new Error('Unable to find "agree" button in any document (timeout)');
}

/**
 * Wait for a selector to appear in any frame and return that frame.<br>
 * zh-CN: 在所有 frame 中等待某个选择器出现, 并返回该 frame.
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
            if (await f.$(selector)) {
                return f;
            }
        }
        await sleep(300);
    }
    throw new Error(`Could not find selector "${selector}" in any frame`);
}

/**
 * @returns {Promise<AndroidStudioArchiveItem[]>}
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

    // Scroll to download area, trigger lazy loading (helpful for injecting iframe containing "agree" button).
    // zh-CN: 滚动到下载区域, 触发懒加载 (有助于注入承载 "同意" 按钮的 iframe).
    await page.evaluate(() => {
        const anchor = document.querySelector('#downloads')
            || Array.from(document.querySelectorAll('h2, h3'))
                .find(h => /download|archive/i.test(h.textContent ?? ''));
        if (anchor) anchor.scrollIntoView({ behavior: 'instant', block: 'start' });
    });
    await sleep(300);

    try {
        const { handle, frame } = await waitAndFindAgreeButton(page, 30000);
        await frame.waitForSelector(SELECTOR_PRIMARY_BUTTON, { visible: true, timeout: 15000 }).catch(_ => null);
        await handle.click();
    } catch (e) {
        console.log('No protocol detected or already agreed, continuing parsing...');
    }

    // Don't wait in main document after agreeing; wait for devsite-expandable in content frame instead.
    // If not found initially, try slightly scrolling to trigger lazy loading and check again.
    // zh-CN:
    // 同意后不要在主文档等待; 改为在包含内容的 frame 里等待 devsite-expandable.
    // 若首次未出现, 尝试轻微滚动以触发懒加载, 再次检查.

    /** @type {Frame} */
    let contentFrame;
    try {
        contentFrame = await waitForFrameWithSelector(page, SELECTOR_DEVSITE_EXPANDABLE, 20000);
    } catch {
        // Attempt to trigger loading by scrolling. (zh-CN: 尝试滚动触发.)
        for (let i = 0; i < 8; i++) {
            await page.evaluate(() => window.scrollBy(0, 800));
            await sleep(300);
        }
        // Check again. (zh-CN: 再次检查.)
        contentFrame = await waitForFrameWithSelector(page, SELECTOR_DEVSITE_EXPANDABLE, 20000);
    }
    if (!contentFrame) {
        throw new Error('Failed to find content frame');
    }

    /** @type {AndroidStudioArchiveItem[]} */
    const archives = await contentFrame.$$eval(SELECTOR_DEVSITE_EXPANDABLE, nodes => {
        const pickText = (/** @type {Node | null} */ el) => String(el?.textContent ?? '').trim();
        return nodes.map(n => {
            /** @type {Node} */
            const titleElement = n.querySelector('.expand-control');
            const title = pickText(titleElement?.childNodes?.[0] ?? null);
            const date = pickText(n.querySelector('.expand-control span'))
                .replace(/^([A-Z][a-z]{2})[a-z]*( \d+, \d+)$/, '$1$2');
            /** @type {Element[]} */
            const linkElements = Array.from(n.querySelectorAll('.downloads a[href]'));
            const links = linkElements.map(a => ({
                text: pickText(a),
                href: a.getAttribute('href') ?? '',
            }));

            /** @type {{ [filename: string]: string }} */
            const checksums = {};
            /** @type {HTMLElement} */
            const downloadsElement = n.querySelector('.downloads');
            const bodyText = (downloadsElement?.innerText || '').trim();
            bodyText.split('\n').forEach(line => {
                const m = /^\s*([a-f0-9]{64})\s+(.+?)\s*$/.exec(line);
                if (m) {
                    const [ _, sha256, filename ] = m;
                    checksums[filename] = sha256;
                }
            });

            /* e.g. "2025.1.3". */
            const version = title.match(/\d{2,}\.\d+(?:\.\d+)?/)?.[0] ?? null;
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

// Determine if this file is being run directly.
// zh-CN: 判断是否为直接执行该文件.
if (fileURLToPath(import.meta.url) === process.argv[1]) {
    await main().catch((err) => {
        console.error(err);
        process.exitCode = 1;
    });
}
