// Selector result parity snapshot (ROADMAP selector P0.2; the "order and count unchanged" check of P2.1-P2.3 and P5.1):
// driven by device_parity.py, which opens the screen first and writes the prelude `var AJ6SEL = {...};`.
//
// On a fixed screen (AutoJs6 main screen, home page of the system settings) the cases below run, and the result
// sequence of each is recorded as node fingerprints: [fingerprint(), depth(), indexInParent()] in result order.
// The literal values some cases need (an id, a text, a class name) are derived from the screen by fixed rules over the
// full DFS result, so a rerun derives the same selectors; the snapshot keeps only a hash of each literal (no screen
// text), which the comparison checks too. The result is written as JSON to AJ6SEL.out; the log has only progress marks.
//
// Added by P2 (the framework fast path of P2.3): the cases ending in 'One' / 'Until*' run through the polled entries
// (findOne(timeout), untilFindOne(), untilFind()) and carry `expect`, the DFS result of find() in the same run that
// they must equal, and `fastPath`, the counters of auto.stats for the case (null on a build without them); `coverage`
// counts the texts and ids of the screen whose framework lookup (findAccessibilityNodeInfosByText / ByViewId on the
// root) comes back empty although a full search finds them, the provider behaviour the fast path depends on.

var CFG = typeof AJ6SEL === 'object' ? AJ6SEL : {};
var SCENARIO = CFG.scenario || 'main';
var EXPECT = CFG.expectPackage || 'org.autojs.autojs6';
var WIDTH = CFG.width || device.width;
var HEIGHT = CFG.height || device.height;
var OUT = CFG.out || '/sdcard/Download/aj6-selector/parity.out.json';

var result = { script: 'parity.js', scenario: SCENARIO, expectPackage: EXPECT, screen: [WIDTH, HEIGHT], cases: [], errors: [] };

function mark(s) {
    console.log('AJ6SEL ' + s);
}

function hash(s) {
    return s === null || s === undefined ? null : new java.lang.String(String(s)).hashCode();
}

function errorText(e) {
    var s = String(e && e.message !== undefined ? e.name + ': ' + e.message : e);
    return s.length > 200 ? s.slice(0, 200) : s;
}

function prints(nodes) {
    var out = [];
    var size = nodes.size ? nodes.size() : nodes.length;
    for (var i = 0; i < size; i++) {
        var w = nodes.get ? nodes.get(i) : nodes[i];
        out.push(w === null || w === undefined ? null : [Number(w.fingerprint()), Number(w.depth()), Number(w.indexInParent())]);
    }
    return out;
}

function one(w) {
    return w === null || w === undefined ? [] : [w];
}

function str(v) {
    return v === null || v === undefined ? '' : String(v);
}

// The most frequent non-empty value of [key] over [rows] (the first in DFS order on ties), or null.
function mostFrequent(rows, key, accept) {
    var counts = {};
    var order = [];
    rows.forEach(function (r) {
        var v = r[key];
        if (!v || (accept && !accept(r))) return;
        if (!(v in counts)) {
            counts[v] = 0;
            order.push(v);
        }
        counts[v]++;
    });
    var best = null;
    order.forEach(function (v) {
        if (best === null || counts[v] > counts[best]) best = v;
    });
    return best;
}

function fastPathStats() {
    var f = auto.stats.fastPath;
    if (!f) return null;
    return { hits: Number(f.hits), misses: Number(f.misses), fallbacks: Number(f.fallbacks), mismatches: Number(f.mismatches) };
}

// The last text in DFS order that occurs once on the screen, or null.
function lastUniqueText(rows) {
    var counts = {};
    rows.forEach(function (r) {
        if (r.text) counts[r.text] = (counts[r.text] || 0) + 1;
    });
    for (var j = rows.length - 1; j >= 0; j--) {
        if (rows[j].text && counts[rows[j].text] === 1) return rows[j].text;
    }
    return null;
}

function lookupSize(list) {
    return list === null || list === undefined ? 0 : Number(list.size());
}

