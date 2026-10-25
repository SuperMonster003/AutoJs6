// pickup / detect golden table (ROADMAP selector P0.3; the "behavior unchanged" check of P1.5, apart from the
// differences D6 (b) and P1.1 allow): driven by device_pick_table.py, which writes the prelude `var AJ6SEL = {...};`.
//
// The searches run on a raw floaty window of fixed synthetic content (texts and descriptions start with "aj6pt"),
// searched alone through the window filter { packageName: AutoJs6 }, so the table does not depend on other screens:
//
//   frame (the window root)
//     box      LinearLayout, desc aj6pt-box
//       title  TextView aj6pt-title
//       row    LinearLayout, desc aj6pt-row, clickable
//         a    TextView aj6pt-a
//         btn  Button aj6pt-btn
//         b    TextView aj6pt-b
//       edit   EditText aj6pt-edit, hint aj6pt-hint
//       check  CheckBox aj6pt-check, checked
//       list   LinearLayout, desc aj6pt-list
//         item TextView aj6pt-item-0 / aj6pt-item-1 / aj6pt-item-2
//
// Sections (row ids are "<section>|..."): types (every result type spelling and the non-string forms x compass
// default / p / c0 / s>1 / k2 x one hit / three hits / no hit x without / with an observing callback), detect (the same
// types and compasses on the row node and on null), forms (selector argument forms x result types), roots, arity
// (argument counts and positions of pickup / detect), callbacks, invoke (parameterized calls in and out of the
// interceptor table), sweep (every public zero-argument method of UiObject by name, on a hit and on no hit) and
// exists. Each row keeps the shape of the result (type, length, string value, node summary) or of the error (name,
// Java exception class, message), and for callback rows the shape of the callback argument. Written as JSON to
// AJ6SEL.out.

var CFG = typeof AJ6SEL === 'object' ? AJ6SEL : {};
var PKG = 'org.autojs.autojs6';
var OUT = CFG.out || '/sdcard/Download/aj6-selector/pick_table.out.json';

// PickResultType spellings (core/automator/pick/PickResultType.kt groups, in order).
var SPELLINGS = [
    '#', 'w', 'widget',
    '{}', 'wc', 'collection', 'list', '#{}', '{#}', 'w{}', '{w}',
    '[]', 'ws', 'widgets', '##', '#[]', '[#]', 'w[]', '[w]',
    '$', 'txt', 'content',
    'contents', '$$', '$[]', '[$]', 'txt[]', '[txt]', 'content[]', '[content]',
    '.', 'pt', 'point',
    'points', 'pts', '..', '.[]', '[.]', 'point[]', '[point]', 'pt[]', '[pt]',
    '@', 'selector', 'sel',
    '?', 'exists',
];

// Result type arguments other than the spellings: [label, value].
var OTHER_TYPES = [
    ['empty', ''],
    ['null', null],
    ['number0', 0],
    ['true', true],
    ['list[]', []],
    ["list['#']", ['#']],
    ["list['$']", ['$']],
    ["list['.']", ['.']],
    ["list['w']", ['w']],
    ["list['ws']", ['ws']],
    ["list['#','x']", ['#', 'x']],
    ['object{}', {}],
];

var COMPASSES = [null, 'p', 'c0', 's>1', 'k2'];

var result = { script: 'pick_table.js', rows: [], errors: [] };
var NOARG = {};
var cbState = { arg: NOARG, calls: 0 };

function mark(s) {
    console.log('AJ6SEL ' + s);
}

function errorText(e) {
    var s = String(e && e.message !== undefined ? e.name + ': ' + e.message : e);
    return s.length > 200 ? s.slice(0, 200) : s;
}

function isJava(v) {
    return v !== null && typeof v === 'object' && v instanceof java.lang.Object;
}

function clip(s) {
    s = String(s);
    return s.length <= 80 ? s : s.slice(0, 60) + '...(' + s.length + ')';
}

function node(w) {
    var cls = String(w.className() || '');
    return [cls.slice(cls.lastIndexOf('.') + 1), String(w.text() || ''), String(w.desc() || ''), Number(w.depth()), Number(w.indexInParent())];
}

