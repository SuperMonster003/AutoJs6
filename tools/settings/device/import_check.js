// Device side of the import wizard check (docs/settings/ROADMAP.md P7.5 / P7.4 / P8.1 / P7.7 / P10.2 / P10.3 / P11.1,
// appendix D items 1 - 6, 13 and 14, E3).
// Run by tools/settings/device_import_check.py, which sets AJ6_PHASE and the other AJ6_* variables:
//   prepare  keeps every setting (an archive sealed with the device key) and the internal backups in the private
//            directory of the app, sets the "A" values (theme color, night mode, editor text size, plugin sort, a test
//            GitHub token, AJ6_LANGUAGE and AJ6_ICON when given), writes OUT/a.zip (the default categories and
//            credentials, as the export wizard does) and OUT/b.zip (plugin_enable_state with one installed plugin
//            flipped and a package that is not installed), then sets and applies the "B" values;
//   verify   checks that the values are AJ6_EXPECT ("A" or "B"), applied in this process (theme color, night mode,
//            language), the test token is still the "B" one, and counts the internal and operation backups;
//   plugins  checks the flipped plugin state (AJ6_EXPECT "flipped" or "original") and that the missing package was
//            not written;
//   reset    checks a reset to defaults of AJ6_CATEGORIES (P8.1): their keys are gone, the other test values stay;
//   keep     only the first part of prepare: keeps every setting and the internal backups, for the phases below;
//   pbkdf2   times one PBKDF2 derivation of the password slot (P7.7, appendix C Q30), AJ6_ROUNDS warm runs;
//   token    sets the test GitHub token AJ6_EXPECT ("A" or "B") and writes OUT/d.zip, the credentials sealed with the
//            device key only (P7.7); token-verify checks which test token is stored; access checks how this device
//            reads the credentials of AJ6_FILE (AJ6_EXPECT: DEVICE, PASSWORD, LOCKED, ...);
//   data-prepare  adds three test shortcuts (one without an icon) and two test color libraries (P10.2, item 13);
//            data-inspect checks the data of AJ6_FILE (2 test shortcuts, 1 test color library); data-delete deletes the
//            test entries; data-verify checks them after the import (AJ6_EXPECT "imported") or its undo ("undone");
//   welcome-reset  clears key_setup_wizard_welcome_done (P11.1, item 14); welcome-verify checks it is set again;
//   restore  imports the kept archive back (every category, effects applied), puts the internal backups back and
//            deletes the operation backups written since prepare, the test data, and the private files.
// Lines carry counts and non-secret values only, never the token or other credentials (R6).
// zh-CN: 导入向导真机检查的设备端 (ROADMAP P7.5 / P7.4 / P8.1 / P7.7 / P10.2 / P10.3 / P11.1, 附录 D 第 1 - 6, 13, 14 项, E3). 由 device_import_check.py 设置 AJ6_PHASE 等变量:
// prepare 把全部设置 (以设备密钥加密的归档) 与内部备份保存到应用私有目录, 设置 "A" 值并写出 a.zip (默认分类加凭据) 与 b.zip
// (插件启用状态: 翻转一个已安装插件并加入一个未安装的包), 再设置并应用 "B" 值; verify 核对值为 AJ6_EXPECT, 本进程已生效, 测试令牌仍为
// "B", 并统计内部备份与操作后备份; plugins 核对插件状态; reset 核对恢复默认 (P8.1); keep 只做 prepare 的保存部分; pbkdf2 计时口令槽的
// 一次派生 (P7.7, 附录 C Q30); token 设置测试令牌并写出只有设备槽的 d.zip, token-verify / access 核对令牌与凭据的读取方式; data-*
// 准备, 核对与删除测试快捷方式与颜色库 (P10.2 / P10.3); welcome-* 清除并核对欢迎向导状态 (P11.1); restore 导入保存的归档还原全部设置,
// 放回内部备份, 删除 prepare 之后写出的操作后备份, 测试数据与私有文件. 只输出计数与非机密的值, 不输出令牌等凭据 (R6).

var MARK = 'AJ6SET';
var RESULT_FILE = files.join(files.cwd(), 'aj6set-result.txt');
var resultLines = [];

function out(line) {
    console.log(MARK + ' ' + line);
    resultLines.push(line);
}

function check(name, ok, detail) {
    out((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' ' + detail : ''));
}

var pref = Packages.org.autojs.autojs.core.pref;
var archive = pref.archive;
var keys = pref.keys;
var Category = pref.SettingCategory;
var stores = pref.store.SettingsStores.INSTANCE;
var exporter = Packages.org.autojs.autojs.ui.settings.transfer.SettingsExporter;
var File = java.io.File;

var PRIVATE = new File(context.getFilesDir(), 'aj6-import-check');
var ORIGINAL = new File(PRIVATE, 'original.zip');
var STATE = new File(PRIVATE, 'state.json');
var VAULT = new File(context.getFilesDir(), archive.SettingsBackupVault.DIRECTORY);
var VAULT_COPY = new File(PRIVATE, 'vault');
var MISSING_PACKAGE = 'org.autojs.aj6check.notinstalled';
var TOKEN_A = 'aj6-check-token-A';
var TOKEN_B = 'aj6-check-token-B';

var VALUES = {
    A: {theme: 0xFF009688 | 0, night: true, size: 19, sort: 1},
    B: {theme: 0xFFE91E63 | 0, night: false, size: 12, sort: 0},
};

function str(id) {
    return String(context.getString(id));
}

// A java.lang.Boolean is an object, so Boolean(it) is always true in JavaScript.
function bool(value) {
    return String(value) === 'true';
}

