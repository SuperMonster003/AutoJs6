// Device check for docs/settings/ROADMAP.md P2 (E3): the declared keys of the domains in AJ6_DOMAINS read what the old
// code read, with the resources and run-time values of the device, and the callers moved to them still work. Run by
// tools/settings/device_store_check.py ("domains"); prints "AJ6SET <PASS|FAIL> <id> <detail>" lines, "AJ6SET info
// ..." and "AJ6SET done". Every key a check writes is restored to its stored value (or removed) at the end.
// zh-CN: ROADMAP P2 的真机检查 (E3): AJ6_DOMAINS 中各领域的键声明在本机资源与运行时取值下读到旧代码所读的值, 迁移后的调用方
// 照常工作. 检查写过的每个键结束时恢复为原存储值 (或删除).
// P14.1 removed the deprecated Pref readers and P5.2 the old *Prefs objects; the "*-readers" checks now compare the
// key helpers with the raw stored values instead.
// zh-CN: P14.1 删除了 Pref 的旧读取入口, P5.2 删除了旧的 *Prefs 对象; "*-readers" 检查改为比较键的帮助函数与原始存储值.

var Pref = org.autojs.autojs.core.pref.Pref.INSTANCE;
var Keys = org.autojs.autojs.core.pref.keys;
var Registry = org.autojs.autojs.core.pref.SettingsRegistry;
var SettingDefault = org.autojs.autojs.core.pref.SettingDefault;
var Json = org.autojs.autojs.core.pref.SettingsJson;
var MARK = 'AJ6SET';
var sp = Pref.get();
var res = context.getResources();
var pkg = context.getPackageName();
var touched = {};
var snapshot = sp.getAll();
// Lines also go to RESULT_FILE, which device_store_check.py prefers: some ROMs drop log lines of a busy process.
var RESULT_FILE = files.join(files.cwd(), 'aj6set-result.txt');
var resultLines = [];

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

function expect(actual, expected, what) {
    return same(actual, expected) || (what || '') + ' got ' + actual + ', expected ' + expected;
}

function key(name) {
    return context.getString(res.getIdentifier(name, 'string', pkg));
}

function touch(k) {
    touched[k.getName()] = k;
}

// Puts the snapshot values back; numbers take the declared type of the key, since Rhino may hand Java numbers to
// scripts as plain JavaScript numbers (key_rhino_java_primitive_wrap).
function restore() {
    var editor = sp.edit();
    Object.keys(touched).forEach(function (name) {
        var v = snapshot.get(name);
        var tag = String(touched[name].getType().getTag());
        if (v === null || v === undefined) editor.remove(name);
        else if (v instanceof java.util.Set) editor.putStringSet(name, v);
        else if (tag === 'bool') editor.putBoolean(name, String(v) === 'true');
        else if (tag === 'int') editor.putInt(name, Number(v));
        else if (tag === 'long') editor.putLong(name, java.lang.Long.parseLong(String(v)));
        else if (tag === 'float') editor.putFloat(name, Number(v));
        else editor.putString(name, String(v));
    });
    return editor.commit();
}

// The old default of a key, evaluated the way the old code did: resources for "R.*" sources, the constant, or the
// run-time expression of OLD_EXPRESSIONS (the same expressions the old readers wrote).
var OLD_EXPRESSIONS = {
    'AutoNightMode.isFunctional()': function () { return android.os.Build.VERSION.SDK_INT >= 28; },
    'ThemeColorManager.defaultThemeColor.colorPrimary': function () { return org.autojs.autojs.theme.ThemeColorManager.defaultThemeColor.getColorPrimary(); },
    'ThemeColorManager.defaultThemeColor.colorPrimaryDark': function () { return org.autojs.autojs.theme.ThemeColorManager.defaultThemeColor.getColorPrimaryDark(); },
    'ThemeColorManager.defaultThemeColor.colorAccent': function () { return org.autojs.autojs.theme.ThemeColorManager.defaultThemeColor.getColorAccent(); },
    'if (!isInrt) NetworkUtils.getGatewayAddress() else NetworkUtils.DEFAULT_IP_ADDRESS': function () { return org.autojs.autojs.util.NetworkUtils.INSTANCE.getGatewayAddress(); },
    'AceEditorFont.IOSEVKA.preferenceValue': function () { return org.autojs.autojs.ui.edit.editor.ace.AceEditorFont.IOSEVKA.getPreferenceValue(); },
    'DEFAULT_FILE_TYPES': function () { return lspList('getDEFAULT_FILE_TYPES'); },
    'DEFAULT_DECLARATION_GROUPS': function () { return lspList('getDEFAULT_DECLARATION_GROUPS'); },
};
['typescript', 'python', 'lua', 'java', 'kotlin'].forEach(function (language) {
    OLD_EXPRESSIONS['DEFAULT_SEMANTIC_LANGUAGES[' + language + ']'] = function () {
        return org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences.INSTANCE.getDEFAULT_SEMANTIC_LANGUAGES().get(language);
    };
});