function shape(v, depth) {
    depth = depth || 0;
    if (v === undefined) return { t: 'undefined' };
    if (v === null) return { t: 'null' };
    var t = typeof v;
    if (t === 'boolean') return { t: t, v: v };
    if (t === 'number') return { t: t, v: isNaN(v) ? 'NaN' : v };
    if (t === 'string') return { t: t, v: clip(stable(v)) };
    if (t === 'function') return { t: 'function' };
    if (Array.isArray(v)) {
        return { t: 'array', n: v.length, items: depth > 1 ? undefined : v.slice(0, 4).map(function (x) { return shape(x, depth + 1); }) };
    }
    if (isJava(v)) {
        var cls = String(v.getClass().getName());
        if (v instanceof org.autojs.autojs.core.automator.UiObject) return { t: 'UiObject', node: node(v) };
        if (v instanceof org.autojs.autojs.core.automator.UiObjectCollection) {
            var items = [];
            for (var i = 0; i < Math.min(4, v.size()); i++) items.push(v.get(i) ? node(v.get(i)) : null);
            return { t: 'UiObjectCollection', n: Number(v.size()), items: items };
        }
        if (v instanceof org.autojs.autojs.core.accessibility.UiSelector) return { t: 'UiSelector', v: clip(v.toString()) };
        if (cls === 'org.opencv.core.Point') return { t: 'Point', v: [Math.round(v.x), Math.round(v.y)] };
        if (v instanceof android.graphics.Rect) return { t: 'Rect', v: String(v.toShortString()) };
        if (v instanceof java.lang.CharSequence) return { t: 'CharSequence', v: clip(v) };
        if (v instanceof java.lang.Boolean || v instanceof java.lang.Number) return { t: cls, v: String(v) };
        if (v instanceof java.util.Collection) return { t: cls, n: Number(v.size()) };
        return { t: cls };
    }
    if (t === 'object') {
        if (v instanceof org.autojs.autojs.core.automator.UiObjectCollection) return { t: 'UiObjectCollection', n: Number(v.size()) };
        if (typeof v.size === 'function' && typeof v.get === 'function') {
            var list = [];
            for (var k = 0; k < Math.min(4, v.size()); k++) list.push(v.get(k) ? node(v.get(k)) : null);
            return { t: 'collection-like', n: Number(v.size()), items: list };
        }
        return { t: 'object', keys: Object.keys(v).slice(0, 8) };
    }
    return { t: t };
}

// Object identities (`NativeArray@a509c39`) differ from run to run; they become `@*`.
function stable(s) {
    return String(s).replace(/@[0-9a-f]{5,}/g, '@*');
}

function errShape(e) {
    var r = { name: String(e && e.name !== undefined ? e.name : typeof e) };
    // The first line only: the old layer appends the Java stack, whose line numbers change with the code.
    var msg = stable(String(e && e.message !== undefined ? e.message : e).split(String.fromCharCode(10))[0]);
    r.message = msg.length > 160 ? msg.slice(0, 160) : msg;
    var j = e && e.javaException;
    if (j) r.java = String(j.getClass().getName());
    return r;
}

function observe(o) {
    cbState.calls++;
    cbState.arg = shape(o);
    return undefined;
}

function replace(o) {
    cbState.calls++;
    cbState.arg = shape(o);
    return 'aj6pt-cb';
}

function toNull(o) {
    cbState.calls++;
    cbState.arg = shape(o);
    return null;
}

// [masked]: the value differs from run to run (a window id), so only its type is kept.
function rec(id, call, fn, masked) {
    var row = { id: id, call: call };
    cbState.arg = NOARG;
    cbState.calls = 0;
    try {
        row.result = shape(fn());
        if (masked && 'v' in row.result) row.result.v = '*';
    } catch (e) {
        row.error = errShape(e);
    }
    if (cbState.arg !== NOARG) {
        row.cbArg = cbState.arg;
        row.cbCalls = cbState.calls;
    }
    result.rows.push(row);
}

function lit(v) {
    if (v === null) return 'null';
    if (v === undefined) return 'undefined';
    if (typeof v === 'string') return "'" + v + "'";
    if (Array.isArray(v)) return '[' + v.map(lit).join(', ') + ']';
    if (typeof v === 'object') return '{}';
    return String(v);
}

var overlay = null;

