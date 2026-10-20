// Device check for docs/settings/ROADMAP.md P2.3 (E3): with no stored text size, a new native code editor starts with
// the default of the settings page (appendix C Q10), and a demo script opens in the code editor with the engine
// AJ6_ENGINE ("native" or "ace"). The first run prints the stored engine, selection mode and text size as a snapshot;
// with AJ6_RESTORE the snapshot is put back instead. Run by tools/settings/device_store_check.py ("editor").
// zh-CN: ROADMAP P2.3 的真机检查 (E3): 未存储字号时, 新建的原生代码编辑器以设置页的默认字号开始 (附录 C Q10), 并以 AJ6_ENGINE
// 引擎打开示例脚本. 首次运行输出引擎, 选择模式与字号的快照; 带 AJ6_RESTORE 时改为写回快照.

var MARK = 'AJ6SET';
var sp = org.autojs.autojs.core.pref.Pref.INSTANCE.get();
var K = org.autojs.autojs.core.pref.keys.EditorKeys;
var Engines = org.autojs.autojs.ui.edit.editor.engine;
var SIZE = String(K.EDITOR_TEXT_SIZE.getName());
var NAMES = [String(K.EDITOR_ENGINE.getName()), String(K.EDITOR_ENGINE_SELECTION_MODE.getName()), SIZE];

function report(id, ok, detail) {
    console.log(MARK + ' ' + (ok ? 'PASS' : 'FAIL') + ' ' + id + (detail === undefined ? '' : ' ' + detail));
}

function onMain(fn) {
    var result = { value: null, error: null };
    var latch = new java.util.concurrent.CountDownLatch(1);
    new android.os.Handler(android.os.Looper.getMainLooper()).post(new java.lang.Runnable({
        run: function () {
            try {
                result.value = fn();
            } catch (e) {
                result.error = String(e);
            }
            latch.countDown();
        },
    }));
    latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
    if (result.error !== null) throw new Error(result.error);
    return result.value;
}

if (typeof AJ6_RESTORE !== 'undefined') {
    var editor = sp.edit();
    NAMES.forEach(function (name) {
        var v = AJ6_RESTORE[name];
        if (v === null || v === undefined) editor.remove(name);
        else if (name === SIZE) editor.putInt(name, v);
        else editor.putString(name, String(v));
    });
    report('restore', editor.commit());
} else {
    if (AJ6_SNAPSHOT) {
        var all = sp.getAll();
        var snapshot = {};
        NAMES.forEach(function (name) {
            var v = all.get(name);
            snapshot[name] = v === null || v === undefined ? null : name === SIZE ? Number(v) : String(v);
        });
        console.log(MARK + ' snapshot ' + encodeURIComponent(JSON.stringify(snapshot)));
    }
    K.EDITOR_TEXT_SIZE.remove(true);
    try {
        var sizes = onMain(function () {
            var themed = new android.view.ContextThemeWrapper(context, org.autojs.autojs6.R.style.AppTheme);
            var native = new org.autojs.autojs.ui.edit.editor.CodeEditor(themed);
            var sp2px = context.getResources().getDisplayMetrics().scaledDensity;
            return [native.getLastTextSize(), Math.round(native.getCodeEditText().getTextSize() / sp2px)];
        });
        report('native-initial-text-size', String(sizes[0]) === '14' && String(sizes[1]) === '14', 'lastTextSize ' + sizes[0] + ', text size ' + sizes[1] + ' sp');
    } catch (e) {
        report('native-initial-text-size', false, 'threw ' + e);
    }
    var type = Engines.EditorEngineType.valueOf(String(AJ6_ENGINE).toUpperCase());
    Engines.EditorEnginePreferences.set(type);
    var demo = new java.io.File(files.cwd(), 'aj6_editor_demo.js');
    files.write(demo.getPath(), '// AutoJs6 settings check (docs/settings/ROADMAP.md P2.3)\nconsole.log("text size " + 14);\n');
    org.autojs.autojs.ui.edit.EditActivity.editFile(context, demo.getPath(), true);
    report('opened-' + AJ6_ENGINE, true, 'effective engine ' + Engines.EditorEnginePreferences.get() + ', stored text size ' + sp.getAll().get(SIZE));
}
console.log(MARK + ' done');