function storeOf(key) {
    return stores.get(key.getStore());
}

function stored(key) {
    var store = storeOf(key);
    return store.contains(key.getName()) ? store.get(key.getName()) : null;
}

function put(key, value) {
    storeOf(key).put(key.getName(), value, true);
}

function onMain(fn) {
    var latch = new java.util.concurrent.CountDownLatch(1);
    var error = null;
    new android.os.Handler(android.os.Looper.getMainLooper()).post(function () {
        try {
            fn();
        } catch (e) {
            error = e;
        } finally {
            latch.countDown();
        }
    });
    latch.await(20, java.util.concurrent.TimeUnit.SECONDS);
    if (error) throw error;
}

function effects() {
    return new archive.AndroidSettingsEffects(context);
}

function runEntries(names) {
    onMain(function () {
        var fx = effects();
        names.forEach(function (name) {
            fx.actionOf(archive.SettingsEffects.Entry.valueOf(name)).run();
        });
    });
}

function deviceKey() {
    return archive.AndroidSettingsKeys.device(context);
}

function host() {
    return exporter.host(context);
}

function writeArchive(file, categories) {
    file.getParentFile().mkdirs();
    var stream = new java.io.FileOutputStream(file);
    try {
        var snapshot = archive.SettingsSnapshot.capture(categories);
        return exporter.write(stream, snapshot, java.lang.System.currentTimeMillis(), host(), exporter.device(), archive.SettingsArchive.MAX_BYTES, deviceKey());
    } finally {
        stream.close();
    }
}

function readerOf(file) {
    var opener = new Packages.kotlin.jvm.functions.Function0({
        invoke: function () {
            return new java.io.FileInputStream(file);
        },
    });
    return new archive.SettingsArchiveReader(opener, archive.SettingsArchive.MAX_BYTES, deviceKey());
}

function list(items) {
    var result = new java.util.ArrayList();
    items.forEach(function (it) {
        result.add(it);
    });
    return result;
}

function installedPlugin() {
    var preferred = 'io.github.supermonster003.autojs6.plugin.audioplayer';
    var packages = context.getPackageManager().getInstalledPackages(0);
    var fallback = null;
    for (var i = 0; i < packages.size(); i++) {
        var name = String(packages.get(i).packageName);
        if (name === preferred) return name;
        if (!fallback && name.indexOf('io.github.supermonster003.autojs6.plugin.') === 0) fallback = name;
    }
    return fallback;
}

// The "A" values only go into a.zip, so their language and launcher icon are stored without being applied: two
// language changes in a row race on Android 13, where the recreated activities write the app language back.
function setValues(values, language, icon, apply) {
    var appearance = keys.AppearanceKeys;
    put(appearance.NIGHT_MODE, str(values.night ? Packages.org.autojs.autojs6.R.string.key_night_mode_always_on : Packages.org.autojs.autojs6.R.string.key_night_mode_always_off));
    put(appearance.NIGHT_MODE_ENABLED, java.lang.Boolean.valueOf(values.night));
    put(appearance.AUTO_NIGHT_MODE_ENABLED, java.lang.Boolean.FALSE);
    put(keys.EditorKeys.EDITOR_TEXT_SIZE, java.lang.Integer.valueOf(values.size));
    put(keys.PluginCenterKeys.SORT_ALL.sort, java.lang.Integer.valueOf(values.sort));
    put(keys.CredentialKeys.GITHUB_TOKEN, values === VALUES.A ? TOKEN_A : TOKEN_B);
    var entries = ['NIGHT_MODE'];
    if (language) {
        put(appearance.APP_LANGUAGE, language);
        if (apply) entries.push('LANGUAGE');
    }
    if (icon) {
        put(keys.GeneralKeys.LAUNCHER_ICON, icon);
        if (apply) entries.push('LAUNCHER_ICON');
    }
    onMain(function () {
        Packages.org.autojs.autojs.theme.ThemeColorCoordinator.INSTANCE.apply(values.theme, Packages.org.autojs.autojs.theme.ThemeColorSource.RESTORE);
    });
    runEntries(entries);
}

function copyDirectory(from, to) {
    to.mkdirs();
    var children = from.listFiles();
    if (!children) return 0;
    var n = 0;
    for (var i = 0; i < children.length; i++) {
        if (children[i].isFile()) {
            // files.copy rather than java.nio.file, which Android 7 does not have.
            // zh-CN: 用 files.copy 而不是 Android 7 没有的 java.nio.file.
            if (!files.copy(children[i].getPath(), new File(to, children[i].getName()).getPath())) throw new Error('not copied: ' + children[i].getName());
            n++;
        }
    }
    return n;
}

function deleteDirectory(dir) {
    var children = dir.listFiles();
    if (children) {
        for (var i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) deleteDirectory(children[i]); else children[i].delete();
        }
    }
    dir.delete();
}

function operationBackups(since) {
    var dir = archive.AndroidSettingsAutoBackup.directory();
    var children = dir.listFiles();
    var found = [];
    if (children) {
        for (var i = 0; i < children.length; i++) {
            var name = String(children[i].getName());
            if (name.indexOf('autojs6-settings-auto-operation-') === 0 && children[i].lastModified() >= since) found.push(children[i]);
        }
    }
    found.sort(function (a, b) {
        return b.lastModified() - a.lastModified();
    });
    return found;
}

function vaultBackups() {
    var children = VAULT.listFiles();
    var n = 0;
    if (children) {
        for (var i = 0; i < children.length; i++) {
            if (/^auto-(import|reset)-\d{8}-\d{6}(-\d+)?\.zip$/.test(String(children[i].getName()))) n++;
        }
    }
    return n;
}