function createOverlay() {
    overlay = floaty.rawWindow('<frame id="root" bg="#ffffffff"/>');
    var done = threads.disposable();
    ui.run(function () {
        try {
            overlay.setSize(-1, -1);
            var root = overlay.root;
            var ctx = root.getContext();
            var LL = android.widget.LinearLayout;
            function text(parent, cls, s) {
                var v = new cls(ctx);
                v.setText(s);
                parent.addView(v);
                return v;
            }
            var box = new LL(ctx);
            box.setOrientation(LL.VERTICAL);
            box.setContentDescription('aj6pt-box');
            root.addView(box);
            text(box, android.widget.TextView, 'aj6pt-title');
            var row = new LL(ctx);
            row.setOrientation(LL.HORIZONTAL);
            row.setContentDescription('aj6pt-row');
            row.setClickable(true);
            box.addView(row);
            text(row, android.widget.TextView, 'aj6pt-a');
            text(row, android.widget.Button, 'aj6pt-btn');
            text(row, android.widget.TextView, 'aj6pt-b');
            var edit = text(box, android.widget.EditText, 'aj6pt-edit');
            edit.setHint('aj6pt-hint');
            var check = text(box, android.widget.CheckBox, 'aj6pt-check');
            check.setChecked(true);
            var list = new LL(ctx);
            list.setOrientation(LL.VERTICAL);
            list.setContentDescription('aj6pt-list');
            box.addView(list);
            for (var i = 0; i < 3; i++) text(list, android.widget.TextView, 'aj6pt-item-' + i);
            done.setAndNotify(true);
        } catch (e) {
            done.setAndNotify(errorText(e));
        }
    });
    var state = done.blockedGet();
    if (state !== true) throw Error('overlay: ' + state);
    auto.setWindowFilter({ packageName: PKG });
}

var TARGETS = [
    ['one', "desc('aj6pt-row')", function () { return desc('aj6pt-row'); }],
    ['three', "textStartsWith('aj6pt-item-')", function () { return textStartsWith('aj6pt-item-'); }],
    ['none', "text('aj6pt-none')", function () { return text('aj6pt-none'); }],
];

function allTypes() {
    return SPELLINGS.map(function (s) { return [s, s]; }).concat(OTHER_TYPES);
}

function sectionTypes() {
    allTypes().forEach(function (ty) {
        COMPASSES.forEach(function (c) {
            TARGETS.forEach(function (tg) {
                [false, true].forEach(function (withCb) {
                    var id = 'types|' + ty[0] + '|' + (c || '-') + '|' + tg[0] + '|' + (withCb ? 'cb' : '-');
                    var args = tg[1] + (c ? ", '" + c + "'" : '') + ', ' + lit(ty[1]) + (withCb ? ', observe' : '');
                    rec(id, 'pickup(' + args + ')', function () {
                        var sel = tg[2]();
                        if (c && withCb) return pickup(sel, c, ty[1], observe);
                        if (c) return pickup(sel, c, ty[1]);
                        if (withCb) return pickup(sel, ty[1], observe);
                        return pickup(sel, ty[1]);
                    });
                });
            });
        });
    });
}

function sectionDetect() {
    var w = desc('aj6pt-row').findOnce();
    var subjects = [['row', 'w', function () { return w; }], ['null', 'null', function () { return null; }]];
    allTypes().forEach(function (ty) {
        COMPASSES.forEach(function (c) {
            subjects.forEach(function (sb) {
                [false, true].forEach(function (withCb) {
                    var id = 'detect|' + ty[0] + '|' + (c || '-') + '|' + sb[0] + '|' + (withCb ? 'cb' : '-');
                    var args = sb[1] + (c ? ", '" + c + "'" : '') + ', ' + lit(ty[1]) + (withCb ? ', observe' : '');
                    rec(id, 'detect(' + args + ')', function () {
                        var x = sb[2]();
                        if (c && withCb) return detect(x, c, ty[1], observe);
                        if (c) return detect(x, c, ty[1]);
                        if (withCb) return detect(x, ty[1], observe);
                        return detect(x, ty[1]);
                    });
                });
            });
        });
    });
}