function lspList(getter) {
    var list = org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences.INSTANCE[getter]();
    var items = [];
    for (var i = 0; i < list.size(); i++) items.push(String(list.get(i)));
    return items.join(',');
}

function oldDefault(k) {
    var d = k.getDefault();
    if (d instanceof SettingDefault.Constant) return { value: d.getValue() };
    if (d instanceof SettingDefault.None) return { value: null };
    var source = String(d.getSource());
    var m = /^R\.(bool|integer|string)\.(\w+)$/.exec(source);
    if (m) {
        var id = res.getIdentifier(m[2], m[1], pkg);
        if (id === 0) throw new Error('no resource ' + source);
        if (m[1] === 'bool') return { value: res.getBoolean(id) };
        if (m[1] === 'integer') return { value: res.getInteger(id) };
        return { value: context.getString(id) };
    }
    var f = OLD_EXPRESSIONS[source];
    if (!f) return null;
    return { value: f() };
}

// The stored value read from the store of the key with the getter of its type and the old default, like
// Pref.getXxx(key, default); the default is turned into a JavaScript primitive first, since Rhino does not unbox a
// wrapped java.lang.Boolean. A key without a default (SettingDefault.None) reads null when absent, since its callers
// supply the run-time default (storedOrNull() ?: ...); a Json key compares as canonical JSON text, since the key
// returns the decoded value (P5.3). The named stores (P2.6) are read through the key, not through Pref.get().
// zh-CN: 从键所在存储按其类型的读取方法与旧默认值读出存储值. 无默认值 (SettingDefault.None) 的键未存储时为 null, 运行时默认值
// 由调用方给出 (storedOrNull() ?: ...); Json 键按规范化的 JSON 文本比较, 因为键返回解码后的值 (P5.3). 命名存储 (P2.6) 经键读取.
function oldRead(k, def) {
    var name = k.getName();
    var store = k.getSettingsStore();
    var tag = String(k.getType().getTag());
    if (def === null && !store.contains(name)) return null;
    switch (tag) {
        case 'bool': return store.getBoolean(name, def !== null && String(def) === 'true');
        case 'int': return store.getInt(name, Number(def));
        case 'long': return store.getLong(name, Number(def));
        case 'float': return Number(store.getFloat(name, Number(def)));
        case 'stringSet': return store.getStringSet(name, def);
        case 'json':
            var text = store.getString(name, null);
            return text === null ? jsonText(def) : jsonText(Json.parse(text));
        default: return store.getString(name, def === null ? null : String(def));
    }
}

function jsonText(value) {
    return value === null ? null : Json.stringify(value);
}

function readValue(k) {
    if (k instanceof org.autojs.autojs.core.pref.SettingKey.Enum) return k.storedKeyOrDefault();
    // A java.lang.Float may reach the script wrapped, whose String() is "0.0" rather than "0".
    // zh-CN: java.lang.Float 可能以包装对象到达脚本, 其 String() 为 "0.0" 而不是 "0".
    var value = k.getOrNull();
    var tag = String(k.getType().getTag());
    if (value === null) return null;
    return tag === 'json' ? jsonText(value) : tag === 'float' ? Number(value) : value;
}

function checkDomain(domainName) {
    var domain = Keys[domainName].INSTANCE;
    var keys = domain.getKeys();
    var compared = 0, skipped = [];
    for (var i = 0; i < keys.size(); i++) {
        var k = keys.get(i);
        var od;
        try {
            od = oldDefault(k);
        } catch (e) {
            report(domainName + '-default-' + k.getName(), false, 'old default threw ' + e);
            continue;
        }
        if (od === null) {
            skipped.push(String(k.getName()));
            continue;
        }
        check(domainName + '-default-' + k.getName(), function () {
            return expect(k.defaultOrNull(), od.value, 'default');
        });
        check(domainName + '-read-' + k.getName(), function () {
            var expected = oldRead(k, od.value);
            return expect(readValue(k), expected, 'stored ' + k.getSettingsStore().get(k.getName()));
        });
        compared++;
    }
    info(domainName + ': ' + keys.size() + ' keys, ' + compared + ' compared with the old reading' + (skipped.length ? ', run-time defaults not compared: ' + skipped.join(' ') : ''));
}