function readState() {
    return JSON.parse(String(files.read(STATE.getPath())));
}

function writeState(state) {
    files.write(STATE.getPath(), JSON.stringify(state));
}

// Keeps every setting and the internal backups for restore; null (and a FAIL line) when a previous check was not
// restored, whose kept files must not be overwritten.
function keep() {
    if (ORIGINAL.isFile()) {
        check('keep', false, 'a previous check was not restored; run the restore phase first');
        return null;
    }
    PRIVATE.mkdirs();
    var original = writeArchive(ORIGINAL, Category.ALL);
    var keptBackups = VAULT.isDirectory() ? copyDirectory(VAULT, VAULT_COPY) : 0;
    var plugin = installedPlugin();
    var state = {
        preparedAt: java.lang.System.currentTimeMillis(),
        plugin: plugin,
        pluginOriginal: plugin ? bool(keys.PluginCenterKeys.PLUGIN_ENABLED.key(plugin).get()) : null,
        languageB: String(stored(keys.AppearanceKeys.APP_LANGUAGE) || ''),
        iconTransparent: Boolean(Packages.org.autojs.autojs.util.LauncherIconComponents.isTransparentBackground(context)),
    };
    writeState(state);
    check('kept', true, original.getCategories().size() + ' categories, ' + keptBackups + ' internal backup files');
    return state;
}

function kept() {
    if (!ORIGINAL.isFile()) {
        check(AJ6_PHASE, false, 'run the keep (or prepare) phase first');
        return null;
    }
    return readState();
}

function prepare() {
    var state = keep();
    if (!state) return;
    var plugin = state.plugin;
    var enabled = keys.PluginCenterKeys.PLUGIN_ENABLED;
    var pluginNow = state.pluginOriginal;

    setValues(VALUES.A, AJ6_LANGUAGE_A || null, AJ6_ICON_A || null, false);
    var outDir = new File(AJ6_OUT);
    var defaults = [];
    var all = Category.ALL;
    for (var i = 0; i < all.size(); i++) {
        var category = all.get(i);
        if (!category.isData() && (category.getDefaultSelected() || category.getSensitive())) defaults.push(category);
    }
    var a = writeArchive(new File(outDir, 'a.zip'), list(defaults));
    check('a.zip', true, a.getCategories().size() + ' categories (the default ones and credentials)');

    if (plugin) {
        var snapshot = archive.SettingsSnapshot.capture(list([Category.PLUGIN_ENABLE_STATE]));
        var storeName = keys.NamedStores.PLUGIN_CENTER_ENABLE_STATE.getName();
        var entries = new java.util.LinkedHashMap();
        var categoryOf = snapshot.getCategories().isEmpty() ? null : snapshot.getCategories().get(0);
        if (categoryOf && categoryOf.getStores().get(storeName)) entries.putAll(categoryOf.getStores().get(storeName));
        entries.put(enabled.getPrefix() + plugin, new pref.TypedValue('bool', java.lang.Boolean.valueOf(!pluginNow)));
        entries.put(enabled.getPrefix() + MISSING_PACKAGE, new pref.TypedValue('bool', java.lang.Boolean.FALSE));
        var storesMap = new java.util.LinkedHashMap();
        storesMap.put(storeName, entries);
        var SnapshotCategory = archive['SettingsSnapshot$Category'];
        var modified = new archive.SettingsSnapshot(list([new SnapshotCategory('plugin_enable_state', storesMap)]));
        var stream = new java.io.FileOutputStream(new File(outDir, 'b.zip'));
        try {
            exporter.write(stream, modified, java.lang.System.currentTimeMillis(), host(), exporter.device(), archive.SettingsArchive.MAX_BYTES, deviceKey());
        } finally {
            stream.close();
        }
        check('b.zip', true, entries.size() + ' plugin states, ' + plugin + ' -> ' + !pluginNow + ', missing package included');
    } else {
        out('INFO no official plugin installed, b.zip skipped');
    }

    setValues(VALUES.B, AJ6_LANGUAGE_B || null, AJ6_ICON_B || null, true);
    check('prepared', true, 'values B set and applied');
}