var FORMS = [
    ['string', "'aj6pt-a'", function () { return 'aj6pt-a'; }],
    ['string-miss', "'aj6pt-none'", function () { return 'aj6pt-none'; }],
    ['string-empty', "''", function () { return ''; }],
    ['regex', '/^aj6pt-item-[0-9]$/', function () { return /^aj6pt-item-[0-9]$/; }],
    ['regex-miss', '/^aj6pt-none$/', function () { return /^aj6pt-none$/; }],
    ['regex-flags', '/AJ6PT-A/i', function () { return /AJ6PT-A/i; }],
    ['uiselector', "text('aj6pt-a')", function () { return text('aj6pt-a'); }],
    ['uiselector-bfs', "textStartsWith('aj6pt').algorithm('BFS')", function () { return textStartsWith('aj6pt').algorithm('BFS'); }],
    ['uiselector-empty', 'selector()', function () { return selector(); }],
    ['object', "{ text: 'aj6pt-a' }", function () { return { text: 'aj6pt-a' }; }],
    ['object-multi', "{ textStartsWith: 'aj6pt-item-' }", function () { return { textStartsWith: 'aj6pt-item-' }; }],
    ['object-bool', "{ desc: 'aj6pt-row', clickable: true }", function () { return { desc: 'aj6pt-row', clickable: true }; }],
    ['object-bool-list', "{ desc: 'aj6pt-row', clickable: [] }", function () { return { desc: 'aj6pt-row', clickable: [] }; }],
    ['object-algorithm', "{ algorithm: 'BFS', textStartsWith: 'aj6pt' }", function () { return { algorithm: 'BFS', textStartsWith: 'aj6pt' }; }],
    ['object-filter', '{ filter: w => w.childCount() == 3 }', function () { return { filter: function (w) { return w.childCount() == 3; } }; }],
    ['object-bounds', '{ boundsInside: [0, 0, 1, 1] }', function () { return { boundsInside: [0, 0, 1, 1] }; }],
    ['object-regex-value', '{ textMatch: /aj6pt-[ab]$/ }', function () { return { textMatch: /aj6pt-[ab]$/ }; }],
    ['object-unknown-key', '{ fooBar: 1 }', function () { return { fooBar: 1 }; }],
    ['object-bad-value', "{ clickable: 'x' }", function () { return { clickable: 'x' }; }],
    ['object-bad-depth', "{ depth: 'x' }", function () { return { depth: 'x' }; }],
    ['object-empty', '{}', function () { return {}; }],
    ['array', "[ 'aj6pt-a' ]", function () { return ['aj6pt-a']; }],
    ['array-mixed', "[ text('aj6pt-a'), { clickable: false } ]", function () { return [text('aj6pt-a'), { clickable: false }]; }],
    ['array-regex-object', "[ /aj6pt-item/, { className: 'android.widget.TextView' } ]", function () { return [/aj6pt-item/, { className: 'android.widget.TextView' }]; }],
    ['array-conflict', "[ 'aj6pt-a', 'aj6pt-b' ]", function () { return ['aj6pt-a', 'aj6pt-b']; }],
    ['array-empty', '[]', function () { return []; }],
    ['array-null', '[ null ]', function () { return [null]; }],
    ['array-nested', "[ [ 'aj6pt-a' ] ]", function () { return [['aj6pt-a']]; }],
    ['number', '0', function () { return 0; }],
    ['number-fraction', '1.5', function () { return 1.5; }],
    ['number-nan', 'NaN', function () { return NaN; }],
    ['null', 'null', function () { return null; }],
    ['undefined', 'undefined', function () { return undefined; }],
    ['boolean', 'true', function () { return true; }],
    ['method-uncalled', 'text', function () { return text; }],
    ['function', 'function () {}', function () { return function () {}; }],
    ['java-object', 'new java.lang.Object()', function () { return new java.lang.Object(); }],
    ['uiobject', "desc('aj6pt-row').findOnce()", function () { return desc('aj6pt-row').findOnce(); }],
    ['select', "select('text=aj6pt-a')", function () { return select('text=aj6pt-a'); }],
    ['select-bool', "select('desc=aj6pt-row clickable')", function () { return select('desc=aj6pt-row clickable'); }],
];

function sectionForms() {
    FORMS.forEach(function (f) {
        ['w', '{}', '[]', '$', '?', '@'].forEach(function (ty) {
            rec('forms|' + f[0] + '|' + ty, 'pickup(' + f[1] + ", '" + ty + "')", function () { return pickup(f[2](), ty); });
        });
        rec('forms|' + f[0] + '|1arg', 'pickup(' + f[1] + ')', function () { return pickup(f[2]()); });
    });
}

