/**
 * EPUB 元数据与目录 [v6.8.0+]
 *
 * epub 模块由 Readium EPUB Reader 插件提供, 需先在插件中心安装并启用.
 * 本示例打开一本 EPUB, 打印元数据, 目录树, 阅读顺序与位置数, 并演示便捷层 epub.metadata(path) 与 EpubError.
 * 书籍路径: 先在 Download 与 Books 目录寻找第一本 .epub, 找不到时弹出输入框.
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

/* 1. epub.open 返回 EpubBook, 用完需要 close (脚本退出时也会自动关闭). */
let book;
try {
    book = epub.open(path);
} catch (e) {
    if (e instanceof epub.EpubError) {
        /* code 为 FILE_NOT_FOUND / NOT_EPUB / PARSE_FAILED / ENCRYPTED 等. */
        toastLog('打开失败 [' + e.code + ']: ' + e.message);
        exit();
    }
    throw e;
}

/* 2. 元数据是普通对象, 字段按书籍内容而定. */
let meta = book.metadata;
console.log('标题:', meta.title);
console.log('作者:', (meta.authors || []).join(', ') || '(未知)');
console.log('语言:', meta.language || '(未知)');
console.log('标识符:', meta.identifier || '(无)');
console.log('出版方:', meta.publisher || '(无)');
console.log('封面资源:', meta.cover || '(无)');

/* 3. 目录是 { title, href, children } 树, 按层级缩进打印. */
console.log('目录:');
printToc(book.toc, 0);

/* 4. 阅读顺序与位置数. */
console.log('阅读顺序:', book.readingOrder.length, '个资源');
book.readingOrder.slice(0, 5).forEach((link, i) => console.log('  [' + i + ']', link.href, link.type || ''));
console.log('位置数 (约等于页数):', book.positions);

book.close();

/* 5. 便捷层: 只取元数据时不必手动打开与关闭. */
console.log('便捷层再次读取标题:', epub.metadata(path).title);

function printToc(entries, depth) {
    entries.forEach((entry) => {
        console.log('  '.repeat(depth) + '- ' + (entry.title || '(无标题)') + '  ' + entry.href);
        if (entry.children && entry.children.length > 0) {
            printToc(entry.children, depth + 1);
        }
    });
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