function verify() {
    var expect = VALUES[AJ6_EXPECT];
    var appearance = keys.AppearanceKeys;
    var night = stored(appearance.NIGHT_MODE_ENABLED);
    var size = stored(keys.EditorKeys.EDITOR_TEXT_SIZE);
    var sort = stored(keys.PluginCenterKeys.SORT_ALL.sort);
    var theme = stored(appearance.THEME_COLOR_PRIMARY);
    check('theme-stored', theme !== null && (theme | 0) === expect.theme, '#' + (theme === null ? 'absent' : (theme >>> 0).toString(16)));
    check('night-stored', night !== null && bool(night) === expect.night, String(night));
    check('size-stored', size !== null && (size | 0) === expect.size, String(size));
    check('sort-stored', sort !== null && (sort | 0) === expect.sort, String(sort));
    var applied = Packages.org.autojs.autojs.theme.ThemeColorManager.INSTANCE.getColorPrimary() | 0;
    check('theme-applied', applied === expect.theme, '#' + (applied >>> 0).toString(16));
    var mode = Packages.androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode();
    var wanted = expect.night ? Packages.androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : Packages.androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
    check('night-applied', mode === wanted, 'mode ' + mode);
    if (AJ6_LANGUAGE_EXPECT) {
        var language = stored(appearance.APP_LANGUAGE);
        check('language-stored', String(language) === AJ6_LANGUAGE_EXPECT, String(language));
        var locales = Packages.androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags();
        out('INFO language-applied app locales [' + locales + '], resources ' + context.getResources().getConfiguration().getLocales().toLanguageTags());
    }
    if (AJ6_ICON_EXPECT) {
        var icon = stored(keys.GeneralKeys.LAUNCHER_ICON);
        var transparent = Packages.org.autojs.autojs.util.LauncherIconComponents.isTransparentBackground(context);
        var wantTransparent = AJ6_ICON_EXPECT === str(Packages.org.autojs.autojs6.R.string.key_launcher_icon_transparent_background);
        check('icon-stored', String(icon) === AJ6_ICON_EXPECT, String(icon));
        check('icon-component', transparent === wantTransparent, transparent ? 'transparent background alias enabled' : 'adaptive alias in use');
    }
    var token = stored(keys.CredentialKeys.GITHUB_TOKEN);
    check('token-kept', String(token) === TOKEN_B, 'the test token of this device is ' + (String(token) === TOKEN_B ? 'kept' : 'changed'));
    var state = readState();
    var last = new File(VAULT, archive.SettingsBackupVault.LAST_OPERATION);
    out('INFO internal backups ' + vaultBackups() + ' (at most ' + archive.SettingsBackupVault.KEEP + '), last-operation.json ' + (last.isFile() ? 'present' : 'absent'));
    check('internal-backups', vaultBackups() <= archive.SettingsBackupVault.KEEP, String(vaultBackups()));
    var ops = operationBackups(state.preparedAt);
    out('INFO operation backups since prepare ' + ops.length + (ops.length ? ', newest ' + ops[0].getPath() : ''));
}

function plugins() {
    var state = readState();
    if (!state.plugin) {
        out('INFO no plugin was flipped');
        return;
    }
    var enabled = keys.PluginCenterKeys.PLUGIN_ENABLED;
    var now = bool(enabled.key(state.plugin).get());
    var want = AJ6_EXPECT === 'flipped' ? !state.pluginOriginal : state.pluginOriginal;
    check('plugin-state', now === want, state.plugin + ' enabled ' + now);
    var store = stores.get(keys.NamedStores.PLUGIN_CENTER_ENABLE_STATE);
    check('missing-package', !store.contains(enabled.getPrefix() + MISSING_PACKAGE), 'not written');
    // What the result page counts: every entry of b.zip whose package is not installed here, the test package included.
    var transfer = Packages.org.autojs.autojs.ui.settings.transfer;
    var reader = readerOf(new File(new File(AJ6_OUT), 'b.zip'));
    var inspection = transfer.SettingsImporter.inspect(reader, 'b.zip', context.getPackageName());
    var plan = transfer.SettingsImporter.plan(reader, inspection, list(['plugin_enable_state']), transfer.SettingsImporter.environment(context));
    var missing = [];
    for (var i = 0; i < plan.getSkips().size(); i++) {
        var skip = plan.getSkips().get(i);
        if (String(skip.getReason()) === 'TARGET_MISSING') missing.push(String(skip.getDetail()));
    }
    out('INFO expected TARGET_MISSING ' + missing.length + ' (' + missing.join(', ') + ')');
}

function reset() {
    var ids = String(AJ6_CATEGORIES).split(',');
    var registry = pref.SettingsRegistry.INSTANCE.getKeys();
    var left = 0;
    ids.forEach(function (id) {
        for (var i = 0; i < registry.size(); i++) {
            var key = registry.get(i);
            if (key.getCategory() && String(key.getCategory().getId()) === id && storeOf(key).contains(key.getName())) left++;
        }
    });
    check('reset-cleared', left === 0, left + ' keys of ' + ids.join(',') + ' still stored');
    // A reset that includes appearance clears the theme color too, so there is no other category to look at here.
    // zh-CN: 恢复默认含外观分类时主题色也被清除, 此处不再检查其他分类.
    if (ids.indexOf('appearance') >= 0) {
        out('INFO reset-others-kept skipped: appearance is one of the reset categories');
        return;
    }
    var theme = stored(keys.AppearanceKeys.THEME_COLOR_PRIMARY);
    check('reset-others-kept', theme !== null, 'appearance ' + (theme === null ? 'cleared' : 'kept'));
}

