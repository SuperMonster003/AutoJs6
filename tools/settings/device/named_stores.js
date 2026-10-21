// Device check for docs/settings/ROADMAP.md P3 (E3): the stores and keys moved to the declared layer read what the old
// code read, and one item changed per group survives a restart of the app. Run twice by
// tools/settings/device_store_check.py ("stores"), which force-stops the app in between:
//   AJ6_PHASE 'change': compares the declared keys and stores of each group in AJ6_GROUPS with the raw SharedPreferences
//     of the same file, records every raw value it is going to change ("state" line) and changes one item per group
//     through the moved readers / writers;
//   AJ6_PHASE 'verify' (AJ6_STATE = the recorded state): the changed items read back as changed, through the moved
//     readers and as raw values; then every recorded value is put back ("restore").
// Prints "AJ6SET <PASS|FAIL> <id> <detail>", "AJ6SET info ...", "AJ6SET state <json>" and "AJ6SET done"; the lines go to
// RESULT_FILE as well. No stored value is printed (R6): details name keys and counts only.
// zh-CN: ROADMAP P3 的真机检查 (E3): 迁移到声明层的存储与键读到旧代码所读的值, 每组改动一项后重启应用仍然保持. 由
// device_store_check.py 运行两次, 中间强制停止应用: 'change' 阶段核对并记录将要改动的原始值后改动一项; 'verify' 阶段核对改动
// 已保持, 再恢复全部记录的值. 不输出任何存储值 (R6).

var Keys = org.autojs.autojs.core.pref.keys;
var SettingsStores = org.autojs.autojs.core.pref.store.SettingsStores;
var MARK = 'AJ6SET';
var RESULT_FILE = files.join(files.cwd(), 'aj6set-result.txt');
var resultLines = [];
var state = AJ6_PHASE === 'verify' ? AJ6_STATE : { records: [], expected: {} };

function emit(line) {
    console.log(MARK + ' ' + line);
    resultLines.push(line);
}

function report(id, ok, detail) {
    emit((ok ? 'PASS' : 'FAIL') + ' ' + id + (detail === undefined ? '' : ' ' + detail));
}

function info(text) {
    emit('info ' + text);
}

function check(id, fn) {
    try {
        var r = fn();
        if (r === true) report(id, true);
        else report(id, false, r === false ? '' : String(r));
    } catch (e) {
        report(id, false, 'threw ' + (e && e.javaException ? e.javaException.getClass().getName() + ': ' + e.javaException.getMessage() : e));
    }
}

function same(a, b) {
    return String(a) === String(b);
}

function prefs(storeName) {
    return storeName === 'default'
        ? org.autojs.autojs.core.pref.Pref.INSTANCE.get()
        : context.getSharedPreferences(storeName, android.content.Context.MODE_PRIVATE);
}

// The raw value of [name] as plain data: null, or {t: tag, v: value} with a string set as a sorted array.
function rawOf(storeName, name, tag) {
    var sp = prefs(storeName);
    if (!sp.contains(name)) return null;
    switch (tag) {
        case 'bool': return { t: tag, v: sp.getBoolean(name, false) === true };
        case 'int': return { t: tag, v: Number(sp.getInt(name, 0)) };
        case 'long': return { t: tag, v: String(sp.getLong(name, 0)) };
        case 'float': return { t: tag, v: Number(sp.getFloat(name, 0)) };
        case 'stringSet': {
            var items = [];
            var it = sp.getStringSet(name, null).iterator();
            while (it.hasNext()) items.push(String(it.next()));
            return { t: tag, v: items.sort() };
        }
        default: return { t: tag, v: String(sp.getString(name, null)) };
    }
}

function sameRaw(a, b) {
    return JSON.stringify(a) === JSON.stringify(b);
}

// Records the raw value of [name] before a group changes it (change phase only, once per name).
function record(storeName, name, tag) {
    if (AJ6_PHASE !== 'change') return;
    var exists = state.records.some(function (r) { return r.store === storeName && r.name === name; });
    if (!exists) state.records.push({ store: storeName, name: name, tag: tag, before: rawOf(storeName, name, tag) });
}

function putRaw(editor, name, raw) {
    if (raw === null) return editor.remove(name);
    switch (raw.t) {
        case 'bool': return editor.putBoolean(name, raw.v === true);
        case 'int': return editor.putInt(name, raw.v);
        case 'long': return editor.putLong(name, java.lang.Long.parseLong(raw.v));
        case 'float': return editor.putFloat(name, raw.v);
        case 'stringSet': {
            var set = new java.util.LinkedHashSet();
            raw.v.forEach(function (s) { set.add(s); });
            return editor.putStringSet(name, set);
        }
        default: return editor.putString(name, raw.v);
    }
}

function restoreAll() {
    var byStore = {};
    state.records.forEach(function (r) { (byStore[r.store] = byStore[r.store] || []).push(r); });
    var bad = [];
    Object.keys(byStore).forEach(function (storeName) {
        var editor = prefs(storeName).edit();
        byStore[storeName].forEach(function (r) { putRaw(editor, r.name, r.before); });
        if (!editor.commit()) bad.push(storeName + ' commit');
        byStore[storeName].forEach(function (r) {
            if (!sameRaw(rawOf(storeName, r.name, r.tag), r.before)) bad.push(storeName + ':' + r.name);
        });
    });
    return bad.length === 0 || 'not restored: ' + bad.join(' ');
}