function sectionRoots() {
    var box = desc('aj6pt-box').findOnce();
    var row = desc('aj6pt-row').findOnce();
    var cases = [
        ['box-content', "pickup(box, 'aj6pt-a')", function () { return pickup(box, 'aj6pt-a'); }],
        ['row-outside', "pickup(row, 'aj6pt-title')", function () { return pickup(row, 'aj6pt-title'); }],
        ['row-self', "pickup(row, { desc: 'aj6pt-row' })", function () { return pickup(row, { desc: 'aj6pt-row' }); }],
        ['row-compass', "pickup(row, { className: 'android.widget.Button' }, 'p')", function () { return pickup(row, { className: 'android.widget.Button' }, 'p'); }],
        ['row-result', "pickup(row, 'aj6pt-b', 'txt')", function () { return pickup(row, 'aj6pt-b', 'txt'); }],
        ['row-compass-result', "pickup(row, 'aj6pt-a', 's>1', 'txt')", function () { return pickup(row, 'aj6pt-a', 's>1', 'txt'); }],
        ['box-collection', "pickup(box, textStartsWith('aj6pt-item-'), '{}')", function () { return pickup(box, textStartsWith('aj6pt-item-'), '{}'); }],
        ['box-contents', "pickup(box, textStartsWith('aj6pt-item-'), 'p', '$[]')", function () { return pickup(box, textStartsWith('aj6pt-item-'), 'p', '$[]'); }],
        ['box-cb', "pickup(box, 'aj6pt-a', observe)", function () { return pickup(box, 'aj6pt-a', observe); }],
        ['box-result-cb', "pickup(box, 'aj6pt-a', 'txt', observe)", function () { return pickup(box, 'aj6pt-a', 'txt', observe); }],
        ['box-compass-cb', "pickup(box, 'aj6pt-a', 'p', observe)", function () { return pickup(box, 'aj6pt-a', 'p', observe); }],
        ['box-all', "pickup(box, 'aj6pt-a', 'p', 'txt', observe)", function () { return pickup(box, 'aj6pt-a', 'p', 'txt', observe); }],
        ['box-all-replace', "pickup(box, 'aj6pt-a', 'p', 'txt', replace)", function () { return pickup(box, 'aj6pt-a', 'p', 'txt', replace); }],
        ['null-five', "pickup(null, 'aj6pt-a', 'p', 'w', observe)", function () { return pickup(null, 'aj6pt-a', 'p', 'w', observe); }],
        ['undefined-five', "pickup(undefined, 'aj6pt-a', 'p', 'w', null)", function () { return pickup(undefined, 'aj6pt-a', 'p', 'w', null); }],
        ['string-five', "pickup('x', 'aj6pt-a', 'p', 'w', observe)", function () { return pickup('x', 'aj6pt-a', 'p', 'w', observe); }],
        ['row-as-selector', 'pickup(row)', function () { return pickup(row); }],
        ['row-row', 'pickup(row, row)', function () { return pickup(row, row); }],
        ['row-null-selector', 'pickup(row, null)', function () { return pickup(row, null); }],
        ['row-undefined-selector', "pickup(row, undefined, 'w')", function () { return pickup(row, undefined, 'w'); }],
        ['box-bfs', "pickup(box, textStartsWith('aj6pt').algorithm('BFS'), '$[]')", function () { return pickup(box, textStartsWith('aj6pt').algorithm('BFS'), '$[]'); }],
    ];
    cases.forEach(function (c) { rec('roots|' + c[0], c[1], c[2]); });
}