// P7.6: c.zip with the explorer and editor appearance categories of this device, whose working directory names a
// directory that does not exist and whose editor font is a catalog font that is not downloaded (appendix D 10 - 12).
function deployPrepare() {
    var state = readState();
    var directory = 'aj6-deploy-check-' + java.lang.System.currentTimeMillis();
    var fonts = Packages.org.autojs.autojs.ui.edit.editor.ace.AceEditorFontManager.get(context);
    var installer = new Packages.org.autojs.autojs.core.pref.deploy.AndroidEditorFontInstaller(context);
    var catalog = fonts.catalog();
    var font = null;
    if (catalog) {
        for (var i = 0; i < catalog.getFonts().size(); i++) {
            var id = String(catalog.getFonts().get(i).getId());
            if (!installer.isInstalled(id)) {
                font = id;
                break;
            }
        }
    }
    var snapshot = archive.SettingsSnapshot.capture(list([Category.EXPLORER, Category.EDITOR_APPEARANCE]));
    var SnapshotCategory = archive['SettingsSnapshot$Category'];
    var categories = [];
    for (var c = 0; c < snapshot.getCategories().size(); c++) {
        var category = snapshot.getCategories().get(c);
        var storesMap = new java.util.LinkedHashMap();
        var entries = new java.util.LinkedHashMap();
        var defaults = category.getStores().get('default');
        if (defaults) entries.putAll(defaults);
        if (String(category.getId()) === 'explorer') {
            entries.put(keys.ExplorerKeys.WORKING_DIRECTORY.getName(), new pref.TypedValue('string', directory));
        } else if (font) {
            entries.put(keys.EditorKeys.ACE_EDITOR_FONT.getName(), new pref.TypedValue('string', font));
        }
        // The other stores of the category as they are (a Java map is wrapped for JavaScript, so it is copied whole).
        storesMap.putAll(category.getStores());
        storesMap.put('default', entries);
        categories.push(new SnapshotCategory(category.getId(), storesMap));
    }
    var stream = new java.io.FileOutputStream(new File(new File(AJ6_OUT), 'c.zip'));
    try {
        exporter.write(stream, new archive.SettingsSnapshot(list(categories)), java.lang.System.currentTimeMillis(), host(), exporter.device(), archive.SettingsArchive.MAX_BYTES, deviceKey());
    } finally {
        stream.close();
    }
    state.deployDirectory = directory;
    state.deployFont = font;
    state.directoryBefore = String(stored(keys.ExplorerKeys.WORKING_DIRECTORY));
    state.fontBefore = String(stored(keys.EditorKeys.ACE_EDITOR_FONT));
    writeState(state);
    check('c.zip', true, 'working directory ' + directory + ' (absent), editor font ' + (font || 'none: every catalog font is installed'));
}

// Checks the two deployment items after an import of c.zip: AJ6_EXPECT "deployed" (directory created and key
// written, font downloaded and key written) or "declined" (both keys keep their values of before).
function deployVerify() {
    var state = readState();
    var directoryKey = String(stored(keys.ExplorerKeys.WORKING_DIRECTORY));
    var fontKey = String(stored(keys.EditorKeys.ACE_EDITOR_FONT));
    var device = new Packages.org.autojs.autojs.core.pref.deploy.AndroidDeployments(context);
    var created = device.directory(state.deployDirectory).isDirectory();
    var installer = new Packages.org.autojs.autojs.core.pref.deploy.AndroidEditorFontInstaller(context);
    var fontInstalled = state.deployFont ? installer.isInstalled(state.deployFont) : false;
    out('INFO storage access ' + device.hasStorageAccess() + ', Ace editor plugin ' + device.hasAceEditorPlugin());
    if (AJ6_EXPECT === 'deployed') {
        check('directory-created', created && directoryKey === state.deployDirectory, 'key ' + (directoryKey === state.deployDirectory ? 'written' : 'not written') + ', directory ' + (created ? 'exists' : 'absent'));
        if (state.deployFont) check('font-installed', fontInstalled && fontKey === state.deployFont, state.deployFont + ' ' + (fontInstalled ? 'installed' : 'not installed') + ', key ' + (fontKey === state.deployFont ? 'written' : 'not written'));
    } else {
        check('directory-kept', directoryKey === state.directoryBefore, 'key ' + (directoryKey === state.directoryBefore ? 'kept' : 'changed') + ', directory ' + (created ? 'exists' : 'absent'));
        check('font-kept', fontKey === state.fontBefore, 'key ' + (fontKey === state.fontBefore ? 'kept' : 'changed') + ', font ' + (fontInstalled ? 'installed' : 'not installed'));
    }
}

// Java strings for a Kotlin list compared by equality (a concatenated JavaScript string is not a java.lang.String).
function strings(items) {
    return list(items.map(function (it) {
        return new java.lang.String(String(it));
    }));
}

// P7.7, appendix C Q30: one PBKDF2-HMAC-SHA256 derivation with the iterations the export writes, which the export (a
// password set) and the import (a password checked) each take once. The first run includes the class loading and the
// JIT warm-up of this process; a debuggable build has no ahead-of-time compiled code, so its times are upper bounds.
function pbkdf2() {
    var Password = archive.SettingsPassword;
    var iterations = Password.ITERATIONS;
    var rounds = Number(AJ6_ROUNDS || 5);
    var password = new java.lang.String('aj6-check-timing').getBytes('UTF-8');
    var salt = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 16);
    new java.security.SecureRandom().nextBytes(salt);
    var times = [];
    for (var i = 0; i <= rounds; i++) {
        var start = java.lang.System.nanoTime();
        Password.Companion.pbkdf2(password, salt, iterations, 32);
        times.push(Math.round((java.lang.System.nanoTime() - start) / 1e6));
    }
    var warm = times.slice(1).sort(function (a, b) {
        return a - b;
    });
    var debuggable = (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) !== 0;
    out('INFO pbkdf2 ' + iterations + ' iterations, ' + (debuggable ? 'debuggable' : 'release') + ' build, ' + android.os.Build.MODEL + ' (API ' + android.os.Build.VERSION.SDK_INT + ')');
    check('pbkdf2', true, 'first ' + times[0] + ' ms, then median ' + warm[Math.floor(warm.length / 2)] + ' ms (min ' + warm[0] + ', max ' + warm[warm.length - 1] + ', ' + warm.length + ' runs)');
    // For comparison: the "PBKDF2WithHmacSHA256" of the platform (Android 8+, native), which SettingsPassword does
    // not use because of Android 7. Same password and salt, so the derived keys must be equal.
    if (android.os.Build.VERSION.SDK_INT < 26) return;
    var chars = new java.lang.String('aj6-check-timing').toCharArray();
    var factory = javax.crypto.SecretKeyFactory.getInstance('PBKDF2WithHmacSHA256');
    var platform = [];
    var key = null;
    for (var j = 0; j <= rounds; j++) {
        var begin = java.lang.System.nanoTime();
        key = factory.generateSecret(new javax.crypto.spec.PBEKeySpec(chars, salt, iterations, 256)).getEncoded();
        platform.push(Math.round((java.lang.System.nanoTime() - begin) / 1e6));
    }
    var same = java.util.Arrays.equals(key, Password.Companion.pbkdf2(password, salt, iterations, 32));
    var sorted = platform.slice(1).sort(function (a, b) {
        return a - b;
    });
    check('pbkdf2-platform', same, 'SecretKeyFactory PBKDF2WithHmacSHA256: first ' + platform[0] + ' ms, then median ' + sorted[Math.floor(sorted.length / 2)] + ' ms, same key ' + same);
}

