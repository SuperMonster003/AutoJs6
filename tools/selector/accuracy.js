// Selector accuracy cases on a device (ROADMAP selector P3.2-P3.4), driven by device_accuracy.py, which writes the prelude
// `var AJ6SEL = {...};` (cases, output file). The cases run over a raw floaty window of their own above the home screen
// (searched through the window filter { packageName: AutoJs6 }) or over the screen opened before:
//   refind*   P3.2: a filter function removes the node it is asked about from the window while the search runs (and puts
//             a new view with the same text in its place, or not), so the search returns a node that has left the
//             window. findOnce / findOne / untilFindOne / pickup refresh it and search again (auto.stats.queries.refinds);
//             under noRefreshOnHit they return the stale node.
//   prefer*   P3.4: two views with the same text, the first moved off the window (visibleToUser false): the single
//             results take the first one, under prefer('visible') the visible one (also through an object selector).
//   normalizeObject P3.3: normalize({ caseInsensitive: true }) given a script object (the options overload).
//   normalize P3.3, over the screen opened before (the settings home page, no window of its own): a text of the screen
//             with ASCII letters, written in other case, its first letter full-width and padded with spaces, misses
//             without normalize() and finds the same node with it.
// The result is written as JSON to AJ6SEL.out; the log has only progress marks, no node text.

var CFG = typeof AJ6SEL === 'object' ? AJ6SEL : {};
var PKG = 'org.autojs.autojs6';
var OUT = CFG.out || '/sdcard/Download/aj6-selector/accuracy.out.json';
var CASES = CFG.cases || ['refind'];
var TARGET = 'aj6sel-refind';
var TWIN = 'aj6sel-twin';

var result = { script: 'accuracy.js', cases: [], errors: [] };

function mark(s) {
    console.log('AJ6SEL ' + s);
}

function errorText(e) {
    var s = String(e && e.message !== undefined ? e.name + ': ' + e.message : e);
    return s.length > 200 ? s.slice(0, 200) + '...' : s;
}

function waitService() {
    for (var i = 0; i < 100 && !auto.isRunning(); i++) sleep(100);
    return auto.isRunning();
}

// One case: [fn] fills the record and returns whether it passed.
function run(name, fn) {
    var c = { name: name, ok: false };
    try {
        auto.stats.reset();
        c.ok = fn(c) === true;
    } catch (e) {
        c.error = errorText(e);
    } finally {
        try {
            auto.setFlags([]);
        } catch (e) {
            // A build before the flag names of the case.
        }
    }
    result.cases.push(c);
    mark(name + ' ' + (c.ok ? 'PASS' : 'FAIL'));
}

function onUi(fn) {
    var done = threads.disposable();
    ui.post(function () {
        try {
            done.setAndNotify({ value: fn() });
        } catch (e) {
            done.setAndNotify({ error: errorText(e) });
        }
    });
    var r = done.blockedGet();
    if (r.error) throw Error(r.error);
    return r.value;
}

var overlay = null;
var target = null;

function newTarget(list) {
    var tv = new android.widget.TextView(list.getContext());
    tv.setText(TARGET);
    tv.setTextSize(20);
    return tv;
}

// The twins of the prefer cases: the first moved off the window, so that it is not visible to the user.
function addTwins(twins) {
    for (var i = 0; i < 2; i++) {
        var tv = new android.widget.TextView(twins.getContext());
        tv.setText(TWIN);
        tv.setTextSize(20);
        tv.setClickable(true);
        if (i === 0) tv.setTranslationY(-5000);
        twins.addView(tv);
    }
}

function createOverlay() {
    overlay = floaty.rawWindow('<frame bg="#ffffffff"><vertical><vertical id="list"/><vertical id="twins"/></vertical></frame>');
    onUi(function () {
        overlay.setSize(-1, -2);
        target = newTarget(overlay.list);
        overlay.list.addView(target);
        addTwins(overlay.twins);
    });
    auto.setWindowFilter({ packageName: PKG });
    for (var i = 0; i < 30 && text(TARGET).findOnce() === null; i++) sleep(100);
}

// Puts a fresh target in place (the cases before may have removed it) and waits until the search sees it (the cache of
// the service may hand out the removed node for a while: ColorOS).
function resetTarget() {
    onUi(function () {
        overlay.list.removeAllViews();
        target = newTarget(overlay.list);
        overlay.list.addView(target);
    });
    for (var i = 0; i < 30; i++) {
        var node = text(TARGET).findOnce(0);
        if (node !== null && node.refresh()) break;
        sleep(100);
    }
}

// A filter function that, asked about the target the first time, removes it from the window (and adds a new one with
// the same text when [replace]) before it answers, so the search returns a node that has left the window.
function staleOnce(replace) {
    var state = { armed: true };
    var fn = function (w) {
        if (String(w.text()) !== TARGET) return false;
        if (state.armed) {
            state.armed = false;
            onUi(function () {
                overlay.list.removeView(target);
                if (replace) {
                    target = newTarget(overlay.list);
                    overlay.list.addView(target);
                }
            });
            // Until the service has the change: the dialog closed (and another opened) before the script got the node.
            sleep(300);
        }
        return true;
    };
    return fn;
}

function refinds() {
    var q = auto.stats.queries;
    return q && q.refinds !== undefined ? Number(q.refinds) : null;
}

