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

var Registry = org.autojs.autojs.core.pref.SettingsRegistry;
var NamedStores = Keys.NamedStores;

function storeId(storeName) {
    var all = NamedStores.ALL;
    for (var i = 0; i < all.size(); i++) if (String(all.get(i).getName()) === storeName) return all.get(i);
    throw new Error('store not declared: ' + storeName);
}

// The declared key of [name] in the store [id]: a key declared on its own, else the member of its key family.
function declaredKeyOf(name, id) {
    var k = Registry.find(name, id);
    if (k) return k;
    var family = Registry.findFamily(name, id);
    return family ? family.key(family.suffixOf(name)) : null;
}

// Every raw entry of a named store (or the entries of the default store that [filter] accepts) is declared and reads
// the same through its declared key as through the raw SharedPreferences: the old values stay readable (R1). Only
// counts are printed.
function compareStore(storeName, filter) {
    check('stored-entries-' + storeName, function () {
        var id = storeName === 'default' ? org.autojs.autojs.core.pref.store.StoreId.DEFAULT : storeId(storeName);
        var names = prefs(storeName).getAll().keySet().toArray();
        // Names registered as excluded (left-over keys no code reads any more) are counted apart.
        var excludedNames = {};
        var rows = Registry.describe();
        for (var r = 0; r < rows.size(); r++) {
            var row = rows.get(r);
            if (String(row.getStore()) === String(id.getName()) && String(row.getKind().getLabel()) === 'Excluded') excludedNames[String(row.getName())] = true;
        }
        var compared = 0, undeclared = 0, differ = 0, excluded = 0;
        for (var i = 0; i < names.length; i++) {
            var name = String(names[i]);
            if (filter && !filter(name)) continue;
            if (excludedNames[name]) { excluded++; continue; }
            var k = declaredKeyOf(name, id);
            if (!k) { undeclared++; continue; }
            var tag = String(k.getType().getTag());
            var raw = rawOf(storeName, name, tag === 'json' ? 'string' : tag);
            var actual = tag === 'json' ? k.storedText() : k.storedOrNull();
            if (tag === 'stringSet') {
                var items = [];
                var it = actual.iterator();
                while (it.hasNext()) items.push(String(it.next()));
                actual = items.sort();
            }
            if (!sameRaw(raw.v, tag === 'bool' ? actual === true || String(actual) === 'true' : (tag === 'int' || tag === 'float') ? Number(actual) : (tag === 'stringSet' ? actual : String(actual)))) differ++;
            compared++;
        }
        info(storeName + ': ' + compared + ' entries compared, ' + excluded + ' excluded, ' + undeclared + ' undeclared, ' + differ + ' differ');
        return undeclared === 0 && differ === 0 || undeclared + ' undeclared, ' + differ + ' differ';
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

// P3.1: the plugin center stores; the sort of all plugins, a hidden probe plugin and the automatic mount of the probe
// are changed.
GROUPS.plugin_center = function () {
    var P = Keys.PluginCenterKeys;
    var center = org.autojs.autojs.core.plugin.center;
    var Sort = center.PluginCenterFragment.Sort;
    var probe = 'org.autojs.settings.probe.plugin';
    var families = [P.MOUNT_ENABLED, P.MOUNT_OBJECT_NAME, P.MOUNT_POINT, P.UPDATE_CHECK, P.VARIANT_PRIORITY];
    if (AJ6_PHASE === 'change') {
        ['plugin_center_filter_state', 'plugin_center_sort_state', 'plugin_center_visibility_state', 'plugin_launcher_icons',
            'plugin_center_enable_state', 'plugin_center_recent', 'plugin_center_attention'].forEach(function (name) { compareStore(name); });
        compareStore('default', function (name) {
            return name === String(P.MOUNT_PACKAGES.getName()) || families.some(function (f) { return f.matches(name); });
        });
        check('plugin-center-readers', function () {
            var sp = prefs('plugin_center_sort_state');
            var lists = [[center.PluginSortStore.INSTANCE, P.SORT_ALL], [center.PluginSortStoreOfficial.INSTANCE, P.SORT_OFFICIAL], [center.PluginSortStoreThirdParty.INSTANCE, P.SORT_THIRD_PARTY]];
            var bad = lists.filter(function (l) {
                return !same(l[0].getSortOrdinal(context, Sort.Companion.defaultPrimarySort().ordinal()), sp.getInt(l[1].sort.getName(), Sort.Companion.defaultPrimarySort().ordinal()));
            });
            if (bad.length) return 'sort ordinal differs';
            var hidden = prefs('plugin_center_visibility_state').getStringSet(P.HIDDEN_PACKAGES.getName(), new java.util.HashSet());
            if (!same(center.PluginVisibilityStore.INSTANCE.hiddenCount(context), hidden.size())) return 'hidden count differs';
            return true;
        });
        check('plugin-center-change', function () {
            record('plugin_center_sort_state', P.SORT_ALL.sort.getName(), 'int');
            record('plugin_center_visibility_state', P.HIDDEN_PACKAGES.getName(), 'stringSet');
            record('plugin_center_visibility_state', P.HIDDEN_TITLE.nameOf(probe), 'string');
            record('plugin_center_visibility_state', P.HIDDEN_SOURCE.nameOf(probe), 'string');
            record('default', P.MOUNT_PACKAGES.getName(), 'stringSet');
            record('default', P.MOUNT_ENABLED.nameOf(probe), 'bool');
            // Scripts started before the restore apply the automatic mounts, which may switch the probe off.
            record('plugin_center_enable_state', P.PLUGIN_ENABLED.nameOf(probe), 'bool');
            var Store = center.PluginSortStore.INSTANCE;
            var current = String(Store.getSort(context, Sort.Companion.defaultPrimarySort().ordinal()).name());
            var sort = current === 'TITLE_ASC' ? Sort.PACKAGE_NAME_ASC : Sort.TITLE_ASC;
            Store.setSort(context, sort);
            center.PluginVisibilityStore.INSTANCE.hide(context, probe, 'Settings probe', null);
            center.PluginMountStore.INSTANCE.setEnabled(probe, true);
            state.expected.plugin_center = { sort: String(sort.name()), probe: probe };
            return true;
        });
    } else {
        var expected = state.expected.plugin_center;
        check('plugin-center-sort-after-restart', function () {
            return same(center.PluginSortStore.INSTANCE.getSort(context, Sort.Companion.defaultPrimarySort().ordinal()).name(), expected.sort) || 'sort lost';
        });
        check('plugin-center-hidden-after-restart', function () {
            var V = center.PluginVisibilityStore.INSTANCE;
            if (!V.isHidden(context, expected.probe)) return 'probe not hidden';
            var list = V.listHidden(context);
            for (var i = 0; i < list.size(); i++) {
                if (String(list.get(i).getPackageName()) === expected.probe) return same(list.get(i).getTitle(), 'Settings probe') || 'title lost';
            }
            return 'probe not listed';
        });
        check('plugin-center-mount-after-restart', function () {
            var M = center.PluginMountStore.INSTANCE;
            if (!M.isEnabled(expected.probe)) return 'mount switch lost';
            var configs = M.listEnabledConfigs();
            for (var i = 0; i < configs.size(); i++) if (String(configs.get(i).getPackageName()) === expected.probe) return true;
            return 'probe not tracked';
        });
    }
};

// P3.2: the editor keyboard and font dialog stores; the font sort mode and the selected variant of a probe font group
// are changed.
GROUPS.editor_keyboard = function () {
    var E = Keys.EditorKeyboardKeys;
    var ace = org.autojs.autojs.ui.edit.editor.ace;
    var Fonts = ace.AceEditorFontUiPreferences.INSTANCE;
    var Mode = ace.AceEditorFontSortMode;
    var group = 'aj6_settings_probe';
    if (AJ6_PHASE === 'change') {
        ['editor_symbols_config', 'ace_editor_font_ui_preferences', 'ace_action_history', 'FunctionsKeyboardHelper'].forEach(function (name) { compareStore(name); });
        check('editor-keyboard-readers', function () {
            var Symbols = org.autojs.autojs.ui.edit.keyboard.SymbolsConfigStore.INSTANCE;
            var raw = prefs('editor_symbols_config').getString('active_profile', '__default__');
            if (!same(Symbols.getActiveProfileName(context), raw)) return 'active profile differs';
            return same(Fonts.getSortMode(context).name(), Mode.Companion.fromStoredValue(prefs('ace_editor_font_ui_preferences').getString(E.FONT_SORT_MODE.getName(), null)).name()) || 'sort mode differs';
        });
        check('editor-keyboard-change', function () {
            record('ace_editor_font_ui_preferences', E.FONT_SORT_MODE.getName(), 'string');
            record('ace_editor_font_ui_preferences', E.FONT_SELECTED_VARIANT.nameOf(group), 'string');
            var current = String(Fonts.getSortMode(context).name());
            var values = Mode.values();
            var next = null;
            for (var i = 0; i < values.length; i++) if (String(values[i].name()) !== current) { next = values[i]; break; }
            Fonts.setSortMode(context, next);
            Fonts.setSelectedVariantId(context, group, 'aj6_settings_probe_variant');
            state.expected.editor_keyboard = { mode: String(next.name()), group: group };
            return true;
        });
    } else {
        var expected = state.expected.editor_keyboard;
        check('editor-keyboard-after-restart', function () {
            if (!same(Fonts.getSortMode(context).name(), expected.mode)) return 'sort mode lost';
            return same(Fonts.getSelectedVariantId(context, expected.group), 'aj6_settings_probe_variant') || 'selected variant lost';
        });
    }
};

// P3.3: the profile stores; the active APK build profile, the launcher icon option of the plugin install wizard and an
// ignored version of a probe plugin are changed.
GROUPS.profiles = function () {
    var W = Keys.WizardKeys, U = Keys.UpdatesKeys, A = Keys.ApkBuildKeys;
    var Build = org.autojs.autojs.ui.project.BuildConfigStore;
    var Install = org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardProfileStore.INSTANCE;
    var Ignore = org.autojs.autojs.network.UpdateIgnoreStore.INSTANCE;
    var probe = 'org.autojs.settings.probe.plugin';
    var ignoredStore = String(NamedStores.IGNORED_UPDATES.getName());
    if (AJ6_PHASE === 'change') {
        ['build_apk_config_profiles', 'permission_wizard', 'plugin_install_wizard', 'jvm_source_provider_selection', ignoredStore]
            .forEach(function (name) { compareStore(name); });
        check('profiles-readers', function () {
            var raw = prefs('build_apk_config_profiles').getString('active_profile', '__default__');
            var expected = raw === null || String(raw).trim() === '' ? '__default__' : raw;
            if (!same(Build.getActiveProfileName(context), expected)) return 'active build profile differs';
            return same(Install.showLauncherIcons(context), prefs('plugin_install_wizard').getBoolean('show_launcher_icons_for_new_plugins', true)) || 'launcher icon option differs';
        });
        check('profiles-change', function () {
            record('build_apk_config_profiles', A.ACTIVE_PROFILE.getName(), 'string');
            record('plugin_install_wizard', W.PLUGIN_INSTALL_WIZARD_SHOW_LAUNCHER_ICONS.getName(), 'bool');
            record(ignoredStore, U.IGNORED_PLUGIN_VERSIONS.nameOf(probe), 'stringSet');
            var show = !Install.showLauncherIcons(context);
            Build.setActiveProfileName(context, 'aj6_settings_probe');
            Install.setShowLauncherIcons(context, show);
            Ignore.ignoreVersion(probe, java.lang.Long.valueOf('999999'), 'probe');
            state.expected.profiles = { show: show, probe: probe };
            return true;
        });
    } else {
        var expected = state.expected.profiles;
        check('profiles-after-restart', function () {
            if (!same(Build.getActiveProfileName(context), 'aj6_settings_probe')) return 'active build profile lost';
            if (!same(Install.showLauncherIcons(context), expected.show)) return 'launcher icon option lost';
            return Ignore.isIgnored(expected.probe, java.lang.Long.valueOf('999999')) || 'ignored version lost';
        });
    }
};

// P3.4: the credential stores are compared only (no change, no value printed).
GROUPS.credentials = function () {
    if (AJ6_PHASE !== 'change') return;
    ['github_auth', 'plugin_center_authorization', 'mcp_server_trust_confirmation', 'three_stove_agent_trust_confirmation',
        'bun_host_capability_grants'].forEach(function (name) { compareStore(name); });
    check('credentials-readers', function () {
        var token = org.autojs.autojs.network.GitHubTokenStore.getToken(context);
        var raw = prefs('github_auth').getString('github_token', null);
        // Compared as present / absent only: the token itself is never printed (R6).
        return (token === null) === (raw === null || String(raw).trim() === '') || 'token presence differs';
    });
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