// P7.7: sets the test token of this device (AJ6_EXPECT "A" on the exporting device, "B" on the importing one) after
// keep, and writes d.zip: the credentials sealed with the device key only (an archive of before P7.7, without a
// password slot), which another device reads as LOCKED without asking for a password.
function token() {
    if (!kept()) return;
    var value = {A: TOKEN_A, B: TOKEN_B}[AJ6_EXPECT];
    put(keys.CredentialKeys.GITHUB_TOKEN, value);
    var d = writeArchive(new File(new File(AJ6_OUT), 'd.zip'), list([Category.CREDENTIALS]));
    check('token', tokenName() === AJ6_EXPECT, 'test token ' + tokenName() + ' set, d.zip with ' + d.getCategories().size() + ' category (device slot only)');
}

function tokenName() {
    var token = stored(keys.CredentialKeys.GITHUB_TOKEN);
    if (token === null) return 'absent';
    return String(token) === TOKEN_A ? 'A' : String(token) === TOKEN_B ? 'B' : 'other';
}

function tokenVerify() {
    check('token-' + AJ6_EXPECT, tokenName() === AJ6_EXPECT, 'the test token of this device is ' + tokenName());
}

// P7.7: how this device reads the credentials of AJ6_FILE (PLAIN, DEVICE, PASSWORD or LOCKED; AJ6_EXPECT).
function access() {
    var file = new File(AJ6_FILE);
    var reader = readerOf(file);
    var categories = reader.manifest().getCategories();
    var found = false;
    for (var i = 0; i < categories.size(); i++) {
        if (String(categories.get(i).getId()) === 'credentials') found = true;
    }
    var now = found ? String(reader.access('credentials')) : 'none';
    check('access', now === AJ6_EXPECT, file.getName() + ' credentials ' + now);
}

var DATA = pref.data;
var TEST_PREFIX = 'aj6-check';

function pngIcon(color) {
    var bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888);
    // The int overload: Rhino would choose eraseColor(long) (a ColorLong, API 29+) for a number.
    bitmap['eraseColor(int)'](color);
    var bytes = new java.io.ByteArrayOutputStream();
    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes);
    bitmap.recycle();
    return bytes.toByteArray();
}

function shortcutRepository() {
    return Packages.org.autojs.autojs.external.shortcut.ShortcutBackupRepository.Companion.get(context);
}

// The test shortcuts of the shortcut backup: id -> status.
function testShortcuts() {
    var entries = shortcutRepository().read().getEntries();
    var found = {};
    for (var i = 0; i < entries.size(); i++) {
        var id = String(entries.get(i).getId());
        if (id.indexOf(TEST_PREFIX + '-') === 0) found[id] = String(entries.get(i).getStatus());
    }
    return found;
}

// The test color libraries ({id, name}), found by their names; read from the database, the repository only suspends.
function testLibraries() {
    var db = Packages.org.autojs.autojs.theme.app.ColorHistoryDatabase.Companion.getInstance(context).getOpenHelper().getReadableDatabase();
    var cursor = db.query("SELECT id, name FROM color_library WHERE name LIKE '" + TEST_PREFIX + "%'");
    var found = [];
    try {
        while (cursor.moveToNext()) found.push({id: String(cursor.getLong(0)), name: String(cursor.getString(1))});
    } finally {
        cursor.close();
    }
    return found;
}

function names(libraries) {
    return libraries.map(function (it) {
        return it.name;
    }).sort();
}

// P10.2 (appendix D item 13): three test shortcuts (the first one without an icon) and two plain test color libraries,
// added the way an import adds them; restore deletes them again.
function dataPrepare() {
    var state = kept();
    if (!state) return;
    if (Object.keys(testShortcuts()).length || testLibraries().length) {
        check('data-prepare', false, 'test entries of an earlier check are still there; run data-delete first');
        return;
    }
    var shortcut = Packages.org.autojs.autojs.external.shortcut;
    var Entry = shortcut.ShortcutBackupEntry;
    var SAVED = shortcut['ShortcutBackupEntry$Status'].SAVED;
    var specs = [['log', null], ['docs', pngIcon(0xFF3F51B5 | 0)], ['settings', pngIcon(0xFF009688 | 0)]];
    var entries = specs.map(function (spec, i) {
        var label = TEST_PREFIX + ' ' + (i + 1) + (spec[1] ? '' : ' no icon');
        return new Entry(TEST_PREFIX + '-shortcut-' + (i + 1), label, label, spec[0], null, null, null, spec[1], SAVED, false, null, false);
    });
    var shortcuts = DATA.SettingsDataImport['import'](context, DATA.ShortcutDataCodec.encode(new shortcut.ShortcutBackupState(list(entries), null, null, null)), null);
    var json = Packages.org.autojs.autojs.theme.library.ColorLibraryJson;
    var libraries = [1, 2].map(function (n) {
        var colors = list([new json.Color(0xFFE91E63 | 0, 'aj6 pink ' + n, 0, 0), new json.Color(0xFF4CAF50 | 0, 'aj6 green ' + n, 0, 0)]);
        return new json.PlainLibrary(TEST_PREFIX + '-' + n, colors);
    });
    var colors = DATA.SettingsDataImport['import'](context, DATA.ColorLibraryDataCodec.encode(list(libraries), java.lang.System.currentTimeMillis()), 'aj6-check.zip');
    state.dataShortcuts = Object.keys(testShortcuts()).sort();
    state.dataLibraries = names(testLibraries());
    writeState(state);
    check('data-shortcuts', state.dataShortcuts.length === 3 && !shortcuts.getFailure(), state.dataShortcuts.join(', ') + (shortcuts.getFailure() ? ', ' + shortcuts.getFailure() : ''));
    check('data-libraries', state.dataLibraries.length === 2 && !colors.getFailure(), state.dataLibraries.join(', ') + (colors.getFailure() ? ', ' + colors.getFailure() : ''));
}