// How many distinct texts / ids of the screen the framework lookups on the root miss (counts only, no screen text).
function coverage(rows) {
    var root = auto.root;
    var out = { texts: 0, textMisses: 0, textMissClasses: {}, ids: 0, idMisses: 0, shortIds: 0, shortIdMisses: 0, idsWithoutPrefix: 0 };
    if (!root) return out;
    var pkg = String(root.packageName());
    var seenText = {};
    var seenId = {};
    var seenEntry = {};
    rows.forEach(function (r) {
        if (r.text && !seenText[r.text] && out.texts < 300) {
            seenText[r.text] = true;
            out.texts++;
            if (lookupSize(root.findAccessibilityNodeInfosByText(r.text)) === 0) {
                out.textMisses++;
                var cls = (r.className || '?').replace(/^.*\./, '');
                out.textMissClasses[cls] = (out.textMissClasses[cls] || 0) + 1;
            }
        }
        if (r.id && !seenId[r.id]) {
            seenId[r.id] = true;
            var at = r.id.indexOf(':id/');
            if (at < 0) {
                out.idsWithoutPrefix++;
                return;
            }
            out.ids++;
            if (lookupSize(root.findAccessibilityNodeInfosByViewId(r.id)) === 0) out.idMisses++;
            var entry = r.id.slice(at + 4);
            if (!seenEntry[entry]) {
                seenEntry[entry] = true;
                out.shortIds++;
                var n = lookupSize(root.findAccessibilityNodeInfosByViewId(pkg + ':id/' + entry));
                if (n === 0) n = lookupSize(root.findAccessibilityNodeInfosByViewId('android:id/' + entry));
                if (n === 0) out.shortIdMisses++;
            }
        }
    });
    return out;
}

function waitService() {
    for (var i = 0; i < 100 && !auto.isRunning(); i++) sleep(100);
    return auto.isRunning();
}