function sectionArity() {
    var row = desc('aj6pt-row').findOnce();
    var sel = function () { return text('aj6pt-a'); };
    var cases = [
        ['pickup0', 'pickup()', function () { return pickup(); }],
        ['pickup6', "pickup(row, sel, 'p', 'w', observe, 1)", function () { return pickup(row, sel(), 'p', 'w', observe, 1); }],
        ['compass-pass', "pickup(sel, '%')", function () { return pickup(sel(), '%'); }],
        ['compass-empty-2', "pickup(sel, '')", function () { return pickup(sel(), ''); }],
        ['compass-empty-3', "pickup(sel, '', 'txt')", function () { return pickup(sel(), '', 'txt'); }],
        ['compass-null-3', "pickup(sel, null, 'txt')", function () { return pickup(sel(), null, 'txt'); }],
        ['compass-undefined-3', "pickup(sel, undefined, 'txt')", function () { return pickup(sel(), undefined, 'txt'); }],
        ['compass-number', "pickup(sel, 1, 'txt')", function () { return pickup(sel(), 1, 'txt'); }],
        ['type-null-cb', 'pickup(sel, null, observe)', function () { return pickup(sel(), null, observe); }],
        ['cb-null-3', "pickup(sel, 'txt', null)", function () { return pickup(sel(), 'txt', null); }],
        ['cb-string', "pickup(sel, 'p', 'txt', 'x')", function () { return pickup(sel(), 'p', 'txt', 'x'); }],
        ['cb-object', "pickup(sel, 'txt', {})", function () { return pickup(sel(), 'txt', {}); }],
    ];
    ['p2', 'pp', 'p0', 'p-1', 'k', 'k1', 'c', 'c-1', 'c0>0', 's', 's1', 's-1', 's<1', 's>9', 'p4ppp12p', 'zz', 'p 1', 'P', 'pk'].forEach(function (c) {
        cases.push(['compass-' + c, "pickup(sel, '" + c + "')", function () { return pickup(sel(), c); }]);
        cases.push(['compass3-' + c, "pickup(sel, '" + c + "', 'txt')", function () { return pickup(sel(), c, 'txt'); }]);
    });
    cases.push(['detect0', 'detect()', function () { return detect(); }]);
    cases.push(['detect1', 'detect(row)', function () { return detect(row); }]);
    cases.push(['detect5', "detect(row, 'p', 'w', observe, 1)", function () { return detect(row, 'p', 'w', observe, 1); }]);
    cases.push(['detect-string', "detect('x', 'w')", function () { return detect('x', 'w'); }]);
    cases.push(['detect-number', "detect(42, 'w')", function () { return detect(42, 'w'); }]);
    cases.push(['detect-undefined', "detect(undefined, 'txt')", function () { return detect(undefined, 'txt'); }]);
    cases.push(['detect-selector', "detect(text('aj6pt-a'), 'txt')", function () { return detect(text('aj6pt-a'), 'txt'); }]);
    cases.push(['detect-compass-cb', "detect(row, 'c0', observe)", function () { return detect(row, 'c0', observe); }]);
    cases.push(['detect-type-cb', "detect(row, 'txt', observe)", function () { return detect(row, 'txt', observe); }]);
    cases.push(['detect-compass-type', "detect(row, 'c1', 'txt')", function () { return detect(row, 'c1', 'txt'); }]);
    cases.push(['detect-compass-pass', "detect(row, '%')", function () { return detect(row, '%'); }]);
    cases.push(['detect-compass-empty', "detect(row, '')", function () { return detect(row, ''); }]);
    cases.push(['detect-compass-number', "detect(row, 1, 'w')", function () { return detect(row, 1, 'w'); }]);
    cases.push(['detect-cb-string', "detect(row, 'p', 'w', 'x')", function () { return detect(row, 'p', 'w', 'x'); }]);
    cases.forEach(function (c) { rec('arity|' + c[0], c[1], c[2]); });
}

function sectionCallbacks() {
    var cases = [
        ['replace', "pickup(text('aj6pt-a'), 'txt', replace)", function () { return pickup(text('aj6pt-a'), 'txt', replace); }],
        ['null', "pickup(text('aj6pt-a'), 'txt', toNull)", function () { return pickup(text('aj6pt-a'), 'txt', toNull); }],
        ['replace-miss', "pickup(text('aj6pt-none'), 'w', replace)", function () { return pickup(text('aj6pt-none'), 'w', replace); }],
        ['replace-plural', "pickup(textStartsWith('aj6pt-item-'), '$[]', replace)", function () { return pickup(textStartsWith('aj6pt-item-'), '$[]', replace); }],
        ['throws', "pickup(text('aj6pt-a'), 'w', () => { throw Error('aj6pt') })", function () { return pickup(text('aj6pt-a'), 'w', function () { throw Error('aj6pt'); }); }],
        ['returns-zero', "pickup(text('aj6pt-a'), 'w', () => 0)", function () { return pickup(text('aj6pt-a'), 'w', function () { return 0; }); }],
        ['returns-false', "pickup(text('aj6pt-a'), '?', () => false)", function () { return pickup(text('aj6pt-a'), '?', function () { return false; }); }],
        ['arguments', "pickup(text('aj6pt-a'), 'txt', function () { return arguments.length })", function () { return pickup(text('aj6pt-a'), 'txt', function () { return arguments.length; }); }],
        ['detect-replace', "detect(row, 'p', 'txt', replace)", function () { return detect(desc('aj6pt-row').findOnce(), 'p', 'txt', replace); }],
        ['detect-throws', "detect(row, 'w', () => { throw Error('aj6pt') })", function () { return detect(desc('aj6pt-row').findOnce(), 'w', function () { throw Error('aj6pt'); }); }],
    ];
    cases.forEach(function (c) { rec('callbacks|' + c[0], c[1], c[2]); });
}