// P10.2: the data of AJ6_FILE, the export of the maintainer: 2 test shortcuts and 1 test color library, nothing else.
function dataInspect() {
    var state = kept();
    if (!state) return;
    var reader = readerOf(new File(AJ6_FILE));
    var data = reader.manifest().getData();
    var found = {};
    for (var i = 0; i < data.size(); i++) {
        var entry = data.get(i);
        var id = String(entry.getId());
        var entries = DATA.SettingsDataEntries.entries(id, reader.data(entry.getFile()));
        var items = [];
        for (var j = 0; j < entries.size(); j++) items.push(String(id === 'data_shortcuts' ? entries.get(j).getId() : entries.get(j).getTitle()));
        found[id] = {manifest: Number(entry.getEntries()), items: items};
    }
    var shortcuts = found.data_shortcuts || {manifest: 0, items: []};
    var libraries = found.data_color_library || {manifest: 0, items: []};
    var testShortcutIds = shortcuts.items.filter(function (it) {
        return state.dataShortcuts.indexOf(it) >= 0;
    });
    var testLibraryNames = libraries.items.filter(function (it) {
        return state.dataLibraries.indexOf(it) >= 0;
    });
    check('export-shortcuts', shortcuts.manifest === 2 && shortcuts.items.length === 2 && testShortcutIds.length === 2, 'manifest ' + shortcuts.manifest + ', file ' + shortcuts.items.length + ' (' + testShortcutIds.join(', ') + ')');
    check('export-libraries', libraries.manifest === 1 && libraries.items.length === 1 && testLibraryNames.length === 1, 'manifest ' + libraries.manifest + ', file ' + libraries.items.length + ' (' + testLibraryNames.join(', ') + ')');
    state.exportedShortcuts = testShortcutIds.sort();
    state.exportedLibraries = testLibraryNames.sort();
    writeState(state);
}

// Deletes the test shortcuts and color libraries, the way undoing an import does.
function removeTestData() {
    var shortcuts = DATA.SettingsDataImport.remove(context, 'data_shortcuts', strings(Object.keys(testShortcuts())));
    var libraries = DATA.SettingsDataImport.remove(context, 'data_color_library', strings(testLibraries().map(function (it) {
        return it.id;
    })));
    return {shortcuts: shortcuts, libraries: libraries, left: Object.keys(testShortcuts()).length + testLibraries().length};
}

function dataDelete() {
    if (!kept()) return;
    var removed = removeTestData();
    check('data-deleted', removed.left === 0, removed.shortcuts.getRemoved() + ' test shortcuts and ' + removed.libraries.getRemoved() + ' test color libraries deleted, ' + removed.left + ' left');
}

// P10.2 / P10.3: AJ6_EXPECT "imported" (the exported test entries are back and the last operation records them as
// added) or "undone" (they are gone again and the record is cleared).
function dataVerify() {
    var state = kept();
    if (!state) return;
    var shortcuts = testShortcuts();
    var ids = Object.keys(shortcuts).sort();
    var libraries = testLibraries();
    var exportedShortcuts = state.exportedShortcuts || [];
    var exportedLibraries = state.exportedLibraries || [];
    var last = new File(VAULT, archive.SettingsBackupVault.LAST_OPERATION);
    var statuses = ids.map(function (id) {
        return id + ' ' + shortcuts[id];
    }).join(', ');
    if (AJ6_EXPECT === 'imported') {
        check('shortcuts-imported', ids.join() === exportedShortcuts.join(), ids.length + ' test shortcuts (' + statuses + '), exported ' + exportedShortcuts.length);
        check('libraries-imported', names(libraries).join() === exportedLibraries.join(), libraries.length + ' test color libraries (' + names(libraries).join(', ') + '), exported ' + exportedLibraries.length);
        var record = last.isFile() ? JSON.parse(String(files.read(last.getPath()))) : {};
        var added = record.added || {};
        var addedShortcuts = (added.data_shortcuts || []).slice().sort();
        var addedLibraries = (added.data_color_library || []).slice().sort();
        var libraryIds = libraries.map(function (it) {
            return it.id;
        }).sort();
        check('added-recorded', addedShortcuts.join() === ids.join() && addedLibraries.join() === libraryIds.join(), 'last operation ' + (record.operation || 'absent') + ': added ' + addedShortcuts.length + ' shortcuts, ' + addedLibraries.length + ' color libraries');
    } else {
        check('shortcuts-undone', ids.length === 0, ids.length + ' test shortcuts left' + (ids.length ? ' (' + statuses + ')' : ''));
        check('libraries-undone', libraries.length === 0, libraries.length + ' test color libraries left');
        check('record-cleared', !last.isFile(), 'last-operation.json ' + (last.isFile() ? 'present' : 'absent'));
        var ops = operationBackups(state.preparedAt);
        out('INFO operation backups since keep ' + ops.length);
    }
}

