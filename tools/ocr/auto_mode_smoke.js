// Run through auto_mode_smoke.py on a test device with trusted OCR plugins installed.
// Plugin Center enablement preferences are restored even if an assertion fails.
var receipt = { checks: [], observations: [], complete: false, ok: false };
var output = new java.io.File(context.getExternalFilesDir(null), '__RECEIPT__').getAbsolutePath();
var store = Packages.org.autojs.autojs.core.plugin.center.PluginEnableStore.INSTANCE;
var prefs = context.getSharedPreferences('plugin_center_enable_state', 0);
var priority = ['mlkit', 'paddle', 'rapid'];
var packages = [];
var worker;
var observing = false;
var image;

function check(value, label) {
    if (!value) throw Error(label);
    receipt.checks.push(label);
}
function fails(fn, label) {
    var error;
    try { fn(); } catch (e) { error = String(e); }
    check(!!error, label);
    return error;
}
function setEngines(engines) {
    packages.forEach(function (p) {
        store.setEnabled(context, p.name, engines.indexOf(p.engine) >= 0);
    });
}
function save() { files.write(output, JSON.stringify(receipt)); }
function textWith(options) { return ocr(image, options).join(' '); }
function waitForMode(mode, fromIndex) {
    var deadline = Date.now() + 5000;
    while (Date.now() < deadline) {
        if (receipt.observations.indexOf(mode, fromIndex || 0) >= 0) return;
        sleep(50);
    }
    throw Error('Running mode loop did not observe ' + JSON.stringify(mode));
}

