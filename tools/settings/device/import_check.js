// Device side of the import wizard check (docs/settings/ROADMAP.md P7.5 / P7.4 / P8.1, appendix D items 1 - 6, E3).
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
//   restore  imports the kept archive back (every category, effects applied), puts the internal backups back and
//            deletes the operation backups written since prepare and the private files.
// Lines carry counts and non-secret values only, never the token or other credentials (R6).
// zh-CN: 导入向导真机检查的设备端 (ROADMAP P7.5 / P7.4 / P8.1, 附录 D 第 1 - 6 项, E3). 由 device_import_check.py 设置 AJ6_PHASE 等变量:
// prepare 把全部设置 (以设备密钥加密的归档) 与内部备份保存到应用私有目录, 设置 "A" 值并写出 a.zip (默认分类加凭据) 与 b.zip
// (插件启用状态: 翻转一个已安装插件并加入一个未安装的包), 再设置并应用 "B" 值; verify 核对值为 AJ6_EXPECT, 本进程已生效, 测试令牌仍为
// "B", 并统计内部备份与操作后备份; plugins 核对插件状态; reset 核对恢复默认 (P8.1); restore 导入保存的归档还原全部设置, 放回内部备份,
// 删除 prepare 之后写出的操作后备份与私有文件. 只输出计数与非机密的值, 不输出令牌等凭据 (R6).

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
            java.nio.file.Files.copy(children[i].toPath(), new File(to, children[i].getName()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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

function prepare() {
    if (ORIGINAL.isFile()) {
        check('prepare', false, 'a previous check was not restored; run the restore phase first');
        return;
    }
    PRIVATE.mkdirs();
    var original = writeArchive(ORIGINAL, Category.ALL);
    var keptBackups = VAULT.isDirectory() ? copyDirectory(VAULT, VAULT_COPY) : 0;
    var plugin = installedPlugin();
    var enabled = keys.PluginCenterKeys.PLUGIN_ENABLED;
    var pluginKey = plugin ? enabled.key(plugin) : null;
    var pluginNow = plugin ? bool(pluginKey.get()) : null;
    files.write(STATE.getPath(), JSON.stringify({
        preparedAt: java.lang.System.currentTimeMillis(),
        plugin: plugin,
        pluginOriginal: pluginNow,
        languageB: String(stored(keys.AppearanceKeys.APP_LANGUAGE) || ''),
        iconTransparent: Boolean(Packages.org.autojs.autojs.util.LauncherIconComponents.isTransparentBackground(context)),
    }));
    check('kept', true, original.getCategories().size() + ' categories, ' + keptBackups + ' internal backup files');

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
    files.write(STATE.getPath(), JSON.stringify(state));
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
    deleteDirectory(PRIVATE);
    check('restored', true, plan.getPuts().size() + ' written, ' + plan.getRemoves().size() + ' removed, ' + changes.size() + ' changed, ' + restored + ' internal backup files back, ' + ops.length + ' operation backups deleted');
}

try {
    ({prepare: prepare, verify: verify, plugins: plugins, reset: reset, restore: restore, 'deploy-prepare': deployPrepare, 'deploy-verify': deployVerify})[AJ6_PHASE]();
} catch (e) {
    check(AJ6_PHASE, false, 'threw ' + e + (e.lineNumber ? ' (line ' + e.lineNumber + ')' : ''));
}
out('done');
files.write(RESULT_FILE, resultLines.join('\n'));
