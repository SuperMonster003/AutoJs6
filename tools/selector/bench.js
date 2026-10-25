// Selector benchmark of one screen (ROADMAP selector P0.1, rerun by P2.4 / P3): driven by device_bench.py, which opens
// the screen first and writes the prelude `var AJ6SEL = {...};` (scenario, expected package, rounds, output file).
//
// Scenarios: 'settings' (home page of the system settings), 'main' (AutoJs6 main screen), 'overlay' (a raw floaty window
// with a progress bar, a label and 300 TextViews above the home screen; searched through the window filter
// { packageName: AutoJs6 }). Items, each [warmup] runs and then [rounds] timed runs (milliseconds):
//   full      selector().find().size(), the whole tree
//   miss      text('__aj6sel_none__').findOnce()
//   missId    id('aj6sel_none').findOnce() (the framework fast path of P2.3 starts with id / text)
//   missMixed textMatch(/^__aj6sel_none__$/).clickable(true).findOnce() (regex declared before a boolean: P2.2 order)
//   hit       packageName(<screen package>).findOnce(), the root itself (an early hit)
//   late      text(<last unique text in DFS order>).findOnce() (a late hit with a single candidate)
//   findOne600 text('__aj6sel_none__').findOne(600): attempts (auto.stats) and elapsed time
//   cpu*      text('__aj6sel_none__').findOne(cpuMs) polled for cpuMs: process CPU (percent of one core) and attempts,
//             fixed interval (the default) and with the eventAssistedPolling flag; cpuIdle is the process without a
//             query (the floor). The overlay scenario repeats them with the progress bar updated every 16 ms (storm).
// The result is written as JSON to AJ6SEL.out; the log has only progress marks, no node text.

var CFG = typeof AJ6SEL === 'object' ? AJ6SEL : {};
var PKG = 'org.autojs.autojs6';
var SCENARIO = CFG.scenario || 'main';
var EXPECT = CFG.expectPackage || (SCENARIO === 'settings' ? 'com.android.settings' : PKG);
var ROUNDS = CFG.rounds || 10;
var WARMUP = CFG.warmup == null ? 5 : CFG.warmup;
var CPU_MS = CFG.cpuMs || 10000;
var CPU_ROUNDS = CFG.cpuRounds || ROUNDS;
var IDLE_ROUNDS = CFG.idleRounds || 3;
var OVERLAY_TEXTS = CFG.overlayTexts || 300;
var OUT = CFG.out || '/sdcard/Download/aj6-selector/bench.out.json';
var NONE = '__aj6sel_none__';

var result = {
    script: 'bench.js',
    scenario: SCENARIO,
    expectPackage: EXPECT,
    rounds: ROUNDS,
    warmup: WARMUP,
    cpuMs: CPU_MS,
    cpuRounds: CPU_ROUNDS,
    idleRounds: IDLE_ROUNDS,
    items: {},
    errors: [],
};

function mark(s) {
    console.log('AJ6SEL ' + s);
}

function nowNs() {
    return java.lang.System.nanoTime();
}

function round3(x) {
    return Math.round(x * 1000) / 1000;
}

function median(xs) {
    var v = xs.slice().sort(function (a, b) { return a - b; });
    if (v.length === 0) return null;
    var n = v.length;
    return n % 2 ? v[(n - 1) / 2] : round3((v[n / 2 - 1] + v[n / 2]) / 2);
}

function errorText(e) {
    var s = String(e && e.message !== undefined ? e.name + ': ' + e.message : e);
    return s.length > 200 ? s.slice(0, 200) : s;
}

// Times [fn] [rounds] times after [warmup] untimed runs; [check] judges the values returned by the timed runs.
function timed(name, fn, check, extra) {
    var item = { ok: false, samples: [], median: null };
    try {
        for (var w = 0; w < WARMUP; w++) fn();
        var values = [];
        for (var i = 0; i < ROUNDS; i++) {
            var t0 = nowNs();
            var v = fn();
            item.samples.push(round3((nowNs() - t0) / 1e6));
            values.push(v);
        }
        item.median = median(item.samples);
        item.ok = check(values, item);
        if (extra) extra(values, item);
    } catch (e) {
        item.error = errorText(e);
    }
    result.items[name] = item;
    mark(name + ' ' + (item.ok ? 'PASS' : 'FAIL') + ' ' + item.median);
    return item;
}

