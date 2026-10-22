// Checks settings.export and settings.categories, the script API of docs/settings/ROADMAP.md P12.1 (T15, E3), as a
// script calls them. AJ6_PATH is the archive of the explicit export, which requests credentials (they must be skipped).
// The default export goes to the backup directory of the working directory; its path and the directories it created are
// reported so that the caller pulls and removes them. Lines carry ids, paths and counts only, never values (R6). Run by
// tools/settings/device_script_export_check.py.
// zh-CN: 以脚本调用的方式检查 settings.export 与 settings.categories (ROADMAP P12.1, T15, E3). AJ6_PATH 为显式导出的归档, 该次导出请求
// 了凭据 (须被略去). 默认导出写入工作目录下的备份目录, 报告其路径与新建的目录, 由调用方拉取并删除. 只输出 id, 路径与计数, 不输出值 (R6).

var MARK = 'AJ6SET';
var RESULT_FILE = files.join(files.cwd(), 'aj6set-result.txt');
var resultLines = [];

function out(line) {
    console.log(MARK + ' ' + line);
    resultLines.push(line);
}

function check(name, ok, detail) {
    out((ok ? 'PASS ' : 'FAIL ') + name + (detail === undefined ? '' : ' ' + detail));
}

function info(name, value) {
    out('INFO ' + name + ' ' + value);
}

function thrown(block) {
    try {
        block();
        return null;
    } catch (e) {
        return String(e);
    }
}

function absolute(path) {
    return String(new java.io.File(path).getAbsolutePath());
}

function parentOf(path) {
    return String(new java.io.File(path).getParent());
}

function isZip(path) {
    var stream = new java.io.FileInputStream(path);
    try {
        return stream.read() === 0x50 && stream.read() === 0x4B;
    } finally {
        stream.close();
    }
}

var NAME = /^autojs6-settings-[0-9]{8}-[0-9]{6}(-[0-9]+)?[.]zip$/;

