// Device check for docs/settings/ROADMAP.md appendix D item 8 (P14.3, E3), run on the old build by
// tools/settings/device_upgrade_check.py, which prepends "var AJ6_PLAN = [...];": writes each planned value (store,
// key, type, value) with plain SharedPreferences, as changing these settings on the old build would, so that the
// stored files can be compared before and after the upgrade. Uses no class of the settings layer, which the old build
// does not have.
// zh-CN: ROADMAP 附录 D 第 8 项的真机检查 (P14.3, E3), 由 device_upgrade_check.py 在旧版本上运行: 用普通 SharedPreferences 写入
// AJ6_PLAN 中的每个值 (存储, 键, 类型, 值), 相当于在旧版本上改了这些设置, 以便比较升级前后的存储文件. 不使用旧版本没有的设置层类.

var MARK = 'AJ6SET';
var RESULT_FILE = files.join(files.cwd(), 'aj6set-result.txt');
var resultLines = [];
var res = context.getResources();
var pkg = context.getPackageName();

function emit(line) {
    console.log(MARK + ' ' + line);
    resultLines.push(line);
}

function prefsOf(store) {
    return store === 'default'
        ? android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        : context.getSharedPreferences(store, android.content.Context.MODE_PRIVATE);
}

function nameOf(entry) {
    if (!entry.res) return entry.name;
    var id = res.getIdentifier(entry.res, 'string', pkg);
    if (id === 0) throw new Error('no resource ' + entry.res);
    return String(context.getString(id));
}

function valueOf(entry) {
    if (!entry.valueRes) return entry.value;
    var id = res.getIdentifier(entry.valueRes, 'string', pkg);
    if (id === 0) throw new Error('no resource ' + entry.valueRes);
    return String(context.getString(id));
}

var written = 0;
var editors = {};
AJ6_PLAN.forEach(function (entry) {
    try {
        var name = nameOf(entry);
        var value = valueOf(entry);
        var editor = editors[entry.store] || (editors[entry.store] = prefsOf(entry.store).edit());
        if (entry.type === 'bool') editor.putBoolean(name, value === true);
        else if (entry.type === 'int') editor.putInt(name, value);
        else if (entry.type === 'long') editor.putLong(name, java.lang.Long.parseLong(String(value)));
        else if (entry.type === 'float') editor.putFloat(name, value);
        else if (entry.type === 'stringSet') {
            var set = new java.util.HashSet();
            value.forEach(function (s) { set.add(s); });
            editor.putStringSet(name, set);
        } else editor.putString(name, String(value));
        written++;
    } catch (e) {
        emit('FAIL seed-' + (entry.res || entry.name) + ' ' + e);
    }
});
var committed = Object.keys(editors).every(function (store) {
    return editors[store].commit();
});
emit((committed && written === AJ6_PLAN.length ? 'PASS' : 'FAIL') + ' seed ' + written + ' of ' + AJ6_PLAN.length + ' values in '
    + Object.keys(editors).length + ' stores' + (committed ? '' : ', commit failed'));
resultLines.push('done');
files.write(RESULT_FILE, resultLines.join(String.fromCharCode(10)));
console.log(MARK + ' done');