function allNull(values) {
    return values.every(function (v) { return v === null || v === undefined; });
}

function noneNull(values) {
    return values.every(function (v) { return v !== null && v !== undefined; });
}

function cpuTime() {
    return android.os.Process.getElapsedCpuTime();
}

function realtime() {
    return android.os.SystemClock.elapsedRealtime();
}

// One poll of cpuMs: [query] true runs findOne(cpuMs) on a selector that never matches, false only sleeps.
function cpuItem(name, rounds, query, flags) {
    var item = { ok: false, cpu: [], attempts: [], elapsed: [], medianCpu: null, medianAttempts: null };
    try {
        auto.setFlags(flags || []);
        for (var i = 0; i < rounds; i++) {
            auto.stats.reset();
            var c0 = cpuTime();
            var t0 = realtime();
            var found = query ? text(NONE).findOne(CPU_MS) : (sleep(CPU_MS), null);
            var t1 = realtime();
            var c1 = cpuTime();
            if (found !== null && found !== undefined) throw Error('the never-matching selector matched');
            item.cpu.push(Math.round((c1 - c0) * 1000 / (t1 - t0)) / 10);
            item.elapsed.push(t1 - t0);
            if (query) item.attempts.push(Number(auto.stats.queries.attempts));
        }
        item.medianCpu = median(item.cpu);
        item.medianAttempts = query ? median(item.attempts) : null;
        item.ok = true;
    } catch (e) {
        item.error = errorText(e);
    } finally {
        auto.setFlags([]);
    }
    result.items[name] = item;
    mark(name + ' ' + (item.ok ? 'PASS' : 'FAIL') + ' cpu ' + item.medianCpu + '% attempts ' + item.medianAttempts);
    return item;
}

function waitService() {
    for (var i = 0; i < 100 && !auto.isRunning(); i++) sleep(100);
    return auto.isRunning();
}

var overlay = null;
var storm = { on: false, updates: 0, thread: null };

function createOverlay() {
    overlay = floaty.rawWindow('<frame bg="#ffffffff"><vertical id="list"/></frame>');
    var done = threads.disposable();
    ui.run(function () {
        try {
            overlay.setSize(-1, -1);
            var list = overlay.list;
            var ctx = list.getContext();
            var bar = new android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            list.addView(bar, new android.widget.LinearLayout.LayoutParams(-1, -2));
            // A label of fixed size: its text changes invalidate it without a relayout of the 300 TextViews.
            var label = new android.widget.TextView(ctx);
            label.setText('0%');
            list.addView(label, new android.widget.LinearLayout.LayoutParams(-1, 48));
            for (var i = 0; i < OVERLAY_TEXTS; i++) {
                var tv = new android.widget.TextView(ctx);
                tv.setText('item ' + i);
                tv.setTextSize(4);
                list.addView(tv);
            }
            storm.bar = bar;
            storm.label = label;
            done.setAndNotify(true);
        } catch (e) {
            done.setAndNotify(errorText(e));
        }
    });
    var state = done.blockedGet();
    if (state !== true) throw Error('overlay: ' + state);
    auto.setWindowFilter({ packageName: PKG });
}

function startStorm() {
    storm.on = true;
    storm.thread = threads.start(function () {
        var i = 0;
        while (storm.on) {
            var p = i % 101;
            ui.post(function () {
                storm.bar.setProgress(p);
                storm.label.setText(p + '%');
            });
            storm.updates++;
            i++;
            sleep(16);
        }
    });
}

function stopStorm() {
    storm.on = false;
    if (storm.thread) {
        storm.thread.join(2000);
    }
}

function rootPackage() {
    var root = auto.root;
    return root ? String(root.packageName()) : null;
}

function lastUniqueText() {
    var all = selector().find();
    var counts = {};
    var texts = [];
    for (var i = 0; i < all.size(); i++) {
        var t = String(all.get(i).text() || '');
        texts.push(t);
        if (t) counts[t] = (counts[t] || 0) + 1;
    }
    for (var j = texts.length - 1; j >= 0; j--) {
        if (texts[j] && counts[texts[j]] === 1) return { text: texts[j], index: j, total: texts.length };
    }
    return null;
}