// Callers moved to the keys in each domain (P2.x); every write is restored at the end.
var CALLERS = {
    AppearanceKeys: function () {
        var A = Keys.AppearanceKeys;
        var Language = org.autojs.autojs.core.pref.Language;
        var ViewUtils = org.autojs.autojs.util.ViewUtils.INSTANCE;
        var ThemeColor = org.autojs.autojs.theme.ThemeColor;
        check('appearance-language', function () {
            touch(A.APP_LANGUAGE);
            A.APP_LANGUAGE.set(key('key_app_language_ja'), true);
            if (!same(Language.getPrefLanguage(), Language.JA)) return 'ja: ' + Language.getPrefLanguage();
            A.APP_LANGUAGE.setStoredKey('key_$_app_language_removed', true);
            if (Language.getPrefLanguageOrNull() !== null) return 'unknown key: ' + Language.getPrefLanguageOrNull();
            if (!same(Language.getPrefLanguage(), Language.AUTO)) return 'unknown key: ' + Language.getPrefLanguage();
            Language.Companion.setPrefLanguageAuto();
            return expect(sp.getString(A.APP_LANGUAGE.getName(), null), key('key_app_language_auto'), 'auto');
        });
        check('appearance-night-mode-switches', function () {
            touch(A.NIGHT_MODE_ENABLED);
            touch(A.AUTO_NIGHT_MODE_ENABLED);
            var night = ViewUtils.isNightModeEnabled();
            var auto = ViewUtils.isAutoNightModeEnabled();
            if (!same(night, sp.getBoolean(A.NIGHT_MODE_ENABLED.getName(), false))) return 'night ' + night;
            if (!same(auto, sp.getBoolean(A.AUTO_NIGHT_MODE_ENABLED.getName(), android.os.Build.VERSION.SDK_INT >= 28))) return 'auto ' + auto;
            ViewUtils.setNightModeEnabled(!night);
            if (!same(sp.getBoolean(A.NIGHT_MODE_ENABLED.getName(), night), !night)) return 'write night';
            ViewUtils.setAutoNightModeEnabled(!auto);
            return expect(sp.getBoolean(A.AUTO_NIGHT_MODE_ENABLED.getName(), auto), !auto, 'write auto');
        });
        check('appearance-theme-color', function () {
            var stored = sp.contains(A.THEME_COLOR_PRIMARY.getName()) && sp.contains(A.THEME_COLOR_PRIMARY_DARK.getName()) && sp.contains(A.THEME_COLOR_ACCENT.getName());
            var color = ThemeColor.Companion.fromPreferences();
            if (!stored) return color === null || 'not stored, got ' + color;
            return color !== null && same(color.getColorPrimary(), sp.getInt(A.THEME_COLOR_PRIMARY.getName(), 0))
                && same(color.getColorPrimaryDark(), sp.getInt(A.THEME_COLOR_PRIMARY_DARK.getName(), 0))
                && same(color.getColorAccent(), sp.getInt(A.THEME_COLOR_ACCENT.getName(), 0)) || 'colors differ';
        });
        check('appearance-keep-screen-on', function () {
            var expected = sp.getString(A.KEEP_SCREEN_ON_WHEN_IN_FOREGROUND.getName(), key('key_keep_screen_on_when_in_foreground_disabled'));
            if (!same(A.getKeepScreenOnWhenInForegroundKey(), expected)) return 'stored key ' + A.getKeepScreenOnWhenInForegroundKey();
            return expect(ViewUtils.isKeepScreenOnWhenInForegroundDisabled(), same(expected, key('key_keep_screen_on_when_in_foreground_disabled')), 'disabled');
        });
    },
    GeneralKeys: function () {
        var G = Keys.GeneralKeys;
        var DocsUtils = org.autojs.autojs.util.DocsUtils;
        check('general-restart-strategy', function () {
            touch(G.RESTART_STRATEGY);
            G.RESTART_STRATEGY.set(key('key_restart_strategy_scheduled'), true);
            if (G.isQuickRestartEnabled()) return 'scheduled is quick';
            G.RESTART_STRATEGY.setStoredKey('key_$_restart_strategy_removed', true);
            if (G.isQuickRestartEnabled()) return 'unknown key is quick';
            G.RESTART_STRATEGY.remove(true);
            return G.isQuickRestartEnabled() || 'default is not quick';
        });
        check('general-scheduled-restart', function () {
            touch(G.SCHEDULED_RESTART_BACKEND);
            touch(G.SCHEDULED_RESTART_DELAY);
            G.SCHEDULED_RESTART_BACKEND.set(key('key_scheduled_restart_backend_alarm_manager'), true);
            if (G.isScheduledRestartPreferredWorkManager()) return 'alarm manager is work manager';
            G.SCHEDULED_RESTART_BACKEND.remove(true);
            if (!G.isScheduledRestartPreferredWorkManager()) return 'default is not work manager';
            G.SCHEDULED_RESTART_DELAY.set(java.lang.Integer.valueOf(1200), true);
            return expect(sp.getInt(G.SCHEDULED_RESTART_DELAY.getName(), 0), 1200, 'delay');
        });
        check('general-documentation-source', function () {
            touch(G.DOCUMENTATION_SOURCE);
            G.DOCUMENTATION_SOURCE.set(key('key_documentation_source_online'), true);
            if (DocsUtils.isOfflineSourceSelected()) return 'online is offline';
            G.DOCUMENTATION_SOURCE.remove(true);
            return DocsUtils.isOfflineSourceSelected() || 'missing is online';
        });
        check('general-foreground-service-and-launcher-icon', function () {
            if (!same(G.FOREGROUND_SERVICE.storedOrNull() || false, sp.getBoolean(G.FOREGROUND_SERVICE.getName(), false))) return 'foreground service';
            return expect(G.LAUNCHER_ICON.storedKey(), sp.getString(G.LAUNCHER_ICON.getName(), null), 'launcher icon');
        });
    },
    ExplorerKeys: function () {
        var E = Keys.ExplorerKeys;
        check('explorer-hidden-files', function () {
            touch(E.HIDDEN_FILES);
            var show = key('key_hidden_files_show');
            var expected = same(sp.getString(E.HIDDEN_FILES.getName(), key('key_hidden_files_not_show')), show);
            if (!same(E.isHiddenFilesShown(), expected)) return 'stored reading';
            E.HIDDEN_FILES.set(show, true);
            if (!E.isHiddenFilesShown()) return 'show';
            E.HIDDEN_FILES.setStoredKey('key_$_hidden_files_removed', true);
            return !E.isHiddenFilesShown() || 'unknown key shows hidden files';
        });
        check('explorer-file-extensions', function () {
            touch(E.FILE_EXTENSIONS);
            var states = function () {
                return [E.isFileExtensionsShownForAll(), E.isFileExtensionsShownForAllButExecutable(), E.isFileExtensionsHidden()].join(',');
            };
            E.FILE_EXTENSIONS.remove(true);
            if (states() !== 'true,false,false') return 'default ' + states();
            E.FILE_EXTENSIONS.set(key('key_file_extensions_not_show'), true);
            if (states() !== 'false,false,true') return 'not show ' + states();
            E.FILE_EXTENSIONS.setStoredKey('key_$_file_extensions_removed', true);
            return states() === 'false,false,false' || 'unknown key ' + states();
        });
        check('explorer-working-directory', function () {
            var stored = sp.getString(E.WORKING_DIRECTORY.getName(), null);
            var path = String(org.autojs.autojs.util.WorkingDirectoryUtils.getPath());
            info('working directory stored ' + stored + ', path ' + path);
            return path.length > 0 && same(E.WORKING_DIRECTORY.get(), stored === null ? context.getString(res.getIdentifier('default_value_working_directory', 'string', pkg)) : stored) || 'path ' + path;
        });
        check('explorer-batch-rename-retention', function () {
            touch(E.BATCH_RENAME_HISTORY_RETENTION);
            E.setBatchRenameHistoryRetention(50);
            if (!same(sp.getInt(E.BATCH_RENAME_HISTORY_RETENTION.getName(), 0), 50) || !same(E.batchRenameHistoryRetention(), 50)) return 'retention 50';
            sp.edit().putInt(E.BATCH_RENAME_HISTORY_RETENTION.getName(), 33).commit();
            return expect(E.batchRenameHistoryRetention(), 20, 'not a choice');
        });
    },
    HistoryKeys: function () {
        var H = Keys.HistoryKeys;
        var Retention = org.autojs.autojs.ui.settings.RetentionLimitSettings.INSTANCE;
        check('history-readers', function () {
            var pairs = [
                [H.HISTORY_MAX_FILE_SIZE_TO_TRACK_BYTES.get(), sp.getLong(H.HISTORY_MAX_FILE_SIZE_TO_TRACK_BYTES.getName(), 20 * 1024 * 1024)],
                [H.HISTORY_MAX_DAYS.get(), sp.getInt(H.HISTORY_MAX_DAYS.getName(), 30)],
                [H.HISTORY_MAX_VERSIONS.get(), sp.getInt(H.HISTORY_MAX_VERSIONS.getName(), 50)],
                [H.HISTORY_MAX_TOTAL_BYTES_PER_FILE.get(), sp.getLong(H.HISTORY_MAX_TOTAL_BYTES_PER_FILE.getName(), 200 * 1024 * 1024)],
                [H.HISTORY_MAX_TOTAL_BYTES.get(), sp.getLong(H.HISTORY_MAX_TOTAL_BYTES.getName(), 2 * 1024 * 1024 * 1024)],
                [H.TRASH_MAX_DAYS.get(), sp.getInt(H.TRASH_MAX_DAYS.getName(), 30)],
                [H.TRASH_MAX_TOTAL_BYTES.get(), sp.getLong(H.TRASH_MAX_TOTAL_BYTES.getName(), 500 * 1024 * 1024)],
                [H.DRAFTS_MAX_DAYS.get(), sp.getInt(H.DRAFTS_MAX_DAYS.getName(), 7)],
                [H.DRAFTS_MAX_TOTAL_BYTES.get(), sp.getLong(H.DRAFTS_MAX_TOTAL_BYTES.getName(), 200 * 1024 * 1024)],
                [H.DELETE_PERMANENTLY_ALSO_CLEAR_HISTORY.get(), sp.getBoolean(H.DELETE_PERMANENTLY_ALSO_CLEAR_HISTORY.getName(), false)],
            ];
            var bad = pairs.map(function (p, i) { return same(p[0], p[1]) ? null : i + ': ' + p[0] + ' != ' + p[1]; }).filter(function (x) { return x; });
            return bad.length === 0 || bad.join('; ');
        });
        check('history-retention-page', function () {
            touch(H.DRAFTS_MAX_DAYS);
            touch(H.TRASH_MAX_TOTAL_BYTES);
            var specs = Retention.getSpecs();
            if (specs.size() !== 9) return 'specs ' + specs.size();
            var days = Retention.specForKey(H.DRAFTS_MAX_DAYS.getName());
            Retention.write(days, 12);
            if (!same(sp.getInt(H.DRAFTS_MAX_DAYS.getName(), 0), 12) || !same(Retention.read(days), 12)) return 'days';
            var bytes = Retention.specForKey(H.TRASH_MAX_TOTAL_BYTES.getName());
            Retention.write(bytes, -3);
            return same(sp.getLong(H.TRASH_MAX_TOTAL_BYTES.getName(), 1), 0) || 'bytes ' + sp.getLong(H.TRASH_MAX_TOTAL_BYTES.getName(), 1);
        });
    },
    EditorKeys: function () {
        var K = Keys.EditorKeys;
        var Display = org.autojs.autojs.ui.edit.editor.EditorDisplayPreferences.INSTANCE;
        var Lsp = org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences;
        var Font = org.autojs.autojs.ui.edit.editor.ace.AceEditorFontPreferences;
        var Themes = org.autojs.autojs.ui.edit.theme.Themes;
        check('editor-text-size', function () {
            touch(K.EDITOR_TEXT_SIZE);
            var stored = sp.contains(K.EDITOR_TEXT_SIZE.getName());
            if (!stored && !same(K.EDITOR_TEXT_SIZE.get(), 14)) return 'default ' + K.EDITOR_TEXT_SIZE.get();
            var storedSize = K.EDITOR_TEXT_SIZE.storedOrNull();
            if (!same(storedSize === null ? 99 : storedSize, stored ? sp.getInt(K.EDITOR_TEXT_SIZE.getName(), 0) : 99)) return 'stored reading';
            K.EDITOR_TEXT_SIZE.set(java.lang.Integer.valueOf(17), true);
            return expect(sp.getInt(K.EDITOR_TEXT_SIZE.getName(), 0), 17, 'write');
        });
        check('editor-thresholds', function () {
            touch(K.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES);
            touch(K.EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES);
            K.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES.set(java.lang.Integer.valueOf(-5), true);
            var def = res.getInteger(res.getIdentifier('editor_code_highlight_threshold_default_value', 'integer', pkg));
            if (!same(K.getCodeHighlightThresholdChars(), def)) return 'below -1 is not the default';
            K.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES.set(java.lang.Integer.valueOf(-1), true);
            if (!K.isCodeHighlightEnabledForLength(1 << 30)) return 'no threshold is not always';
            K.EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES.set(java.lang.Integer.valueOf(0), true);
            return K.shouldEnableLargeFileModeForSize(1) || 'zero is not always large file mode';
        });
        check('editor-display-preferences', function () {
            touch(K.EDITOR_GUTTER_WIDTH_MODE);
            touch(K.ACE_WORD_WRAP_ENABLED);
            Display.setWordWrapEnabled(!Display.isWordWrapEnabled());
            if (!same(Display.isWordWrapEnabled(), sp.getBoolean(K.ACE_WORD_WRAP_ENABLED.getName(), false))) return 'word wrap';
            K.EDITOR_GUTTER_WIDTH_MODE.setStoredKey('removed', true);
            return expect(Display.gutterWidthMode(), 'dynamic', 'unknown gutter mode');
        });
        check('editor-theme-and-font', function () {
            var unified = same(sp.getString(K.EDITOR_THEME_MODE.getName(), 'separate_light_dark'), 'unified_light_dark');
            if (!same(Themes.isSeparateLightDarkMode(), !unified)) return 'theme mode';
            if (!same(K.EDITOR_THEME.getOrNull(), sp.getString(K.EDITOR_THEME.getName(), null))) return 'current theme';
            var id = Font.getId();
            return /^[a-z0-9][a-z0-9_]{0,63}$/.test(String(id)) || 'font id ' + id;
        });
        check('editor-lsp', function () {
            touch(K.ACE_SEMANTIC_KOTLIN_ENABLED);
            if (!same(Lsp.isEnabled(), sp.getBoolean(K.ACE_LSP_ENABLED.getName(), true))) return 'lsp enabled';
            var fileTypes = Lsp.getFileTypes();
            if (!sp.contains(K.ACE_LSP_FILE_TYPES.getName()) && fileTypes.size() !== Lsp.INSTANCE.getDEFAULT_FILE_TYPES().size()) return 'default file types';
            sp.edit().remove(K.ACE_SEMANTIC_KOTLIN_ENABLED.getName()).commit();
            return expect(K.ACE_SEMANTIC_KOTLIN_ENABLED.get(), Lsp.INSTANCE.getDEFAULT_SEMANTIC_LANGUAGES().get('kotlin'), 'semantic default');
        });
    },
    TimedTaskKeys: function () {
        check('timed-task-backend', function () {
            var T = Keys.TimedTaskKeys;
            return expect(T.TIMED_TASK_BACKEND.storedKeyOrDefault(), sp.getString(T.TIMED_TASK_BACKEND.getName(), key('key_timed_task_backend_alarm')), 'backend');
        });
    },
    A11yKeys: function () {
        var A = Keys.A11yKeys;
        check('a11y-readers', function () {
            var keys = [A.ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS, A.ENABLE_A11Y_SERVICE_WITH_SECURE_SETTINGS, A.ENABLE_A11Y_SERVICE_WITH_SHIZUKU, A.STABLE_MODE, A.GESTURE_OBSERVING];
            var bad = keys.filter(function (k) { return !same(k.get(), sp.getBoolean(k.getName(), String(k.defaultOrNull()) === 'true')); }).map(function (k) { return String(k.getName()); });
            return bad.length === 0 || bad.join(' ');
        });
        // Appendix C Q10 (2) / (3): a missing switch reads the default of its declaration through the script API.
        check('settings-isEnabled-missing-keys', function () {
            touch(A.ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS);
            touch(Keys.RunningKeys.USE_VOLUME_CONTROL_RUNNING);
            touch(A.STABLE_MODE);
            A.ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS.remove(true);
            Keys.RunningKeys.USE_VOLUME_CONTROL_RUNNING.remove(true);
            A.STABLE_MODE.remove(true);
            var results = [settings.isEnabled('enable_accessibility_service_by_root'), settings.isEnabled('stop_all_on_volume_up'), settings.isEnabled('stable_mode')];
            if (!same(results.join(','), 'true,true,false')) return 'missing keys read ' + results.join(',');
            settings.setEnabled('stop_all_on_volume_up', false);
            return expect(settings.isEnabled('stop_all_on_volume_up'), false, 'stored false');
        });
    },
    RunningKeys: function () {
        var R = Keys.RunningKeys;
        check('running-readers', function () {
            var bools = [R.GUARD_MODE, R.USE_VOLUME_CONTROL_RUNNING];
            var bad = bools.filter(function (k) { return !same(k.get(), sp.getBoolean(k.getName(), String(k.defaultOrNull()) === 'true')); });
            if (bad.length) return 'stored reading ' + bad.map(function (k) { return String(k.getName()); }).join(' ');
            if (R.getRootMode() === null) return 'root mode';
            var delay = sp.getInt(R.SCREEN_CAPTURE_REQUEST_DELAY.getName(), 350);
            return expect(R.getScreenCaptureRequestDelay(), Math.min(2000, Math.max(0, delay)), 'screen capture delay');
        });
    },
    RecordKeys: function () {
        var R = Keys.RecordKeys;
        check('record-readers', function () {
            var binary = same(sp.getString(R.ROOT_RECORD_OUT_FILE_TYPE.getName(), key('key_root_record_out_file_type_binary')), key('key_root_record_out_file_type_binary'));
            if (!same(R.rootRecordGeneratesBinary(), binary)) return 'root record';
            var bools = [R.RECORD_TOAST, R.USE_VOLUME_CONTROL_RECORD];
            return bools.every(function (k) { return same(k.get(), sp.getBoolean(k.getName(), String(k.defaultOrNull()) === 'true')); }) || 'toast / volume key';
        });
    },
    ExtensibilityKeys: function () {
        check('extensibility-readers', function () {
            var k = Keys.ExtensibilityKeys.EXTENDING_JS_BUILD_IN_OBJECTS;
            return expect(k.get(), sp.getBoolean(k.getName(), String(k.defaultOrNull()) === 'true'), 'extending');
        });
    },
    UpdatesKeys: function () {
        var U = Keys.UpdatesKeys;
        check('updates-ignored-versions', function () {
            var raw = sp.getString(U.IGNORED_UPDATES.getName(), null);
            var set = U.ignoredUpdates();
            if (raw === null) return set.size() === 0 || 'missing key is not empty';
            var encoded = org.autojs.autojs.core.pref.SettingsJson.INSTANCE.encodeStringList(set);
            return expect(encoded, raw, 'text written back');
        });
    },
    ConsoleKeys: function () {
        check('console-time-settings', function () {
            var entries = org.autojs.autojs.core.console.ConsoleTimeSettings.values();
            var bad = [];
            for (var i = 0; i < entries.length; i++) {
                var e = entries[i];
                if (!same(e.isTimeVisible(), sp.getBoolean(e.getVisibleKey().getName(), e.getDefaultVisible()))) bad.push(e + ' visible');
                var pattern = org.autojs.autojs.core.console.ConsoleLogFormat.INSTANCE.normalizeTimePattern(sp.getString(e.getFormatKey().getName(), 'HH:mm:ss.SSS'));
                if (!same(e.getTimeFormat(), pattern)) bad.push(e + ' format');
            }
            return bad.length === 0 || bad.join(' ');
        });
    },
    ConnectionKeys: function () {
        var C = Keys.ConnectionKeys;
        check('connection-readers', function () {
            var keys = [C.CLIENT_MODE_AUTO_RECONNECT, C.CLIENT_MODE_RECONNECT_ON_NETWORK_CHANGE, C.CLIENT_MODE_RESTORE_ON_LAUNCH,
                C.SERVER_MODE_RESTORE_ON_LAUNCH, C.SERVER_MODE_NOTIFY_CONNECTION_CHANGES, C.SERVER_MODE_LAN_DISCOVERY];
            var bad = keys.filter(function (k) { return !same(k.get(), sp.getBoolean(k.getName(), String(k.defaultOrNull()) === 'true')); }).map(function (k) { return String(k.getName()); });
            if (bad.length) return bad.join(' ');
            var gateway = org.autojs.autojs.util.NetworkUtils.INSTANCE.getGatewayAddress();
            return expect(C.SERVER_ADDRESS.get(), sp.getString(C.SERVER_ADDRESS.getName(), gateway), 'server address');
        });
    },
    LayoutInspectorKeys: function () {
        var L = Keys.LayoutInspectorKeys;
        check('layout-inspector-query-mode', function () {
            touch(L.LAYOUT_INSPECTOR_QUERY_MODE);
            var Store = org.autojs.autojs.ui.floating.layoutinspector.search.QueryModeStore.INSTANCE;
            L.LAYOUT_INSPECTOR_QUERY_MODE.set('REMOVED_MODE', true);
            if (!same(Store.load(), 'KEYWORD')) return 'unknown mode read as ' + Store.load();
            L.LAYOUT_INSPECTOR_QUERY_MODE.remove(true);
            return expect(Store.load(), 'KEYWORD', 'missing mode');
        });
    },
    PluginCenterKeys: function () {
        var P = Keys.PluginCenterKeys;
        check('plugin-center-online-settings', function () {
            var S = org.autojs.autojs.core.plugin.center.PluginOnlineSettingsStore.INSTANCE;
            return same(S.isOfficialEnabled(), P.PLUGIN_CENTER_SHOW_OFFICIAL_PLUGINS.get())
                && same(S.isThirdPartyEnabled(), P.PLUGIN_CENTER_SHOW_THIRD_PARTY_PLUGINS.get())
                && same(S.isAutoCheckUpdatesEnabled(), P.PLUGIN_CENTER_AUTO_CHECK_UPDATES.get()) || 'online settings';
        });
    },
    MiscStateKeys: function () {
        // inrt/Pref reads the default preferences through the store layer now (T13); read-only calls only.
        check('inrt-pref-through-the-store', function () {
            var I = org.autojs.autojs.inrt.Pref.INSTANCE;
            var pairs = [
                [I.shouldEnableAccessibilityServiceByRoot(), sp.getBoolean(key('key_enable_a11y_service_with_root_access'), true)],
                [I.shouldHideLogs(), sp.getBoolean(key('key_not_showing_main_activity'), false)],
                [I.shouldStopAllScriptsWhenVolumeUp(), sp.getBoolean(key('key_use_volume_control_running'), true)],
                [I.shouldRunOnBoot(), sp.getBoolean(key('key_run_on_boot_inrt'), false)],
            ];
            var bad = pairs.map(function (p, i) { return same(p[0], p[1]) ? null : String(i); }).filter(function (x) { return x; });
            return bad.length === 0 || 'differs at ' + bad.join(' ');
        });
    },
    DeveloperKeys: function () {
        var D = Keys.DeveloperKeys;
        check('developer-readers', function () {
            var bools = [D.SCRIPT_MODULE_DEBUG_PRINT];
            var bad = bools.filter(function (k) { return !same(k.get(), sp.getBoolean(k.getName(), String(k.defaultOrNull()) === 'true')); });
            if (bad.length) return 'stored reading ' + bad.map(function (k) { return String(k.getName()); }).join(' ');
            var raw = sp.getStringSet(D.SCRIPT_MODULE_DEBUG_PRINT_DISABLED_MODULES.getName(), null);
            return expect(D.getScriptModuleDebugPrintDisabledModules().size(), raw === null ? 0 : raw.size(), 'disabled modules');
        });
    },
};

info('registry: ' + Registry.DOMAINS.size() + ' domains, ' + Registry.getKeys().size() + ' keys, ' + Registry.describe().size() + ' descriptors');
AJ6_DOMAINS.forEach(function (name) {
    checkDomain(name);
    if (CALLERS[name]) CALLERS[name]();
});
check('restore', function () {
    if (!restore()) return 'commit failed';
    var after = sp.getAll();
    var diff = Object.keys(touched).filter(function (name) { return !same(after.get(name), snapshot.get(name)); });
    return diff.length === 0 || 'not restored: ' + diff.join(' ');
});
resultLines.push('done');
files.write(RESULT_FILE, resultLines.join(String.fromCharCode(10)));
console.log(MARK + ' done');