// Parameterized calls by name: in the interceptor table (child, setText, compass, performAction, setSelection ...) and
// out of it (bounds, hintText, unknown names). EDIT rows come last: setText changes the edit text.
var INVOKE = [
    ['one', 'text'], ['one', 'desc'], ['one', 'id'], ['one', 'className'], ['one', 'packageName'], ['one', 'bounds'],
    ['one', 'hintText'], ['one', 'getHintText'], ['one', 'isChecked'], ['one', 'checked'], ['one', 'childCount'],
    ['one', 'getChildCount'], ['one', 'parent'], ['one', 'children'], ['one', 'firstChild'], ['one', 'lastChild'],
    ['one', 'fooBar'], ['one', 'getFooBar'], ['one', 'Text'], ['one', 'TEXT'], ['one', 'getText'], ['one', 'isClickable'],
    ['one', ['child', 0]], ['one', ['child', 2]], ['one', ['child', '1']], ['one', ['child', 1.7]], ['one', ['child', -1]],
    ['one', ['child', 99]], ['one', ['child', NaN]], ['one', ['child']], ['one', ['child', 0, 1]], ['one', ['getChild', 0]],
    ['one', ['setText', 'x']], ['one', ['bounds']], ['one', ['hintText']], ['one', ['getHintText']],
    ['one', ['compass', 'p']], ['one', ['compass', 'c0']], ['one', ['compass', 'zz']], ['one', ['performAction', 16]],
    ['one', ['performAction', '16']], ['one', ['fooBar']], ['one', ['fooBar', 1]], ['one', ['text', 1]],
    ['one', ['findAccessibilityNodeInfosByText', 'aj6pt']], ['one', ['setChecked', true]], ['one', ['setClickable', 'x']],
    ['one', ['setProgress', 0.5]], ['one', ['scrollTo', 0, 0]], ['one', ['setSelection', 0, 1]], ['one', ['childCount', 1]],
    ['one', ['isChecked']], ['one', ['checked']], ['one', ['parent']], ['one', ['sibling', 1]], ['one', ['offset', 1]],
    ['none', 'text'], ['none', 'fooBar'], ['none', ['child', 0]], ['none', ['setText', 'x']], ['none', ['fooBar', 1]],
    ['none', ['bounds']], ['none', ['hintText']],
    ['edit', 'hintText'], ['edit', ['hintText']], ['edit', 'text'], ['edit', ['setSelection', 0, 2]], ['edit', ['setSelection', 0]],
    ['edit', ['setText', 'aj6pt-new']], ['edit', 'text'], ['edit', ['setText', 5]], ['edit', 'text'], ['edit', ['setText']],
];

function sectionInvoke() {
    var targets = {
        one: ["desc('aj6pt-row')", function () { return desc('aj6pt-row'); }],
        none: ["text('aj6pt-none')", function () { return text('aj6pt-none'); }],
        edit: ["className('android.widget.EditText')", function () { return className('android.widget.EditText'); }],
    };
    INVOKE.forEach(function (c, i) {
        var tg = targets[c[0]];
        rec('invoke|' + i + '|' + c[0] + '|' + lit(c[1]), 'pickup(' + tg[0] + ', ' + lit(c[1]) + ')', function () { return pickup(tg[1](), c[1]); });
    });
    var row = desc('aj6pt-row').findOnce();
    [['child', 1], ['compass', 'p'], 'bounds', 'hintText', 'fooBar', ['fooBar', 1], ['setText', 'x']].forEach(function (rt, i) {
        rec('invoke|detect|' + i + '|' + lit(rt), 'detect(row, ' + lit(rt) + ')', function () { return detect(row, rt); });
    });
}

// The public zero-argument methods of UiObject by name (the domain of NodeMethodRegistry, D11), minus those that block,
// dispatch gestures, recycle the node or are Object methods; static and internal (mangled) methods are left out.
var SWEEP_EXCLUDED = /^(recycle|wait.*|notify.*|getClass|hashCode|equals|toString|clickBounds|longClickBounds|.*Bounds.*Click.*|.*[Ss]creenshot.*|obtain)$/;

function sweepNames() {
    // Class.forName from the script sees no app classes; start from a node and walk up to UiObject itself.
    var cls = desc('aj6pt-row').findOnce().getClass();
    while (cls !== null && String(cls.getName()) !== 'org.autojs.autojs.core.automator.UiObject') cls = cls.getSuperclass();
    var methods = cls.getMethods();
    var names = {};
    for (var i = 0; i < methods.length; i++) {
        var m = methods[i];
        var name = String(m.getName());
        if (m.getParameterCount() !== 0) continue;
        if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
        if (name.indexOf('$') >= 0 || SWEEP_EXCLUDED.test(name)) continue;
        names[name] = true;
    }
    return Object.keys(names).sort();
}