try {
    // settings.categories(): every category in page order, counted as the export wizard counts them.
    var all = settings.categories();
    var known = Packages.org.autojs.autojs.core.pref.SettingCategory.ALL;
    var knownIds = [];
    for (var i = 0; i < known.size(); i++) knownIds.push(String(known.get(i).getId()));
    var byId = {};
    all.forEach(function (c) { byId[c.id] = c; });
    check('categories-order', all.map(function (c) { return c.id; }).join(',') === knownIds.join(','), all.length + ' categories');
    check('categories-shape', all.every(function (c) {
        return typeof c.title === 'string' && c.title.length > 0 && (c.kind === 'settings' || c.kind === 'data')
            && typeof c.entries === 'number' && c.entries >= 0 && typeof c.defaultSelected === 'boolean' && typeof c.sensitive === 'boolean';
    }));
    var sensitive = all.filter(function (c) { return c.sensitive; }).map(function (c) { return c.id; }).join(',');
    check('categories-sensitive', sensitive === 'credentials' && !byId.credentials.defaultSelected, sensitive);
    var data = all.filter(function (c) { return c.kind === 'data'; }).map(function (c) { return c.id; }).join(',');
    check('categories-data', data === 'data_shortcuts,data_color_library', data);
    info('entries', all.filter(function (c) { return c.entries > 0; }).map(function (c) { return c.id + '=' + c.entries; }).join(','));

    // An explicit path, credentials requested: skipped as sensitive; a data category is written or skipped as empty.
    var requested = ['credentials', 'general', 'appearance', 'appearance', 'data_shortcuts', 'data_color_library'];
    var r = settings.export(AJ6_PATH, requested);
    check('explicit-path', r.path === absolute(AJ6_PATH), r.path);
    check('explicit-file', files.exists(r.path) && isZip(r.path), new java.io.File(r.path).length() + ' bytes');
    var skipped = r.skipped.map(function (s) { return s.id + ':' + s.reason; }).join(',');
    check('explicit-credentials', r.categories.indexOf('credentials') < 0 && r.skipped.some(function (s) { return s.id === 'credentials' && s.reason === 'sensitive'; }), skipped);
    check('explicit-settings', r.categories.slice(0, 2).join(',') === 'appearance,general', r.categories.join(','));
    ['data_shortcuts', 'data_color_library'].forEach(function (id) {
        var written = r.categories.indexOf(id) >= 0;
        var empty = r.skipped.some(function (s) { return s.id === id && s.reason === 'empty'; });
        check('explicit-' + id, byId[id].entries > 0 ? written && !empty : empty && !written,
            byId[id].entries + ' entries, ' + (written ? 'written' : empty ? 'skipped as empty' : 'missing'));
    });
    var sum = r.categories.reduce(function (a, id) { return a + byId[id].entries; }, 0);
    check('explicit-entries', r.entries === sum, r.entries + '/' + sum + ', unexportable ' + r.unexportable);
    info('explicit-categories', r.categories.join(','));
    info('explicit-entries', r.entries);

    // Defaults: the categories selected by default, written to <working directory>/backups/settings/.
    var wd = String(Packages.org.autojs.autojs.util.WorkingDirectoryUtils.getPath());
    var backups = files.join(wd, 'backups');
    var directory = files.join(backups, 'settings');
    var created = [directory, backups].filter(function (d) { return !files.exists(d); });
    var d = settings.export();
    info('default-path', d.path);
    info('default-created', created.length ? created.join(',') : '-');
    check('default-directory', parentOf(d.path) === absolute(directory), parentOf(d.path));
    check('default-name', NAME.test(String(new java.io.File(d.path).getName())), String(new java.io.File(d.path).getName()));
    var defaults = all.filter(function (c) { return c.defaultSelected; }).map(function (c) { return c.id; });
    var settingsDefaults = defaults.filter(function (id) { return byId[id].kind === 'settings'; });
    check('default-categories', d.categories.every(function (id) { return defaults.indexOf(id) >= 0; })
        && settingsDefaults.every(function (id) { return d.categories.indexOf(id) >= 0; })
        && ['credentials', 'developer', 'other'].every(function (id) { return d.categories.indexOf(id) < 0; }),
        d.categories.length + '/' + defaults.length + ' categories');
    check('default-skipped', d.skipped.every(function (s) { return s.reason === 'empty'; }),
        d.skipped.map(function (s) { return s.id + ':' + s.reason; }).join(',') || '-');
    info('default-categories', d.categories.join(','));
    info('default-entries', d.entries);

    // A relative directory is resolved against the directory of the script and gets the default name.
    var rel = settings.export('aj6-rel/', 'appearance');
    check('relative-directory', parentOf(rel.path) === absolute(files.join(files.cwd(), 'aj6-rel')) && NAME.test(String(new java.io.File(rel.path).getName())), rel.path);
    files.removeDir(files.join(files.cwd(), 'aj6-rel'));

    // A named file that exists is replaced, and no temporary file is left next to it.
    var replaced = files.join(parentOf(AJ6_PATH), 'replaced.zip');
    files.write(replaced, 'old');
    var o = settings.export(replaced, ['appearance']);
    var names = new java.io.File(parentOf(AJ6_PATH)).list();
    var temporary = 0;
    for (var n = 0; n < names.length; n++) temporary += /[.]tmp$/.test(String(names[n])) ? 1 : 0;
    check('replace-file', o.path === absolute(replaced) && isZip(replaced) && temporary === 0, new java.io.File(replaced).length() + ' bytes, ' + temporary + ' temporary files');
    files.remove(replaced);

    // Bad arguments are refused before anything is written.
    [
        ['path-number', function () { settings.export(1); }],
        ['path-blank', function () { settings.export(' '); }],
        ['categories-number', function () { settings.export(null, 1); }],
        ['categories-empty', function () { settings.export(null, []); }],
        ['categories-unknown', function () { settings.export(null, 'nope'); }],
        ['categories-only-credentials', function () { settings.export(null, ['credentials']); }],
        ['export-three-arguments', function () { settings.export(null, null, 1); }],
        ['categories-argument', function () { settings.categories(1); }],
    ].forEach(function (pair) {
        var error = thrown(pair[1]);
        check('refuses-' + pair[0], error !== null, error === null ? 'no error' : error.split(':')[0]);
    });

    // Both block, so both are refused on the UI thread (the message names the function).
    ['export', 'categories'].forEach(function (name) {
        var error = ui.run(function () {
            return thrown(function () { name === 'export' ? settings.export(files.join(parentOf(AJ6_PATH), 'ui.zip')) : settings.categories(); });
        });
        check('ui-thread-' + name, error !== null && error.indexOf('settings.' + name) >= 0 && !files.exists(files.join(parentOf(AJ6_PATH), 'ui.zip')),
            error === null ? 'no error' : 'refused');
    });
} catch (e) {
    out('FAIL script-export-check threw ' + e);
}
out('done');
files.write(RESULT_FILE, resultLines.join(String.fromCharCode(10)));
