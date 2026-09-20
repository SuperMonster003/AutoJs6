/**
 * 打开阅读器并监听进度 [v6.8.0+]
 *
 * epub 模块由 Readium EPUB Reader 插件提供, 需先在插件中心安装并启用.
 * epub.read 打开插件的阅读器并返回 EpubReaderSession (EventEmitter): 事件在脚本线程到达, 会话打开期间脚本保持运行.
 * 本示例从目录的第二章开始阅读, 打印 open / progress / bookmark / error 事件, 在阅读器里翻页或添加书签即可看到输出;
 * 关闭阅读器 (或 60 秒后脚本自动 close) 触发 close 事件, 脚本随即结束.
 */

if (!epub.isAvailable()) {
    toastLog('Readium EPUB Reader 插件不可用, 请在插件中心安装或启用后重试');
    exit();
}

let path = findFirstEpub([
    files.join(files.getSdcardPath(), 'Download'),
    files.join(files.getSdcardPath(), 'Books'),
]) || dialogs.rawInput('输入 EPUB 文件路径');
if (!path) exit();

/* 1. 起始位置可用 href, progression (0 到 1) 或 locator 之一指定; preferences 为阅读偏好的子集. */
let toc = epub.toc(path);
let start = toc.length > 1 ? { href: toc[1].href } : {};
let session = epub.read(path, Object.assign(start, { preferences: { theme: 'sepia', fontSize: 1.1 } }));
console.log(String(session)); // EpubReaderSession { path: ..., state: pending }

/* 2. open: 阅读器可见. 之后才能调用 goTo / next / setPreferences 等方法. */
session.on('open', (e) => {
    console.log('已打开:', e.title, '| 起始章节:', e.href, '| 位置数:', e.positions);
    console.log('当前进度:', percent(session.progress));
});

/* 3. progress: 翻页或跳转后 (节流 500 毫秒) 到达, 带 locator, 总进度与章节标题. */
session.on('progress', (e) => {
    console.log('进度', percent(e.totalProgression), '|', e.chapterTitle || e.href);
});

/* 4. bookmark: 在阅读器里添加或删除书签. */
session.on('bookmark', (e) => {
    console.log('书签', e.action === 'added' ? '添加' : '移除', '于', e.locator.href);
    console.log('书签总数:', session.bookmarks().length);
});

/* 5. error: 非致命错误 (如未知的偏好键 UNSUPPORTED_PREFERENCE), 会话继续. */
session.on('error', (e) => console.warn('阅读器错误 [' + e.code + ']: ' + e.message));

/* 6. close: 唯一的终结事件, reason 为 user (用户关闭) / host (脚本 close) / replaced / timeout / error 等. */
session.on('close', (e) => {
    console.log('会话结束:', e.reason, '| 最后进度:', percent(session.progress));
});

/* 7. 30 秒后翻到下一章演示控制方法; 60 秒后结束会话 (不传 finish 时阅读器留给用户继续阅读). */
setTimeout(() => {
    if (session.isOpen) {
        session.nextChapter();
        toastLog('已翻到下一章');
    }
}, 30 * 1000);
setTimeout(() => {
    if (session.isOpen) {
        session.close();
    }
}, 60 * 1000);

function percent(value) {
    return typeof value === 'number' ? (value * 100).toFixed(1) + '%' : '-';
}

function findFirstEpub(dirs) {
    for (let i = 0; i < dirs.length; i += 1) {
        let dir = dirs[i];
        if (!files.isDir(dir)) continue;
        let names = files.listDir(dir, (name) => name.toLowerCase().endsWith('.epub'));
        if (names.length > 0) return files.join(dir, names[0]);
    }
    return null;
}
