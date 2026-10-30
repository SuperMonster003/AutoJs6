// Runs the export of the wizard on a device with every category ticked and a password (docs/settings/ROADMAP.md P6.4,
// P7.7, P10.2, E3): SettingsExporter.export writes an archive into the cache of the app, which is read back and deleted.
// A data category is in the archive exactly when it has entries, the others are left out without failing the export.
// Every category reads with the device key, the credentials with the password alone as well. The password is random and
// lines carry counts only, never values (R6). Run by tools/settings/device_export_check.py.
// zh-CN: 在设备上以全部分类加口令运行向导的导出 (ROADMAP P6.4, P7.7, P10.2, E3): SettingsExporter.export 把归档写入应用缓存, 读回后删除.
// 数据分类有条目时才进入归档, 没有条目的略去且导出不失败. 每个分类都能用设备密钥读取, 凭据还能只用口令读取. 口令随机生成, 只输出计数, 不输出值 (R6).

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
var exporter = Packages.org.autojs.autojs.ui.settings.transfer.SettingsExporter;
var capture = pref.data.SettingsDataCapture;

var file = new java.io.File(context.getCacheDir(), 'aj6-export-check.zip');

function readerOf(key, password) {
    var opener = new Packages.kotlin.jvm.functions.Function0({
        invoke: function () {
            return new java.io.FileInputStream(file);
        },
    });
    return new archive.SettingsArchiveReader(opener, archive.SettingsArchive.MAX_BYTES, key, password);
}

function listOf(items, map) {
    var list = [];
    for (var i = 0; i < items.size(); i++) list.push(map(items.get(i)));
    return list;
}

function sameSorted(a, b) {
    return a.slice().sort().join() === b.slice().sort().join();
}

try {
    var all = pref.SettingCategory.ALL;
    var ids = new java.util.ArrayList(all.size());
    all.forEach(function (category) { ids.add(category.getId()); });

    var now = java.lang.System.currentTimeMillis();
    var withEntries = [];
    var read = capture.readAll(context, all, now);
    read.keySet().forEach(function (category) {
        var dataFile = read.get(category);
        check('read-' + category.getId(), dataFile !== null, dataFile === null ? 'unreadable' : dataFile.getEntries() + ' entries');
        if (dataFile !== null && dataFile.getEntries() > 0) withEntries.push(String(category.getId()));
    });

    var password = archive.SettingsPassword.of('aj6-export-check-' + java.util.UUID.randomUUID());
    var result = exporter.export(context, android.net.Uri.fromFile(file), ids, now, password, java.util.Collections.emptyMap());
    check('export', file.length() > 0, ids.size() + ' ticked, ' + result.getCategories() + ' categories, ' + result.getEntries() + ' entries, ' + file.length() + ' bytes');

    var key = archive.AndroidSettingsKeys.device(context);
    var reader = readerOf(key, null);
    var manifest = reader.manifest();
    var unreadable = 0;
    listOf(manifest.getCategories(), function (entry) {
        var id = String(entry.getId());
        if (reader.isLocked(id) || reader.category(id).getCategory().getEntries() !== entry.getEntries()) {
            unreadable++;
            check('category-' + id, false, 'locked or entries differ');
        }
    });
    check('categories-read', unreadable === 0, manifest.getCategories().size() + ' categories');

    var written = listOf(manifest.getData(), function (entry) { return String(entry.getId()); });
    check('data-with-entries-only', sameSorted(written, withEntries), 'written [' + written + ']');
    listOf(manifest.getData(), function (entry) {
        check('data-' + entry.getId(), reader.data(entry.getFile()).length > 0, entry.getEntries() + ' entries');
    });

    var credentials = manifest.getCategories().stream().filter(function (entry) { return entry.getSensitive(); }).findFirst().get();
    var byPassword = readerOf(null, password);
    check('credentials-by-password', !byPassword.isLocked(credentials.getId())
        && byPassword.category(credentials.getId()).getCategory().getEntries() === credentials.getEntries(), credentials.getEntries() + ' entries');
    check('credentials-locked-without-keys', readerOf(null, null).isLocked(credentials.getId()));
} catch (e) {
    out('FAIL export-check threw ' + (e.javaException || e));
} finally {
    file.delete();
}
check('archive-deleted', !file.exists());
out('done');
files.write(RESULT_FILE, resultLines.join(String.fromCharCode(10)));