// Declared key vs. the raw value read with the getter of its type and its default (what the old readers did).
function compareKey(id, k, oldRead) {
    check(id + '-read-' + k.getName(), function () {
        var actual = k.getOrNull();
        var expected = oldRead();
        return same(actual, expected) || 'declared and raw readings differ';
    });
}

// ------------------------------------------------------------------------------------------------------- groups

var GROUPS = {
    // P3.6 (appendix C Q15): the literal keys of the default preferences; the derived theme colors switch and the dex
    // compiler selection are changed.
    q15: function () {
        var A = Keys.AppearanceKeys, E = Keys.ExtensibilityKeys, D = Keys.DeveloperKeys;
        var sp = prefs('default');
        var ThemeColorManager = org.autojs.autojs.theme.ThemeColorManager.INSTANCE;
        var Dex = org.autojs.autojs.core.plugin.dex.DexCompilerOptInStore.INSTANCE;
        var DexComponent = org.autojs.autojs.core.plugin.dex.DexCompilerComponent;
        var DexCodec = org.autojs.autojs.core.plugin.dex.DexCompilerComponentCodec.INSTANCE;
        var R8 = org.autojs.autojs.core.plugin.r8.R8CompilerOptInStore.INSTANCE;
        var probe = new DexComponent('org.autojs.settings.probe', 'org.autojs.settings.probe.CompilerService');
        if (AJ6_PHASE === 'change') {
            [A.THEME_COLOR_DERIVED_ACCENT_AND_DARK, A.COLOR_HISTORY_MERGE_SIMILAR, D.DEX_COMPILER_EXPERIMENTAL_ENABLED, D.R8_COMPILER_EXPLICIT_ENABLED].forEach(function (k) {
                compareKey('q15', k, function () { return sp.getBoolean(k.getName(), false); });
            });
            [D.DEX_COMPILER_EXPERIMENTAL_COMPONENT, D.R8_COMPILER_EXPLICIT_COMPONENT].forEach(function (k) {
                compareKey('q15', k, function () { return sp.getString(k.getName(), null); });
            });
            check('q15-stove-root-configuration-text', function () {
                return same(E.threeStoveAgentScriptRootConfigurationText(), sp.getString(E.THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION.getName(), '{}')) || 'text differs';
            });
            check('q15-theme-derivation-reader', function () {
                return same(ThemeColorManager.isDerivationEnabled(), sp.getBoolean(A.THEME_COLOR_DERIVED_ACCENT_AND_DARK.getName(), false)) || 'reader differs';
            });
            check('q15-r8-selection-reader', function () {
                var options = R8.read();
                return same(options.getEnabled(), D.R8_COMPILER_EXPLICIT_ENABLED.get() && options.getSelectedComponent() !== null) || 'r8 enabled differs';
            });
            check('q15-change', function () {
                record('default', A.THEME_COLOR_DERIVED_ACCENT_AND_DARK.getName(), 'bool');
                record('default', D.DEX_COMPILER_EXPERIMENTAL_ENABLED.getName(), 'bool');
                record('default', D.DEX_COMPILER_EXPERIMENTAL_COMPONENT.getName(), 'string');
                var derived = !ThemeColorManager.isDerivationEnabled();
                ThemeColorManager.setDerivationEnabled(derived);
                // The switch is turned off right after the selection, so no compile uses the probe component.
                Dex.select(probe);
                Dex.disable();
                state.expected.q15 = { derived: derived, component: String(DexCodec.encode(probe)) };
                return true;
            });
        } else {
            var expected = state.expected.q15;
            check('q15-theme-derivation-after-restart', function () {
                return same(ThemeColorManager.isDerivationEnabled(), expected.derived)
                    && same(sp.getBoolean(A.THEME_COLOR_DERIVED_ACCENT_AND_DARK.getName(), !expected.derived), expected.derived) || 'derivation switch lost';
            });
            check('q15-dex-selection-after-restart', function () {
                var options = Dex.read();
                if (options.getEnabled()) return 'enabled after disable';
                if (!same(options.getSelectedComponent(), probe)) return 'component lost';
                return same(sp.getString(D.DEX_COMPILER_EXPERIMENTAL_COMPONENT.getName(), null), expected.component) || 'raw component differs';
            });
        }
    },
};

info('phase ' + AJ6_PHASE + ', groups ' + AJ6_GROUPS.join(','));
AJ6_GROUPS.forEach(function (name) {
    if (!GROUPS[name]) return report('group-' + name, false, 'unknown group');
    GROUPS[name]();
});
if (AJ6_PHASE === 'change') {
    emit('state ' + encodeURIComponent(JSON.stringify(state)));
} else {
    check('restore', restoreAll);
}
resultLines.push('done');
files.write(RESULT_FILE, resultLines.join(String.fromCharCode(10)));
console.log(MARK + ' done');
