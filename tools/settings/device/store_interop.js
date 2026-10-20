// Device check for docs/settings/ROADMAP.md P1.2 (E3): SharedPreferencesStore and the old Pref / SharedPreferences
// calls read and write the same file. Run by tools/settings/device_store_check.py; prints "AJ6SET <PASS|FAIL> <id>
// <detail>" lines and "AJ6SET done". Only probe keys (key_$_settings_probe_*) and the probe store
// aj6_settings_probe are written, and they are removed at the end.
// zh-CN: ROADMAP P1.2 的真机检查 (E3): SharedPreferencesStore 与旧 Pref / SharedPreferences 调用读写同一文件. 只写探针键与
// 探针存储, 结束时删除.

var Pref = org.autojs.autojs.core.pref.Pref.INSTANCE;
var SettingsStores = org.autojs.autojs.core.pref.store.SettingsStores;
var StoreId = org.autojs.autojs.core.pref.store.StoreId;
var MARK = 'AJ6SET';
var P = 'key_$_settings_probe_';
var NAMED = 'aj6_settings_probe';

function report(id, ok, detail) {
    console.log(MARK + ' ' + (ok ? 'PASS' : 'FAIL') + ' ' + id + (detail === undefined ? '' : ' ' + detail));
}

function check(id, fn) {
    try {
        var r = fn();
        if (r === true) report(id, true);
        else report(id, false, r === false ? '' : String(r));
    } catch (e) {
        report(id, false, 'threw ' + (e && e.javaException ? e.javaException.getClass().getName() : e));
    }
}

function setOf(items) {
    var set = new java.util.HashSet();
    items.forEach(function (it) { set.add(it); });
    return set;
}

function prefsFile(name) {
    return new java.io.File(context.getApplicationInfo().dataDir, 'shared_prefs/' + name + '.xml');
}

function fileText(file) {
    return file.isFile() ? String(new java.lang.String(java.nio.file.Files.readAllBytes(file.toPath()), 'UTF-8')) : '';
}

var store = SettingsStores.getDefault();
var sp = Pref.get();

check('default-is-same-preferences', function () {
    return store.getPreferences() === sp || 'store preferences differ from Pref.get()';
});

check('store-put-string-read-by-pref', function () {
    store.put(P + 'str', 'v1', true);
    return Pref.getString(P + 'str', '') == 'v1' || Pref.getString(P + 'str', '');
});

check('store-commit-reaches-file', function () {
    var text = fileText(prefsFile(context.getPackageName() + '_preferences'));
    return text.indexOf('name="' + P + 'str">v1<') >= 0 || 'probe not in ' + context.getPackageName() + '_preferences.xml';
});

check('pref-int-read-by-store', function () {
    Pref.putIntSync(P + 'int', 42);
    var raw = store.get(P + 'int');
    return (store.getInt(P + 'int', 0) == 42 && raw.getClass().getName() == 'java.lang.Integer') || ('got ' + raw);
});

check('store-long-read-by-pref', function () {
    store.put(P + 'long', new java.lang.Long('3000000000'), true);
    return Pref.getLong(P + 'long', 0) == 3000000000 || Pref.getLong(P + 'long', 0);
});

check('pref-float-read-by-store', function () {
    Pref.putFloatSync(P + 'float', 1.25);
    return store.getFloat(P + 'float', 0) == 1.25 || store.getFloat(P + 'float', 0);
});

check('store-bool-read-by-pref', function () {
    store.put(P + 'bool', true, true);
    return Pref.getBoolean(P + 'bool', false) === true;
});

check('string-sets-both-ways', function () {
    sp.edit().putStringSet(P + 'set_old', setOf(['a', 'b'])).commit();
    var a = store.getStringSet(P + 'set_old', null);
    store.put(P + 'set_new', setOf(['x', 'y', 'z']), true);
    var b = sp.getStringSet(P + 'set_new', null);
    return (a.size() == 2 && a.contains('b') && b.size() == 3 && b.contains('z')) || ('sizes ' + a + ' ' + b);
});

check('changed-keys', function () {
    var same = store.put(P + 'str', 'v1', true);
    var changed = store.put(P + 'str', 'v2', true);
    return (same.size() == 0 && changed.size() == 1 && changed.contains(P + 'str')) || ('same=' + same + ' changed=' + changed);
});

check('type-mismatch-throws-like-pref', function () {
    var storeThrew = false, prefThrew = false;
    try { store.getLong(P + 'int', 0); } catch (e) { storeThrew = true; }
    try { Pref.getLong(P + 'int', 0); } catch (e) { prefThrew = true; }
    return (storeThrew && prefThrew) || ('store=' + storeThrew + ' pref=' + prefThrew);
});

check('removes-both-ways', function () {
    store.remove(P + 'bool', true);
    Pref.remove(P + 'float');
    sleep(100);
    return (!Pref.containsKey(P + 'bool') && !store.contains(P + 'float')) || 'a removed key is still there';
});

check('change-events', function () {
    var events = [];
    var registration = SettingsStores.addChangeListener(new SettingsStores.ChangeListener({
        onChanged: function (id, name) { events.push(String(id.getName()) + ':' + name); },
    }));
    try {
        Pref.putString(P + 'event', 'e1');
        for (var i = 0; i < 30 && events.indexOf('default:' + P + 'event') < 0; i++) sleep(100);
    } finally {
        registration.close();
    }
    Pref.putString(P + 'event', 'e2');
    sleep(300);
    var hits = events.filter(function (e) { return e == 'default:' + P + 'event'; }).length;
    return hits == 1 || ('events for the probe: ' + hits);
});

check('named-store-both-ways', function () {
    var named = SettingsStores.get(new StoreId(NAMED));
    var direct = context.getSharedPreferences(NAMED, 0);
    named.put('a', 'n1', true);
    direct.edit().putInt('b', 7).commit();
    var ok = direct.getString('a', null) == 'n1' && named.getInt('b', 0) == 7 && prefsFile(NAMED).isFile();
    return ok || ('a=' + direct.getString('a', null) + ' b=' + named.getInt('b', 0) + ' file=' + prefsFile(NAMED).isFile());
});

// Clean up the probe keys and the probe store.
var editor = sp.edit();
['str', 'int', 'long', 'float', 'bool', 'set_old', 'set_new', 'event'].forEach(function (k) { editor.remove(P + k); });
editor.commit();
SettingsStores.get(new StoreId(NAMED)).clear(true);
context.deleteSharedPreferences(NAMED);
check('cleanup', function () {
    return (!Pref.containsKey(P + 'str') && !prefsFile(NAMED).exists()) || 'probe data left';
});
console.log(MARK + ' info sdk=' + android.os.Build.VERSION.SDK_INT + ' model=' + android.os.Build.MODEL);
console.log(MARK + ' done');
