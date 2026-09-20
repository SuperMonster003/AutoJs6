/**
 * 导出章节文本 [v6.8.0+]
 *
 * epub 模块由 Readium EPUB Reader 插件提供, 需先在插件中心安装并启用.
 * 本示例列出目录供选择, 把所选章节的文本 (或全书文本) 导出为 .txt, 同时把封面另存为图片, 并演示 search 与 Async 形态.
 * 输出目录: 内部存储的 AutoJs6/epub/.
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

let outDir = files.join(files.getSdcardPath(), 'AutoJs6', 'epub');
files.ensureDir(outDir + '/');

let book = epub.open(path);
let title = book.metadata.title || files.getNameWithoutExtension(path);

/* 1. 把目录树摊平成列表供选择, 末尾追加 "全书" 一项. */
let entries = [];
flatten(book.toc, 0, entries);
let labels = entries.map((e) => '  '.repeat(e.depth) + e.title).concat(['[ 全书 ]']);
let index = dialogs.select('选择要导出的章节', labels);
if (index < 0) {
    book.close();
    exit();
}

/* 2. text(href, options) 取单个资源的文本, textAll(options) 按阅读顺序拼接全书; maxChars 限制字符数. */
let text;
let fileName;
if (index === entries.length) {
    text = book.textAll({ maxChars: 2 * 1024 * 1024 });
    fileName = safeName(title) + '.txt';
} else {
    let entry = entries[index];
    text = book.text(entry.href, { format: 'text' });
    fileName = safeName(title + ' - ' + entry.title) + '.txt';
}
let target = files.join(outDir, fileName);
files.write(target, text);
console.log('已导出', text.length, '字符到', target);

/* 3. 封面: 无参返回 Image, 传路径则直接保存 (已存在时需要 overwrite). 没有封面时 code 为 RESOURCE_NOT_FOUND. */
try {
    let coverPath = book.cover(outDir, { overwrite: true }); // 传目录时按封面资源的文件名保存.
    console.log('封面已保存到', coverPath);
} catch (e) {
    if (e instanceof epub.EpubError && e.code === 'RESOURCE_NOT_FOUND') {
        console.log('这本书没有封面');
    } else {
        throw e;
    }
}

/* 4. 全文搜索: 返回 { href, title, text, locator } 数组, limit 默认 50, 最多 500. */
let keyword = dialogs.rawInput('输入要搜索的词 (留空跳过)', '');
if (keyword) {
    let hits = book.search(keyword, { limit: 10 });
    console.log('"' + keyword + '" 命中', hits.length, '处 (最多显示 10 处)');
    hits.forEach((hit, i) => console.log('  ' + (i + 1) + '. ' + (hit.title || hit.href) + ': ' + hit.text));
}

book.close();

/* 5. Async 形态返回 Promise, 回调在脚本线程执行; 便捷层每次调用自行打开与关闭书籍. */
epub.readingOrderAsync(path).then((links) => {
    console.log('阅读顺序 (异步):', links.length, '个资源');
}).catch((e) => console.error(String(e)));

function flatten(nodes, depth, out) {
    nodes.forEach((node) => {
        if (node.href) out.push({ title: node.title || node.href, href: node.href, depth: depth });
        if (node.children) flatten(node.children, depth + 1, out);
    });
}

function safeName(name) {
    return name.replace(/[\\/:*?"<>|]+/g, '_').trim().slice(0, 80);
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