try {
    var intent = new android.content.Intent('org.autojs.plugin.PADDLE_OCR');
    intent.addFlags(android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
    var candidates = context.getPackageManager().queryIntentServices(intent, android.content.pm.PackageManager.GET_RESOLVED_FILTER);
    for (var i = 0; i < candidates.size(); i++) {
        var candidate = candidates.get(i);
        var name = String(candidate.serviceInfo.packageName);
        for (var j = 0; j < priority.length; j++) {
            if (candidate.filter && candidate.filter.hasCategory(priority[j] + '-ocr')) {
                var key = 'key_$_enabled_plugin_$_' + name;
                packages.push({ name: name, engine: priority[j], key: key, contained: prefs.contains(key), enabled: prefs.getBoolean(key, true) });
                break;
            }
        }
    }
    receipt.packages = packages;
    var installed = priority.filter(function (engine) { return packages.some(function (p) { return p.engine === engine; }); });
    check(installed.length > 0, 'OCR fixture plugins are installed');
    if (__REQUIRE_ALL__) check(installed.length === 3, 'All three engine families are installed');
    var expectedInitial = priority.filter(function (engine) { return packages.some(function (p) { return p.engine === engine && p.enabled; }); })[0] || '';
    check(ocr.mode === expectedInitial, 'Default mode is a resolved primitive string');

    var bitmap = android.graphics.Bitmap.createBitmap(640, 160, android.graphics.Bitmap.Config.ARGB_8888);
    var canvas = new android.graphics.Canvas(bitmap);
    canvas['drawColor(int)'](android.graphics.Color.WHITE);
    var paint = new android.graphics.Paint();
    paint['setColor(int)'](android.graphics.Color.BLACK);
    paint.setAntiAlias(true);
    paint.setTextSize(48);
    canvas.drawText('Hello AutoJs6', 30, 90, paint);
    image = Packages.org.autojs.autojs.core.image.ImageWrapper.ofBitmap(runtime, bitmap);

    setEngines([]);
    check(ocr.mode === '', 'No enabled plugins yields an empty string');
    var missing = fails(function () { ocr(); }, 'Automatic OCR without plugins fails before screen capture');
    check(missing.indexOf('ML Kit OCR / Paddle OCR / Rapid OCR') >= 0, 'Unavailable error identifies all supported families');
    var disposable = images.copy(image).oneShot();
    fails(function () { ocr(disposable); }, 'Automatic OCR reports missing plugins for a one-shot image');
    check(disposable.isRecycled(), 'Early selection failure releases a one-shot image');
    var uiMode;
    ui.run(function () { uiMode = ocr.mode; });
    check(uiMode === '', 'Reading auto mode on the UI thread does not bind plugins');

    for (var k = installed.length - 1; k >= 0; k--) {
        var enabled = installed.slice(k);
        setEngines(enabled);
        check(ocr.mode === enabled[0], 'Engine priority selects ' + enabled[0]);
        var text = textWith({});
        check(text.indexOf('Hello') >= 0, 'Automatic recognition succeeds with ' + enabled[0] + ': ' + text);
    }

    var high = installed[Math.max(0, installed.length - 2)];
    var low = installed[installed.length - 1];
    setEngines([high, low]);
    observing = true;
    worker = threads.start(function () {
        while (observing) {
            var mode = ocr.mode;
            receipt.observations.push(mode);
            console.log(mode);
            sleep(300);
        }
    });
    waitForMode(high);
    setEngines(high === low ? [] : [low]);
    waitForMode(high === low ? '' : low);
    var beforeEnable = receipt.observations.length;
    setEngines([high, low]);
    waitForMode(high, beforeEnable);
    observing = false;
    worker.join(2000);
    check(true, 'The same running 300 ms loop observes disable and enable immediately');

    setEngines([low]);
    ocr.mode = high;
    setEngines([]);
    check(ocr.mode === high, 'Explicit mode stays fixed when its plugin is disabled');
    fails(function () { ocr(image); }, 'Explicit mode does not silently fall back');
    setEngines([low]);
    var resets = ['auto', '', null, undefined, 'AUTO'];
    resets.forEach(function (value) {
        ocr.mode = high;
        ocr.mode = value;
        check(ocr.mode === low, 'mode reset: ' + String(value));
        ocr.tap(high);
        ocr.tap(value);
        check(ocr.mode === low, 'tap reset: ' + String(value));
    });
    installed.forEach(function (mode) {
        ocr.tap(ocr[mode]);
        check(ocr.mode === mode, 'tap accepts the ' + mode + ' engine object');
    });
    ocr.mode = high;
    ['invalid-engine', 0, false, {}].forEach(function (value) {
        fails(function () { ocr.mode = value; }, 'Invalid mode rejected: ' + String(value));
        check(ocr.mode === high, 'Invalid assignment preserves the previous mode');
    });
    fails(function () { ocr.tap(); }, 'tap still requires one argument');
    check(ocr.mode === high, 'Invalid tap arity preserves the previous mode');
    resets.forEach(function (value) {
        check(textWith({mode: value}).indexOf('Hello') >= 0, 'Per-call automatic reset: ' + String(value));
        check(ocr.mode === high, 'Per-call mode does not change the script mode');
    });
    ocr.mode = 'auto';
    setEngines(installed);
    check(textWith({engine: low + '-ocr'}).indexOf('Hello') >= 0, 'Automatic selection honors an explicit engine filter');
    if (packages.some(function (p) { return p.name === 'io.github.supermonster003.autojs6.plugin.rapidocr.ppocrv6'; })) {
        check(textWith({engine: 'rapid-ocr', variant: 'v6'}).indexOf('Hello') >= 0, 'Automatic selection retains Rapid variant aliases');
    }
    check(ocr.mode === installed[0], 'Per-call engine filter does not pin the default mode');
    var result = ocr.detect(image, {mode: 'auto'});
    check(result.length > 0 && result[0].bounds.width() > 0, 'Automatic detect retains result bounds');
    check(ocr.summary().indexOf('Mode selection: auto') >= 0, 'summary identifies automatic selection');

    if (__HOT_SWAP__) {
        setEngines(installed.filter(function (mode) { return mode !== 'mlkit'; }));
        check(ocr.mode === 'paddle', 'Hot uninstall fixture starts with Paddle before Rapid');
        receipt.phase = 'hot-swap-ready';
        save();
        var until = Date.now() + 90000;
        var sawRapid = false;
        while (Date.now() < until) {
            var current = ocr.mode;
            receipt.observations.push(current);
            if (current === 'rapid') {
                if (!sawRapid) check(textWith({}).indexOf('Hello') >= 0, 'Automatic OCR works with Rapid after Paddle is uninstalled');
                sawRapid = true;
                receipt.phase = 'rapid-after-uninstall';
            }
            if (sawRapid && current === 'paddle') break;
            save();
            sleep(300);
        }
        check(sawRapid && ocr.mode === 'paddle', 'Same script switches Paddle to Rapid on uninstall and back on reinstall');
        check(textWith({}).indexOf('Hello') >= 0, 'Automatic OCR works with Paddle after reinstall');
    }
    receipt.ok = true;
} catch (e) {
    receipt.error = String(e) + '\n' + e.stack;
} finally {
    observing = false;
    if (worker && worker.isAlive()) { worker.interrupt(); worker.join(2000); }
    if (image) image.recycle();
    packages.forEach(function (p) {
        store.setEnabled(context, p.name, p.enabled);
        if (!p.contained) prefs.edit().remove(p.key).commit();
    });
    receipt.complete = true;
    save();
}