// Node actions not performed on the hit (they run on the overlay of AutoJs6 itself): showTooltip on a view without a
// tooltip throws a NullPointerException in View.showLongClickTooltip on the main thread and kills the process (seen on
// 22120RN86C, API 33); hideTooltip and the drag, press-and-hold, suggestion, IME and paste actions are left out with
// it. They are still resolved by name on no hit.
var SWEEP_UNSAFE_ON_HIT = /^(showTooltip|hideTooltip|dragStart|dragDrop|dragCancel|pressAndHold|showTextSuggestions|imeEnter|paste)$/;

// Values that change with every new overlay window.
var SWEEP_MASKED = /^(getWindowId|windowId)$/;

function sectionSweep() {
    var names = sweepNames();
    result.sweepNames = names.length;
    result.sweepSkippedOnHit = names.filter(function (name) { return SWEEP_UNSAFE_ON_HIT.test(name); });
    names.forEach(function (name) {
        if (SWEEP_UNSAFE_ON_HIT.test(name)) return;
        rec('sweep|one|' + name, "pickup(desc('aj6pt-row'), '" + name + "')", function () { return pickup(desc('aj6pt-row'), name); }, SWEEP_MASKED.test(name));
    });
    names.forEach(function (name) {
        rec('sweep|none|' + name, "pickup(text('aj6pt-none'), '" + name + "')", function () { return pickup(text('aj6pt-none'), name); });
    });
}

function sectionExists() {
    var cases = [
        ['all-hit', "existsAll('aj6pt-a', 'aj6pt-b')", function () { return existsAll('aj6pt-a', 'aj6pt-b'); }],
        ['all-miss', "existsAll('aj6pt-a', 'aj6pt-none')", function () { return existsAll('aj6pt-a', 'aj6pt-none'); }],
        ['all-empty', 'existsAll()', function () { return existsAll(); }],
        ['all-forms', "existsAll(text('aj6pt-a'), { desc: 'aj6pt-row' }, /aj6pt-item/)", function () { return existsAll(text('aj6pt-a'), { desc: 'aj6pt-row' }, /aj6pt-item/); }],
        ['one-hit', "existsOne('aj6pt-none', 'aj6pt-b')", function () { return existsOne('aj6pt-none', 'aj6pt-b'); }],
        ['one-miss', "existsOne('aj6pt-none')", function () { return existsOne('aj6pt-none'); }],
        ['one-empty', 'existsOne()', function () { return existsOne(); }],
        ['one-bad', "existsOne({ fooBar: 1 })", function () { return existsOne({ fooBar: 1 }); }],
    ];
    cases.forEach(function (c) { rec('exists|' + c[0], c[1], c[2]); });
}

// How the process resolves the spelling '{}' (ROADMAP appendix C Q9): when the first use of PickResultType in the
// process is SelectorForms of the new layer (its static PLURAL set touches PickResultType.WidgetCollection before the
// companion), the alias table is built while WidgetCollection is still null, and the eight WidgetCollection spellings
// resolve to Invocation for the life of the process. Asked before any pickup of this script; when nothing used the
// class yet, this call initializes it correctly.
function collectionSpelling() {
    try {
        var type = org.autojs.autojs.core.automator.pick.PickResultType.Companion.of('{}');
        return String(type.getClass().getSimpleName());
    } catch (e) {
        return errorText(e);
    }
}

function run() {
    result.collectionSpelling = collectionSpelling();
    for (var i = 0; i < 100 && !auto.isRunning(); i++) sleep(100);
    if (!auto.isRunning()) {
        result.errors.push('the accessibility service of AutoJs6 is not running');
        return;
    }
    createOverlay();
    for (var k = 0; k < 50 && !desc('aj6pt-list').findOnce(); k++) sleep(100);
    sleep(500);
    var nodes = selector().find().size();
    result.nodes = nodes;
    if (!desc('aj6pt-list').findOnce() || nodes < 13) {
        result.errors.push('the overlay is not searchable (' + nodes + ' nodes)');
        return;
    }
    var sections = [sectionTypes, sectionDetect, sectionForms, sectionRoots, sectionArity, sectionCallbacks, sectionInvoke, sectionSweep, sectionExists];
    sections.forEach(function (s) {
        var before = result.rows.length;
        s();
        mark(s.name + ' ' + (result.rows.length - before));
    });
}

try {
    run();
} catch (e) {
    result.errors.push(errorText(e));
    mark('error ' + errorText(e));
} finally {
    try {
        auto.setWindowFilter(null);
    } catch (e) {
        // The filter belongs to this script runtime only.
    }
    if (overlay) {
        overlay.close();
    }
    files.write(OUT, JSON.stringify(result));
    mark('done');
}