// P11.1 (appendix D item 14): clears key_setup_wizard_welcome_done, the welcome then shows on the next start of the
// home page; restore puts the kept value back.
function welcomeReset() {
    var state = kept();
    if (!state) return;
    var key = keys.MiscStateKeys.SETUP_WIZARD_WELCOME_DONE;
    state.welcomeBefore = stored(key) === null ? null : bool(stored(key));
    writeState(state);
    key.remove(true);
    check('welcome-reset', stored(key) === null, 'key_setup_wizard_welcome_done was ' + state.welcomeBefore + ', now absent');
}

function welcomeVerify() {
    var now = stored(keys.MiscStateKeys.SETUP_WIZARD_WELCOME_DONE);
    check('welcome-done', now !== null && bool(now), 'key_setup_wizard_welcome_done ' + now);
}

function restore() {
    if (!ORIGINAL.isFile()) {
        check('restore', false, 'nothing kept');
        return;
    }
    var state = readState();
    var reader = readerOf(ORIGINAL);
    var manifest = reader.manifest();
    var ids = new java.util.ArrayList();
    for (var i = 0; i < manifest.getCategories().size(); i++) ids.add(manifest.getCategories().get(i).getId());
    var environment = new archive.ImportEnvironment(new java.util.HashSet(), false);
    var plan = archive.SettingsImportPlanner.plan(reader.categories(ids, manifest), ids, environment, false);
    var changes = archive.SettingsImportApplier.write(plan);
    onMain(function () {
        archive.SettingsEffects.run(changes, function (entry) {
            return effects().actionOf(entry);
        });
    });
    // The state of the plugins of this device is not planned (TARGET_MISSING needs the installed packages): put it back.
    if (state.plugin) keys.PluginCenterKeys.PLUGIN_ENABLED.key(state.plugin).set(java.lang.Boolean.valueOf(state.pluginOriginal), true);
    // The launcher component of prepare, and a stored icon that matches it (older builds could store the other one).
    var icons = Packages.org.autojs.autojs.util.LauncherIconComponents;
    var transparent = state.iconTransparent === true;
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        if (icons.isTransparentBackground(context) !== transparent) {
            onMain(function () {
                if (transparent) icons.switchComponent(context, icons.ALIAS_TRANSPARENT_BACKGROUND, icons.ALIAS_ADAPTIVE);
                else icons.switchComponent(context, icons.ALIAS_ADAPTIVE, icons.ALIAS_TRANSPARENT_BACKGROUND);
            });
        }
        put(keys.GeneralKeys.LAUNCHER_ICON, str(transparent ? Packages.org.autojs.autojs6.R.string.key_launcher_icon_transparent_background : Packages.org.autojs.autojs6.R.string.key_launcher_icon_adaptive));
    }
    var vaultFiles = VAULT.listFiles();
    if (vaultFiles) for (var j = 0; j < vaultFiles.length; j++) vaultFiles[j].delete();
    var restored = VAULT_COPY.isDirectory() ? copyDirectory(VAULT_COPY, VAULT) : 0;
    var ops = operationBackups(state.preparedAt);
    ops.forEach(function (f) {
        f.delete();
    });
    // A directory and a font that c.zip deployed (P7.6) are removed again.
    if (state.deployDirectory) {
        var made = new Packages.org.autojs.autojs.core.pref.deploy.AndroidDeployments(context).directory(state.deployDirectory);
        if (made.isDirectory()) deleteDirectory(made);
    }
    if (state.deployFont) Packages.org.autojs.autojs.ui.edit.editor.ace.AceEditorFontManager.get(context).delete(state.deployFont);
    // The test shortcuts and color libraries (P10.2), and the welcome state (P11.1).
    var data = removeTestData();
    if ('welcomeBefore' in state) {
        var welcome = keys.MiscStateKeys.SETUP_WIZARD_WELCOME_DONE;
        if (state.welcomeBefore === null) welcome.remove(true); else welcome.set(java.lang.Boolean.valueOf(state.welcomeBefore), true);
    }
    deleteDirectory(PRIVATE);
    check('restored', data.left === 0, plan.getPuts().size() + ' written, ' + plan.getRemoves().size() + ' removed, ' + changes.size() + ' changed, ' + restored + ' internal backup files back, ' + ops.length + ' operation backups deleted, ' + (data.shortcuts.getRemoved() + data.libraries.getRemoved()) + ' test data entries deleted (' + data.left + ' left)');
}

try {
    ({
        prepare: prepare, keep: keep, verify: verify, plugins: plugins, reset: reset, restore: restore,
        'deploy-prepare': deployPrepare, 'deploy-verify': deployVerify,
        pbkdf2: pbkdf2, token: token, 'token-verify': tokenVerify, access: access,
        'data-prepare': dataPrepare, 'data-inspect': dataInspect, 'data-delete': dataDelete, 'data-verify': dataVerify,
        'welcome-reset': welcomeReset, 'welcome-verify': welcomeVerify,
    })[AJ6_PHASE]();
} catch (e) {
    check(AJ6_PHASE, false, 'threw ' + e + (e.lineNumber ? ' (line ' + e.lineNumber + ')' : ''));
}
out('done');
files.write(RESULT_FILE, resultLines.join('\n'));
