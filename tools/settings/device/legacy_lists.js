// Device check for docs/settings/ROADMAP.md P1.4 (E3): list values written by Pref with Gson stay readable after the
// upgrade to the Pref that goes through SettingsStores and SettingsJson, and the new Pref writes the same text.
// tools/settings/device_store_check.py prepends "var AJ6_PHASE = 'before' | 'after'; var AJ6_STATE = {...};".
// Phase "before" runs on the old build: it records the raw text and the Gson-decoded items of three real list keys
// (seeding the ones that are absent) and writes a probe list. Phase "after" runs on the new build and compares.
// Seeded keys and probe keys are removed by phase "after"; existing values are only read.
// zh-CN: ROADMAP P1.4 的真机检查 (E3): 旧 Pref 用 Gson 写的列表值在升级到经 SettingsStores / SettingsJson 的 Pref 后仍可读,
// 且新 Pref 写出相同的文本. "before" 阶段在旧版本上记录三个真实列表键的原始文本与 Gson 解码结果 (缺失的键先写入样例) 并写入探针
// 列表; "after" 阶段在新版本上比较. 样例键与探针键由 "after" 阶段删除, 已有的值只读不改.

var Pref = org.autojs.autojs.core.pref.Pref.INSTANCE;
var R = org.autojs.autojs6.R;
var MARK = 'AJ6SET';
var PROBE = 'key_$_settings_probe_list';
var PROBE_NEW = 'key_$_settings_probe_list_new';

// Tricky items: path, HTML-escaped characters, quote and backslash, control characters, CJK and an emoji, U+2028.
var SAMPLE = [
    '/storage/emulated/0/Scripts',
    '/sdcard/a=b&c<d>e\'f',
    'quote" back\\slash',
    'tab\tnew\nline',
    String.fromCharCode(0x4e2d, 0x6587, 0x20, 0xd83d, 0xde00),
    String.fromCharCode(0x2028) + 'end',
];

var KEYS = [
    { id: 'working_directory_history', res: R.string.key_working_directory_history, read: function () { return org.autojs.autojs.util.WorkingDirectoryUtils.INSTANCE.getHistory(); } },
    { id: 'pc_server_address_history', res: R.string.key_pc_server_address_history, read: function () { return org.autojs.autojs.pluginclient.JsonSocketClient.Companion.getServerAddressHistory(); } },
    { id: 'ignored_updates', res: R.string.key_ignored_updates, read: null },
];

function report(id, ok, detail) {
    console.log(MARK + ' ' + (ok ? 'PASS' : 'FAIL') + ' ' + id + (detail === undefined ? '' : ' ' + detail));
}

function items(collection) {
    var out = [];
    var it = collection.iterator();
    while (it.hasNext()) out.push(String(it.next()));
    return out;
}

function linkedSet(list) {
    var set = new java.util.LinkedHashSet();
    list.forEach(function (s) { set.add(s); });
    return set;
}

function linkedList(list) {
    var l = new java.util.LinkedList();
    list.forEach(function (s) { l.add(s); });
    return l;
}

function raw(name) {
    var v = Pref.getStringOrNull(name);
    return v === null ? null : String(v);
}

function prefsText() {
    var f = new java.io.File(context.getApplicationInfo().dataDir, 'shared_prefs/' + context.getPackageName() + '_preferences.xml');
    // files.read rather than java.nio.file, which Android 7 does not have.
    // zh-CN: 用 files.read 而不是 Android 7 没有的 java.nio.file.
    return f.isFile() ? String(files.read(f.getPath())) : '';
}

function waitForDisk(names) {
    for (var i = 0; i < 50; i++) {
        var text = prefsText();
        if (names.every(function (n) { return text.indexOf('name="' + n + '"') >= 0; })) return true;
        sleep(100);
    }
    return false;
}

if (AJ6_PHASE === 'before') {
    var written = [PROBE];
    KEYS.forEach(function (k) {
        var name = String(context.getString(k.res));
        var seeded = false;
        if (raw(name) === null) {
            Pref.putLinkedHashSet(k.res, linkedSet(SAMPLE));
            seeded = true;
            written.push(name);
        }
        var state = { name: name, seeded: seeded, raw: raw(name), decoded: items(Pref.getLinkedHashSet(k.res)) };
        console.log(MARK + ' state ' + k.id + ' ' + encodeURIComponent(JSON.stringify(state)));
    });
    Pref.putLinkedList(PROBE, linkedList(SAMPLE));
    var probe = { name: PROBE, seeded: true, raw: raw(PROBE), decoded: items(Pref.getLinkedList(PROBE)) };
    console.log(MARK + ' state probe_list ' + encodeURIComponent(JSON.stringify(probe)));
    report('written-to-disk', waitForDisk(written), written.length + ' keys');
} else {
    var Json = org.autojs.autojs.core.pref.SettingsJson;
    KEYS.concat([{ id: 'probe_list', res: 0, read: null }]).forEach(function (k) {
        var s = AJ6_STATE[k.id];
        if (!s) {
            report(k.id, false, 'no state from phase before');
            return;
        }
        report(k.id + '-raw-unchanged', raw(s.name) === s.raw, s.raw === null ? 'absent' : s.decoded.length + ' items');
        var decoded = k.res ? items(Pref.getLinkedHashSet(k.res)) : items(Pref.getLinkedList(s.name));
        report(k.id + '-decoded-same', JSON.stringify(decoded) === JSON.stringify(s.decoded), decoded.length + ' items');
        if (k.read) {
            report(k.id + '-consumer-same', JSON.stringify(items(k.read())) === JSON.stringify(s.decoded));
        }
        report(k.id + '-encoded-same', String(Json.encodeStringList(linkedList(decoded))) === s.raw);
        // Writing the same items again through the new Pref leaves the stored text as Gson wrote it.
        if (k.res) Pref.putLinkedHashSet(k.res, linkedSet(decoded));
        else Pref.putLinkedList(s.name, linkedList(decoded));
        report(k.id + '-rewrite-same', raw(s.name) === s.raw);
        if (s.seeded) Pref.remove(s.name);
    });
    Pref.putLinkedList(PROBE_NEW, linkedList(SAMPLE));
    report('new-write-equals-gson-text', raw(PROBE_NEW) === AJ6_STATE.probe_list.raw);
    Pref.remove(PROBE_NEW);
    sleep(300);
    report('cleanup', [PROBE, PROBE_NEW].every(function (n) { return !Pref.containsKey(n); }) && KEYS.every(function (k) { var s = AJ6_STATE[k.id]; return !s.seeded || !Pref.containsKey(s.name); }));
}
console.log(MARK + ' info sdk=' + android.os.Build.VERSION.SDK_INT + ' model=' + android.os.Build.MODEL);
console.log(MARK + ' done');