function bench() {
    if (!waitService()) {
        result.errors.push('the accessibility service of AutoJs6 is not running');
        mark('service FAIL');
        return;
    }
    if (SCENARIO === 'overlay') {
        createOverlay();
    }
    // RunIntentActivity is a transparent activity of its own task; wait until the screen of the scenario is active.
    var pkg = null;
    for (var i = 0; i < 50; i++) {
        sleep(200);
        pkg = rootPackage();
        if (i >= 7 && pkg === EXPECT) break;
    }
    result.rootPackage = pkg;
    var nodes = selector().find().size();
    result.nodes = nodes;
    var scenarioOk = pkg === EXPECT && nodes > 0 && (SCENARIO !== 'overlay' || nodes >= OVERLAY_TEXTS + 2);
    result.items.scenario = { ok: scenarioOk };
    mark('scenario ' + (scenarioOk ? 'PASS' : 'FAIL') + ' nodes ' + nodes);
    if (!scenarioOk) return;

    timed('full', function () { return selector().find().size(); }, function (values, item) {
        item.sizes = values.filter(function (v, k) { return values.indexOf(v) === k; });
        return values.every(function (v) { return v > 0; });
    });
    timed('miss', function () { return text(NONE).findOnce(); }, allNull);
    timed('missId', function () { return id('aj6sel_none').findOnce(); }, allNull);
    timed('missMixed', function () { return textMatch(/^__aj6sel_none__$/).clickable(true).findOnce(); }, allNull);
    timed('hit', function () { return packageName(EXPECT).findOnce(); }, noneNull);
    var late = lastUniqueText();
    if (late) {
        result.late = { index: late.index, total: late.total, length: late.text.length };
        timed('late', function () { return text(late.text).findOnce(); }, noneNull);
    } else {
        result.items.late = { ok: false, error: 'no unique text on the screen' };
        mark('late FAIL');
    }

    var f600 = { ok: false, samples: [], attempts: [], median: null, medianAttempts: null };
    try {
        text(NONE).findOne(600);
        for (var r = 0; r < ROUNDS; r++) {
            auto.stats.reset();
            var t0 = nowNs();
            var v = text(NONE).findOne(600);
            f600.samples.push(round3((nowNs() - t0) / 1e6));
            f600.attempts.push(Number(auto.stats.queries.attempts));
            if (v !== null && v !== undefined) throw Error('the never-matching selector matched');
        }
        f600.median = median(f600.samples);
        f600.medianAttempts = median(f600.attempts);
        f600.ok = true;
    } catch (e) {
        f600.error = errorText(e);
    }
    result.items.findOne600 = f600;
    mark('findOne600 ' + (f600.ok ? 'PASS' : 'FAIL') + ' attempts ' + f600.medianAttempts + ' ms ' + f600.median);

    cpuItem('cpuIdle', IDLE_ROUNDS, false);
    cpuItem('cpuFixed', CPU_ROUNDS, true);
    cpuItem('cpuAssisted', CPU_ROUNDS, true, ['eventAssistedPolling']);

    if (SCENARIO === 'overlay') {
        startStorm();
        sleep(1000);
        var u0 = storm.updates;
        var s0 = realtime();
        cpuItem('stormIdle', IDLE_ROUNDS, false);
        cpuItem('stormFixed', CPU_ROUNDS, true);
        cpuItem('stormAssisted', CPU_ROUNDS, true, ['eventAssistedPolling']);
        result.stormUpdatesPerSecond = Math.round((storm.updates - u0) * 10000 / (realtime() - s0)) / 10;
        stopStorm();
    }
}

try {
    bench();
} catch (e) {
    result.errors.push(errorText(e));
    mark('error ' + errorText(e));
} finally {
    stopStorm();
    try {
        auto.setWindowFilter(null);
    } catch (e) {
        // The service may be gone; the filter belongs to this script runtime only.
    }
    if (overlay) {
        overlay.close();
    }
    files.write(OUT, JSON.stringify(result));
    mark('done');
    threads.shutDownAll();
}