// [find] runs the selector; [expect]: 'fresh' (a node that refreshes), 'stale' (the node that left), 'none'.
function refindCase(name, replace, flags, find, expect, expectRefinds) {
    run(name, function (c) {
        resetTarget();
        if (flags) auto.setFlags(flags);
        auto.stats.reset();
        var node = find(text(TARGET).filter(staleOnce(replace)));
        c.found = node !== null && node !== undefined;
        c.fresh = c.found ? Boolean(node.refresh()) : null;
        c.refinds = refinds();
        var shape = !c.found ? 'none' : c.fresh ? 'fresh' : 'stale';
        c.shape = shape;
        return shape === expect && c.refinds === expectRefinds;
    });
}

function refindCases() {
    refindCase('refindFindOnce', true, null, function (s) { return s.findOnce(); }, 'fresh', 1);
    refindCase('refindFindOnceGone', false, null, function (s) { return s.findOnce(); }, 'none', 1);
    refindCase('refindFindOne', true, null, function (s) { return s.findOne(2000); }, 'fresh', 1);
    refindCase('refindUntilFindOne', true, null, function (s) { return s.untilFindOne(); }, 'fresh', 1);
    refindCase('refindPickup', true, null, function (s) { return pickup(s); }, 'fresh', 1);
    refindCase('refindOff', true, ['noRefreshOnHit'], function (s) { return s.findOnce(); }, 'stale', 0);
    // findOnce(index) and find() return the nodes of the search unchecked.
    refindCase('refindIndexUnchecked', true, null, function (s) { return s.findOnce(0); }, 'stale', 0);
}

function visible(node) {
    return node === null || node === undefined ? null : Boolean(node.visibleToUser());
}

function preferCases() {
    run('preferNone', function (c) {
        c.visible = visible(text(TWIN).findOnce());
        return c.visible === false;
    });
    run('preferFindOnce', function (c) {
        c.visible = visible(text(TWIN).prefer('visible').findOnce());
        return c.visible === true;
    });
    run('preferFindOne', function (c) {
        c.visible = visible(text(TWIN).prefer('visible').findOne(1000));
        return c.visible === true;
    });
    run('preferPickup', function (c) {
        c.visible = visible(pickup({ text: TWIN, prefer: 'visible' }));
        return c.visible === true;
    });
    run('preferCompassK', function (c) {
        c.visible = visible(pickup(text(TWIN).prefer('visible', 'top'), 'k'));
        return c.visible === true;
    });
}

function normalizeObjectCase() {
    run('normalizeObject', function (c) {
        c.found = text(TWIN.toUpperCase()).findOnce() !== null;
        c.normalized = text(TWIN.toUpperCase()).normalize({ caseInsensitive: true }).findOnce() !== null;
        return !c.found && c.normalized;
    });
}

// A text of the screen with at least three ASCII letters, or null.
function asciiText() {
    var all = selector().find();
    for (var i = 0; i < all.size(); i++) {
        var t = String(all.get(i).text() || '');
        if (/[A-Za-z].*[A-Za-z].*[A-Za-z]/.test(t) && t.length <= 40) return t;
    }
    return null;
}

// [t] in other case, its first letter full-width, padded with an ideographic space and a space.
function variantOf(t) {
    var swapped = t.split('').map(function (ch) {
        var up = ch.toUpperCase();
        return ch === up ? ch.toLowerCase() : up;
    }).join('');
    var k = swapped.search(/[A-Za-z]/);
    var wide = String.fromCharCode(swapped.charCodeAt(k) + 0xFEE0);
    return String.fromCharCode(0x3000) + swapped.slice(0, k) + wide + swapped.slice(k + 1) + ' ';
}

function sameNode(a, b) {
    return a !== null && b !== null && String(a.bounds()) === String(b.bounds()) && String(a.className()) === String(b.className());
}

function normalizeCase() {
    run('normalize', function (c) {
        var t = asciiText();
        if (t === null) {
            c.skipped = 'no text with ASCII letters on the screen';
            return false;
        }
        var v = variantOf(t);
        var original = text(t).findOnce();
        c.plain = text(v).findOnce() !== null;
        var normalized = text(v).normalize().findOnce();
        c.normalized = normalized !== null;
        c.same = sameNode(original, normalized);
        c.letters = t.length;
        return !c.plain && c.normalized && c.same;
    });
}

function main() {
    if (!waitService()) {
        result.errors.push('the accessibility service of AutoJs6 is not running');
        mark('service FAIL');
        return;
    }
    if (CASES.indexOf('normalize') >= 0) normalizeCase();
    if (CASES.indexOf('refind') < 0 && CASES.indexOf('prefer') < 0) return;
    createOverlay();
    if (CASES.indexOf('refind') >= 0) refindCases();
    if (CASES.indexOf('prefer') >= 0) {
        preferCases();
        normalizeObjectCase();
    }
}

try {
    main();
} catch (e) {
    result.errors.push(errorText(e));
    mark('error ' + errorText(e));
} finally {
    try {
        auto.setWindowFilter(null);
    } catch (e) {
        // Nothing to restore.
    }
    if (overlay) overlay.close();
    files.ensureDir(OUT);
    files.write(OUT, JSON.stringify(result));
    mark('done');
}
