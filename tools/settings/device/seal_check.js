// Checks the sealed credentials of settings archives on a device (docs/settings/ROADMAP.md P6.5, D23 / T21, E3).
// AJ6_MODE "own": captures the credentials and appearance categories, writes them to AJ6_PATH as the export wizard does
// (SettingsExporter with the device key of this app) and reads the archive back with that key. AJ6_MODE "other": reads
// AJ6_PATH, an archive another device wrote, with the key of this device: its credentials must be LOCKED while its
// appearance category still reads. Lines carry counts only, never values (R6). Run by tools/settings/device_seal_check.py.
// zh-CN: 在设备上检查设置归档中加密的凭据 (ROADMAP P6.5, D23 / T21, E3). AJ6_MODE "own": 读取凭据与外观两个分类, 按导出向导的方式
// (SettingsExporter, 本应用的设备密钥) 写入 AJ6_PATH, 再以该密钥读回. AJ6_MODE "other": 以本机密钥读取其他设备写的 AJ6_PATH: 其凭据须为
// LOCKED, 外观分类仍可读取. 只输出计数, 不输出值 (R6).

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

function readerOf(file, key) {
    var opener = new Packages.kotlin.jvm.functions.Function0({
        invoke: function () {
            return new java.io.FileInputStream(file);
        },
    });
    return new archive.SettingsArchiveReader(opener, archive.SettingsArchive.MAX_BYTES, key);
}

function reasonOf(block) {
    try {
        block();
        return 'none';
    } catch (e) {
        var error = e.javaException || (e.rhinoException && e.rhinoException.getCause()) || e;
        return error.getReason ? String(error.getReason()) : String(error);
    }
}

try {
    var key = archive.AndroidSettingsKeys.device(context);
    var file = new java.io.File(AJ6_PATH);
    if (AJ6_MODE === 'own') {
        var categories = java.util.Arrays.asList(pref.SettingCategory.APPEARANCE, pref.SettingCategory.CREDENTIALS);
        var snapshot = archive.SettingsSnapshot.capture(categories);
        file.getParentFile().mkdirs();
        var stream = new java.io.FileOutputStream(file);
        var written;
        try {
            written = exporter.write(stream, snapshot, java.lang.System.currentTimeMillis(), exporter.host(context), exporter.device(),
                archive.SettingsArchive.MAX_BYTES, key);
        } finally {
            stream.close();
        }
        check('written', written.getCategories().size() === 2, file.length() + ' bytes');
        var reader = readerOf(file, key);
        var manifest = reader.manifest();
        for (var i = 0; i < manifest.getCategories().size(); i++) {
            var entry = manifest.getCategories().get(i);
            var id = String(entry.getId());
            var locked = reader.isLocked(id);
            var held = reader.category(id).getCategory().getEntries();
            check('own-' + id, !locked && held === entry.getEntries(), held + '/' + entry.getEntries() + ' entries' + (entry.getSensitive() ? ', sensitive' : ''));
        }
    } else {
        var other = readerOf(file, key);
        var appearance = other.manifest().getCategories().get(0);
        check('other-appearance', !other.isLocked('appearance') && other.category('appearance').getCategory().getEntries() === appearance.getEntries(),
            appearance.getEntries() + ' entries');
        check('other-credentials-locked', other.isLocked('credentials'));
        var reason = reasonOf(function () { other.category('credentials'); });
        check('other-credentials-reason', reason === 'LOCKED', reason);
    }
} catch (e) {
    out('FAIL seal-check threw ' + e);
}
out('done');
files.write(RESULT_FILE, resultLines.join(String.fromCharCode(10)));
