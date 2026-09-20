// Device smoke script for the `epub` global (plugin roadmap P6.1). The test prepends
// `var EPUB_SMOKE = { path, missingPath, workDir, title, author, chapter1, chapter2, heading1,
// heading2, imageHref, searchWord };` (a fixture EPUB written by the test) and reads the JSON
// report from runtime property `epub.smoke.report` once the async chain has settled. Every check
// throws on failure so the report names the first one.

var report = { steps: [], ok: false };

function expect(condition, message) {
    if (!condition) throw new Error('smoke: ' + message);
    report.steps.push(message);
}

function expectEpubError(fn, code) {
    var error = null;
    try {
        fn();
    } catch (e) {
        error = e;
    }
    expect(error !== null, 'expected EpubError ' + code + ' but nothing was thrown');
    expect(error instanceof epub.EpubError, 'expected EpubError ' + code + ', got ' + error);
    expect(error instanceof Error, 'EpubError ' + code + ' extends Error');
    expect(error.code === code, 'expected ' + code + ', got ' + error.code + ' (' + error.message + ')');
    return error;
}

function expectRejection(promise, code) {
    return promise.then(function () {
        throw new Error('smoke: expected rejection ' + code + ' but the promise resolved');
    }, function (e) {
        expect(e instanceof epub.EpubError, 'rejection is an EpubError (' + code + '), got ' + e);
        expect(e.code === code, 'rejection code ' + code + ', got ' + e.code + ' (' + e.message + ')');
        return e;
    });
}

function finish(e) {
    if (e) {
        report.error = String(e) + (e && e.stack ? '\n' + e.stack : '');
        report.errorCode = e && e.code;
    }
    runtime.putProperty('epub.smoke.report', JSON.stringify(report));
}

