// Device smoke script for `epub.read` (plugin roadmap P6.2). The test prepends
// `var EPUB_SMOKE = { path, title, chapter1, chapter2, ... };` (a fixture EPUB written by the
// test) with the host in the foreground, and reads the JSON report from runtime property
// `epub.smoke.report` once the session closed. The reader is driven from the events: `open`
// jumps to chapter 1, the first `progress` there runs the synchronous checks and turns the
// chapter, the `progress` at chapter 2 closes the session with `finish`, and `close` reports.

var report = { steps: [], events: [], errors: [], ok: false };
var phase = 'pending';
var session = null;

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
    expect(error.code === code, 'expected ' + code + ', got ' + error.code + ' (' + error.message + ')');
    return error;
}

function finish(e) {
    if (e && !report.error) {
        report.error = String(e) + (e && e.stack ? '\n' + e.stack : '');
        report.errorCode = e && e.code;
        report.phase = phase;
        if (session && !session.isClosed) {
            try { session.close({ finish: true }); } catch (ignored) { }
        }
    }
    runtime.putProperty('epub.smoke.report', JSON.stringify(report));
}

function onOpen(e) {
    try {
        report.events.push('open:' + e.href);
        expect(phase === 'pending', 'open arrives once, first');
        expect(session.isOpen && session.state === 'open' && !session.isClosed, 'the session is open at the open event');
        expect(e.href === EPUB_SMOKE.chapter2, 'the reader starts at the requested chapter 2');
        expect(e.title === EPUB_SMOKE.title && session.title === e.title, 'the open event carries the book title');
        expect(typeof e.positions === 'number' && e.positions > 0 && session.positions === e.positions, 'the open event carries the position count');
        expect(e.locator && e.locator.href === EPUB_SMOKE.chapter2, 'the open event carries a locator');
        expect(session.locator && session.locator.href === e.locator.href, 'session.locator follows the open event');
        expect(typeof session.progress === 'number' && session.progress <= 1, 'session.progress is a number after open (-1 until the reader knows a total progression)');
        phase = 'toChapter1';
        expect(session.goTo(EPUB_SMOKE.chapter1) === session, 'goTo(href) returns the session');
    } catch (err) {
        finish(err);
    }
}

function onProgress(e) {
    try {
        report.events.push('progress:' + e.href);
        expect(typeof e.totalProgression === 'number', 'progress carries totalProgression');
        expect(session.progress === e.totalProgression && session.progress >= 0 && session.progress <= 1, 'session.progress follows the progress event and lies between 0 and 1');
        expect(e.locator && e.locator.href === e.href, 'progress carries a locator for its href');
        if (phase === 'toChapter1' && e.href === EPUB_SMOKE.chapter1) {
            phase = 'checks';
            expect(session.href === EPUB_SMOKE.chapter1, 'session.href follows the progress event');
            expect(typeof e.chapterTitle === 'string' || e.chapterTitle === null, 'progress carries chapterTitle or null');
            var bookmarks = session.bookmarks();
            expect(Array.isArray(bookmarks), 'bookmarks() returns an array');
            expectEpubError(function () { session.goTo(1.5); }, 'INVALID_ARGUMENT');
            expectEpubError(function () { session.goTo(); }, 'INVALID_ARGUMENT');
            expectEpubError(function () { session.goTo(null); }, 'INVALID_ARGUMENT');
            expectEpubError(function () { session.goTo({ title: 'no href' }); }, 'INVALID_ARGUMENT');
            expectEpubError(function () { session.goTo('OEBPS/missing.xhtml'); }, 'RESOURCE_NOT_FOUND');
            expectEpubError(function () { session.setPreferences({ theme: 'neon' }); }, 'INVALID_ARGUMENT');
            expectEpubError(function () { session.setPreferences({ fontSize: 9 }); }, 'INVALID_ARGUMENT');
            expectEpubError(function () { session.setPreferences('dark'); }, 'INVALID_ARGUMENT');
            expectEpubError(function () { session.close({ finish: 'yes' }); }, 'INVALID_ARGUMENT');
            expect(session.setPreferences({ lineHeight: 1.5, nope: true }) === session, 'setPreferences with an unknown key is accepted (error event follows)');
            phase = 'toChapter2';
            expect(session.nextChapter() === session, 'nextChapter() returns the session');
        } else if (phase === 'toChapter2' && e.href === EPUB_SMOKE.chapter2) {
            phase = 'closing';
            report.progressBeforeClose = session.progress;
            expect(session.close({ finish: true }) === session, 'close({ finish: true }) returns the session');
        }
    } catch (err) {
        finish(err);
    }
}

function onClose(e) {
    try {
        report.events.push('close:' + e.reason);
        expect(phase === 'closing', 'close arrives after the host closed the session (phase ' + phase + ')');
        expect(e.reason === 'host', 'the close reason is host');
        expect(session.isClosed && !session.isOpen && session.state === 'closed', 'the session is closed at the close event');
        expect(session.reason === 'host', 'session.reason is host');
        expect(session.progress === report.progressBeforeClose, 'the last progress survives the close');
        expectEpubError(function () { session.next(); }, 'SESSION_CLOSED');
        expectEpubError(function () { session.bookmarks(); }, 'SESSION_CLOSED');
        expect(session.close() === session, 'close after close is a no-op');
        expect(report.events.filter(function (name) { return name.indexOf('close:') === 0; }).length === 1, 'close is emitted once');
        expect(report.errors.some(function (text) { return text.indexOf('UNSUPPORTED_PREFERENCE') === 0 && text.indexOf('bogus') > 0; }), 'the unknown start preference arrived as an error event');
        expect(report.errors.some(function (text) { return text.indexOf('UNSUPPORTED_PREFERENCE') === 0 && text.indexOf('nope') > 0; }), 'the unknown setPreferences key arrived as an error event');
        report.ok = true;
        finish(null);
    } catch (err) {
        finish(err);
    }
}

try {
    expect(typeof epub.read === 'function' && typeof epub.readAsync === 'function', 'epub.read and epub.readAsync exist');
    expectEpubError(function () { epub.read(EPUB_SMOKE.path, { href: 'a.xhtml', progression: 0.5 }); }, 'INVALID_ARGUMENT');
    expectEpubError(function () { epub.read(EPUB_SMOKE.missingPath); }, 'FILE_NOT_FOUND');
    var started = Date.now();
    session = epub.read(EPUB_SMOKE.path, { href: EPUB_SMOKE.chapter2, preferences: { fontSize: 1.2, bogus: 1 } });
    report.readMs = Date.now() - started;
    expect(String(session).indexOf('EpubReaderSession') === 0, 'String(session) names EpubReaderSession');
    expect(session.state === 'pending' && !session.isOpen && !session.isClosed, 'the session starts pending');
    expect(session.path === EPUB_SMOKE.path, 'session.path is the opened path');
    expect(session.locator === null && session.progress === -1 && session.positions === 0 && session.reason === null, 'no locator, progress, positions or reason before open');
    expect(session.on('open', onOpen) === session, 'on() returns the session');
    session.on('progress', onProgress);
    session.on('bookmark', function (e) { report.events.push('bookmark:' + e.action); });
    session.on('error', function (e) {
        report.errors.push(e.code + ': ' + e.message);
        if (!(e instanceof epub.EpubError)) finish(new Error('smoke: the error event carries no EpubError: ' + e));
    });
    session.on('close', onClose);
} catch (e) {
    finish(e);
}