function run() {
    if (!waitService()) {
        result.errors.push('the accessibility service of AutoJs6 is not running');
        return;
    }
    var pkg = null;
    for (var i = 0; i < 50; i++) {
        sleep(200);
        var root = auto.root;
        pkg = root ? String(root.packageName()) : null;
        if (i >= 7 && pkg === EXPECT) break;
    }
    result.rootPackage = pkg;
    if (pkg !== EXPECT) {
        result.errors.push('the screen is not the one of the scenario');
        return;
    }

    var all = selector().find();
    var rows = [];
    for (var k = 0; k < all.size(); k++) {
        var w = all.get(k);
        rows.push({
            id: str(w.id()),
            text: str(w.text()),
            className: str(w.className()),
            clickable: !!w.clickable(),
        });
    }
    var idFull = mostFrequent(rows, 'id', function (r) { return r.id.indexOf(':id/') > 0; });
    var idShort = idFull ? idFull.slice(idFull.indexOf(':id/') + 4) : null;
    var textExact = mostFrequent(rows, 'text');
    var textPart = textExact ? textExact.slice(0, Math.min(2, textExact.length)) : null;
    var clsClickable = mostFrequent(rows, 'className', function (r) { return r.clickable; });
    var cls = clsClickable || mostFrequent(rows, 'className');
    var clsText = mostFrequent(rows, 'className', function (r) { return !!r.text; });
    var clickable = !!clsClickable;
    result.literals = {
        idFull: hash(idFull),
        idShort: hash(idShort),
        textExact: hash(textExact),
        textPart: hash(textPart),
        className: hash(cls),
        classWithText: hash(clsText),
        clickable: clickable,
    };

    var cases = [
        ['full', 'selector().find()', function () { return selector().find(); }],
        ['fullBfs', "selector().algorithm('BFS').find()", function () { return selector().algorithm('BFS').find(); }],
        ['idExact', 'id(<idFull>).find()', idFull && function () { return id(idFull).find(); }],
        ['idShort', 'id(<idShort>).find()', idShort && function () { return id(idShort).find(); }],
        ['idMatch', 'idMatch(/:id\\/[a-z]+_/).find()', function () { return idMatch(/:id\/[a-z]+_/).find(); }],
        ['textExact', 'text(<textExact>).find()', textExact && function () { return text(textExact).find(); }],
        ['textContains', 'textContains(<textPart>).find()', textPart && function () { return textContains(textPart).find(); }],
        ['textMatch', 'textMatch(/^.{1,4}$/).find()', function () { return textMatch(/^.{1,4}$/).find(); }],
        ['descMatch', 'descMatch(/./).find()', function () { return descMatch(/./).find(); }],
        ['classClickable', 'className(<className>).clickable(<clickable>).find()', cls && function () { return className(cls).clickable(clickable).find(); }],
        ['boundsInside', 'boundsInside(0, 0, width, height / 2).find()', function () { return boundsInside(0, 0, WIDTH, Math.floor(HEIGHT / 2)).find(); }],
        ['depthRange', 'minDepth(2).maxDepth(5).find()', function () { return minDepth(2).maxDepth(5).find(); }],
        ['leaves', 'selector().filter(w => w.childCount() == 0).find()', function () { return selector().filter(function (w) { return w.childCount() == 0; }).find(); }],
        // '[]' rather than '{}': the collection spellings depend on how the process first loaded PickResultType (appendix C Q9).
        ['objectPickup', "pickup({ className: <className>, clickable: <clickable> }, '[]')", cls && function () { return pickup({ className: cls, clickable: clickable }, '[]'); }],
        ['objectPickupArray', "pickup({ className: <className> }, '[]')", cls && function () { return pickup({ className: cls }, '[]'); }],
        ['mixedArray', "pickup([ textMatch(/./), { className: <classWithText> } ], '[]')", clsText && function () { return pickup([ textMatch(/./), { className: clsText } ], '[]'); }],
        ['contentPickup', "pickup(<textExact>, '[]')", textExact && function () { return pickup(textExact, '[]'); }],
        ['selectSyntax', "select('className=\"<className>\" [!]clickable').find()", cls && function () { return select('className="' + cls + '" ' + (clickable ? '' : '!') + 'clickable').find(); }],
        ['classBfs', "className(<className>).algorithm('BFS').find()", cls && function () { return className(cls).algorithm('BFS').find(); }],
        ['findOnceIndex', 'className(<className>).findOnce(1)', cls && function () { return one(className(cls).findOnce(1)); }],
        ['findOnce', 'className(<className>).findOnce()', cls && function () { return one(className(cls).findOnce()); }],
        ['untilFind', 'className(<className>).untilFind()', cls && function () { return className(cls).untilFind(); }],
        ['pickupOne', "pickup(className(<className>), 'w')", cls && function () { return one(pickup(className(cls), 'w')); }],
    ];
    // The polled entries of the fast path (P2.3), each with the DFS result it must equal.
    var uniqueText = lastUniqueText(rows);
    result.literalsExtra = { uniqueText: hash(uniqueText) };
    function first(c) { return c.size() > 0 ? [c.get(0)] : []; }
    var polled = [
        ['idExactOne', 'id(<idFull>).findOne(2000)', idFull && function () { return one(id(idFull).findOne(2000)); }, idFull && function () { return first(id(idFull).find()); }],
        ['idShortOne', 'id(<idShort>).findOne(2000)', idShort && function () { return one(id(idShort).findOne(2000)); }, idShort && function () { return first(id(idShort).find()); }],
        ['textExactOne', 'text(<textExact>).findOne(2000)', textExact && function () { return one(text(textExact).findOne(2000)); }, textExact && function () { return first(text(textExact).find()); }],
        ['uniqueTextOne', 'text(<uniqueText>).findOne(2000)', uniqueText && function () { return one(text(uniqueText).findOne(2000)); }, uniqueText && function () { return first(text(uniqueText).find()); }],
        ['uniqueTextUntilOne', 'text(<uniqueText>).untilFindOne()', uniqueText && function () { return one(text(uniqueText).untilFindOne()); }, uniqueText && function () { return first(text(uniqueText).find()); }],
        ['idExactUntilFind', 'id(<idFull>).untilFind()', idFull && function () { return id(idFull).untilFind(); }, idFull && function () { return id(idFull).find(); }],
        ['textExactUntilFind', 'text(<textExact>).untilFind()', textExact && function () { return text(textExact).untilFind(); }, textExact && function () { return text(textExact).find(); }],
    ];

    cases.forEach(function (c) {
        var entry = { name: c[0], selector: c[1] };
        if (!c[2]) {
            entry.skipped = 'no literal on this screen';
        } else {
            try {
                var p = prints(c[2]());
                entry.count = p.length;
                entry.nodes = p;
            } catch (e) {
                entry.error = errorText(e);
            }
        }
        result.cases.push(entry);
        mark(c[0] + ' ' + (entry.error ? 'ERROR' : entry.skipped ? 'SKIP' : entry.count));
    });

    polled.forEach(function (c) {
        var entry = { name: c[0], selector: c[1] };
        if (!c[2]) {
            entry.skipped = 'no literal on this screen';
        } else {
            try {
                auto.stats.reset();
                var p = prints(c[2]());
                entry.fastPath = fastPathStats();
                entry.count = p.length;
                entry.nodes = p;
                entry.expect = prints(c[3]());
            } catch (e) {
                entry.error = errorText(e);
            }
        }
        result.cases.push(entry);
        mark(c[0] + ' ' + (entry.error ? 'ERROR' : entry.skipped ? 'SKIP' : entry.count));
    });

    try {
        result.coverage = coverage(rows);
    } catch (e) {
        result.coverage = { error: errorText(e) };
    }
    mark('coverage ' + JSON.stringify(result.coverage));
}

try {
    run();
} catch (e) {
    result.errors.push(errorText(e));
} finally {
    files.write(OUT, JSON.stringify(result));
    mark('done');
}