try {
    expect(typeof epub === 'object' && epub !== null, 'the epub global exists');
    expect($epub === epub, '$epub is the alias of epub');
    expect(typeof epub.EpubError === 'function', 'epub.EpubError is a constructor');
    expect(epub.isAvailable() === true, 'epub.isAvailable() is true with the plugin installed');

    var started = Date.now();
    var book = epub.open(EPUB_SMOKE.path);
    report.openMs = Date.now() - started;
    expect(String(book).indexOf('EpubBook') === 0, 'String(book) names EpubBook');
    expect(book.path === EPUB_SMOKE.path, 'book.path is the opened path');
    expect(book.isClosed === false, 'book.isClosed is false while open');

    var metadata = book.metadata;
    expect(metadata.title === EPUB_SMOKE.title, 'metadata.title matches the fixture');
    expect(metadata.authors.length === 1 && metadata.authors[0] === EPUB_SMOKE.author, 'metadata.authors lists the fixture author');
    expect(metadata.layout === 'reflowable', 'metadata.layout is reflowable');
    expect(typeof metadata.positions === 'number' && metadata.positions > 0, 'metadata.positions is a positive number');
    expect(book.metadata === metadata, 'book.metadata is the same object on every access');
    expect(book.positions === metadata.positions, 'book.positions equals metadata.positions');

    var toc = book.toc;
    expect(Array.isArray(toc) && toc.length >= 2, 'book.toc lists the chapters');
    expect(Array.isArray(toc[0].children), 'toc entries carry a children array');
    expect(toc[0].href === EPUB_SMOKE.chapter1, 'toc[0].href is chapter 1');
    expect(book.toc === toc, 'book.toc is stable');

    var order = book.readingOrder;
    expect(order.length === 2 && order[1].href === EPUB_SMOKE.chapter2, 'book.readingOrder lists the two chapters');

    var chapter1 = book.text(EPUB_SMOKE.chapter1);
    expect(chapter1.indexOf(EPUB_SMOKE.heading1) >= 0, 'text(href) contains the chapter heading');
    var chapter2 = book.text(1);
    expect(chapter2.indexOf(EPUB_SMOKE.heading2) >= 0, 'text(index) reads the second chapter');
    expect(book.text(order[0]) === chapter1, 'text(readingOrder entry) equals text(href)');
    expect(book.text(toc[0]) === chapter1, 'text(toc entry) equals text(href)');
    var head = book.text(EPUB_SMOKE.chapter1, { maxChars: 5 });
    expect(head.length === 5 && chapter1.indexOf(head) === 0, 'maxChars 5 yields the first five characters');
    var tail = book.text(EPUB_SMOKE.chapter1, { offset: 5 });
    expect(head + tail === chapter1, 'offset continues where the window ended');
    expect(book.text(EPUB_SMOKE.chapter1, { offset: 100000 }) === '', 'an offset beyond the end yields an empty string');
    var whole = book.text();
    expect(whole.indexOf(EPUB_SMOKE.heading1) >= 0 && whole.indexOf(EPUB_SMOKE.heading2) >= 0, 'text() without a target reads the whole book');
    expect(whole.indexOf('\n\n') >= 0, 'the whole-book text separates chapters with a blank line');
    expect(book.textAll() === whole, 'textAll() equals text() without a target');
    var markdown = book.textAll({ format: 'markdown' });
    expect(markdown.indexOf('#') >= 0 && markdown !== whole, 'markdown text marks the headings');
    expect(book.text(null, { maxChars: 10 }).length === 10, 'whole-book maxChars caps the total');

    var hits = book.search(EPUB_SMOKE.searchWord);
    expect(hits.length > 0, 'search finds the fixture word');
    expect(hits[0].text.highlight.toLowerCase() === EPUB_SMOKE.searchWord, 'the hit highlights the word');
    expect(typeof hits[0].locator.href === 'string' && typeof hits[0].href === 'string', 'the hit carries href and locator');
    expect(book.search('zzz-no-such-word').length === 0, 'a search without hits is an empty array');
    expect(book.search(EPUB_SMOKE.searchWord, { limit: 1 }).length === 1, 'search limit 1 yields one hit');

    var exported = book.resource(EPUB_SMOKE.imageHref, EPUB_SMOKE.workDir, { overwrite: true });
    expect(files.exists(exported) && exported.indexOf('dot.png') > 0, 'resource() exports into a directory under the resource name');
    expectEpubError(function () { book.resource(EPUB_SMOKE.imageHref, exported); }, 'INVALID_ARGUMENT');
    var renamed = book.resource(EPUB_SMOKE.imageHref, files.join(EPUB_SMOKE.workDir, 'renamed.png'), { overwrite: true });
    expect(files.exists(renamed) && files.read(renamed, 'ISO-8859-1').length > 0, 'resource() exports to an explicit file path');
    if (metadata.cover) {
        var image = book.cover();
        expect(image.getWidth() > 0, 'cover() returns an Image');
        image.recycle();
        var coverPath = book.cover(EPUB_SMOKE.workDir, { overwrite: true });
        expect(files.exists(coverPath), 'cover(outputPath) writes the cover file');
        report.cover = metadata.cover;
    } else {
        expectEpubError(function () { book.cover(); }, 'RESOURCE_NOT_FOUND');
        expectEpubError(function () { book.cover(EPUB_SMOKE.workDir); }, 'RESOURCE_NOT_FOUND');
        report.cover = null;
    }

    expectEpubError(function () { book.text('OEBPS/missing.xhtml'); }, 'RESOURCE_NOT_FOUND');
    expectEpubError(function () { book.text(99); }, 'RESOURCE_NOT_FOUND');
    expectEpubError(function () { book.text(-1); }, 'INVALID_ARGUMENT');
    expectEpubError(function () { book.text(EPUB_SMOKE.chapter1, { limit: 5 }); }, 'INVALID_ARGUMENT');
    expectEpubError(function () { book.text(EPUB_SMOKE.chapter1, { format: 'html' }); }, 'INVALID_ARGUMENT');
    expectEpubError(function () { book.text(1, 2, 3); }, 'INVALID_ARGUMENT');
    expectEpubError(function () { book.search(''); }, 'INVALID_ARGUMENT');
    expectEpubError(function () { book.search('x', { offset: 500 }); }, 'INVALID_ARGUMENT');
    expectEpubError(function () { book.resource('OEBPS/nope.png', EPUB_SMOKE.workDir); }, 'RESOURCE_NOT_FOUND');
    expectEpubError(function () { book.resource(EPUB_SMOKE.imageHref); }, 'INVALID_ARGUMENT');
    expectEpubError(function () { epub.open(EPUB_SMOKE.missingPath); }, 'FILE_NOT_FOUND');
    expectEpubError(function () { epub.open('content://media/external/file/1'); }, 'INVALID_ARGUMENT');
    expectEpubError(function () { epub.open({ path: EPUB_SMOKE.path, name: 'x' }); }, 'INVALID_ARGUMENT');
    expectEpubError(function () { epub.open(); }, 'INVALID_ARGUMENT');
    var notEpub = files.join(EPUB_SMOKE.workDir, 'plain.txt');
    files.write(notEpub, 'not a book');
    expectEpubError(function () { epub.open(notEpub); }, 'NOT_EPUB');

    expect(epub.metadata(EPUB_SMOKE.path).title === metadata.title, 'epub.metadata(path) matches book.metadata');
    expect(epub.toc(EPUB_SMOKE.path).length === toc.length, 'epub.toc(path) matches book.toc');
    expect(epub.readingOrder(EPUB_SMOKE.path).length === order.length, 'epub.readingOrder(path) matches book.readingOrder');
    expect(epub.text(EPUB_SMOKE.path, EPUB_SMOKE.chapter2) === chapter2, 'epub.text(path, href) matches book.text');
    expect(epub.text(EPUB_SMOKE.path) === whole, 'epub.text(path) reads the whole book');
    expect(epub.search(EPUB_SMOKE.path, EPUB_SMOKE.searchWord).length === hits.length, 'epub.search(path, query) matches book.search');
    expect(epub.metadata({ path: EPUB_SMOKE.path, displayName: 'Smoke' }).title === metadata.title, 'epub.metadata accepts an options object');

    book.close();
    expect(book.isClosed === true, 'book.isClosed is true after close');
    book.close();
    expect(String(book).indexOf('closed') > 0, 'close is idempotent and String(book) says closed');
    expectEpubError(function () { book.text(0); }, 'SESSION_CLOSED');
    expect(book.metadata === metadata, 'cached metadata stays readable after close');

    var asyncStarted = Date.now();
    var asyncBook = null;
    epub.openAsync(EPUB_SMOKE.path).then(function (b) {
        asyncBook = b;
        expect(String(b).indexOf('EpubBook') === 0, 'openAsync resolves an EpubBook');
        return b.textAsync(EPUB_SMOKE.chapter2, { maxChars: 20 });
    }).then(function (t) {
        expect(t.length === 20 && chapter2.indexOf(t) === 0, 'textAsync honours maxChars');
        return asyncBook.searchAsync(EPUB_SMOKE.searchWord);
    }).then(function (h) {
        expect(h.length === hits.length, 'searchAsync matches search');
        return asyncBook.textAllAsync({ maxChars: 30 });
    }).then(function (t) {
        expect(t.length === 30, 'textAllAsync honours maxChars');
        return expectRejection(asyncBook.textAsync('OEBPS/missing.xhtml'), 'RESOURCE_NOT_FOUND');
    }).then(function () {
        return expectRejection(asyncBook.textAsync(0, { limit: 1 }), 'INVALID_ARGUMENT');
    }).then(function () {
        return expectRejection(asyncBook.resourceAsync('OEBPS/nope.png', EPUB_SMOKE.workDir), 'RESOURCE_NOT_FOUND');
    }).then(function () {
        asyncBook.close();
        return expectRejection(asyncBook.searchAsync('x'), 'SESSION_CLOSED');
    }).then(function () {
        return epub.metadataAsync(EPUB_SMOKE.path);
    }).then(function (m) {
        expect(m.title === metadata.title, 'epub.metadataAsync resolves the metadata');
        return epub.textAsync(EPUB_SMOKE.path, 0, { maxChars: 8 });
    }).then(function (t) {
        expect(t === chapter1.substring(0, 8), 'epub.textAsync(path, index, options) matches');
        return expectRejection(epub.openAsync(EPUB_SMOKE.missingPath), 'FILE_NOT_FOUND');
    }).then(function () {
        return expectRejection(epub.searchAsync(EPUB_SMOKE.path, ''), 'INVALID_ARGUMENT');
    }).then(function () {
        return epub.isAvailableAsync();
    }).then(function (available) {
        expect(available === true, 'isAvailableAsync resolves true');
        report.asyncMs = Date.now() - asyncStarted;
        report.ok = true;
        finish(null);
    }).catch(function (e) {
        finish(e);
    });
} catch (e) {
    finish(e);
}
