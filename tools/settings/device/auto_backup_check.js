// Device side of the automatic backup check (docs/settings/ROADMAP.md P9.1 - P9.3, T20, E3).
// Run by tools/settings/device_auto_backup_check.py, which sets AJ6_PHASE and the other AJ6_* variables:
//   prepare       keeps the switches, the period, the working directory, the explorer setting the change check toggles
//                 and the names of the automatic backups there are now in the private directory of the app; checks the
//                 defaults of the switches (T20, Q32) and that no periodic work is scheduled while its switch is off;
//                 then turns the periodic and the change backup off, so that the phases start from the same state;
//   operation     the operation backup (P9.1) through AndroidSettingsAutoBackup.afterOperation, the call of the import,
//                 undo and reset wizards: written while its switch is on, Outcome.Off and no file while it is off;
//   periodic      the periodic backup (P9.2): the switch schedules the unique periodic work of 7 days, whose first run
//                 writes a backup; the period changes it to 1 and 30 days in place; eight more backups keep 7; the
//                 switch off cancels the work;
//   notify        a periodic backup into a working directory that cannot be written (its parent is a file): the work
//                 and a direct call fail (the script checks the notification 0xBF5 in dumpsys);
//   change-start  the change backup (P9.3): the switch on, then three changes of an explorer setting in a few seconds;
//   change-verify after the quiet time, which the script on the computer waits for: exactly one change backup since
//                 change-start; then the switch off, a change schedules nothing;
//   restore       puts every kept value back (the change switch first), deletes the automatic backups written since
//                 prepare, the notification, the blocked directory and the private files.
// Lines carry names, counts and states only; nothing of the backups' content is printed (R6). Numbers and strings from
// Java are converted before they are compared, since "Java primitive wrap" may be on.
// zh-CN: 自动备份真机检查的设备端 (ROADMAP P9.1 - P9.3, T20, E3). 由 device_auto_backup_check.py 设置 AJ6_PHASE 等变量: prepare 保存开关,
// 周期, 工作目录, 变更检查所改的文件管理器设置以及现有自动备份的文件名, 核对开关默认值与周期开关关闭时无周期任务, 然后关闭周期与变更后备份;
// operation 经导入 / 撤销 / 恢复默认所调用的 afterOperation 核对操作后备份及其开关; periodic 核对周期任务的排入, 首次执行, 周期变更, 份数
// 上限与取消; notify 在不可写的工作目录下核对失败 (通知由脚本查 dumpsys); change-start / change-verify 核对变更后备份只产生一份以及关闭
// 开关后不再安排 (静默时间由电脑端脚本等待); restore 还原全部改动. 只输出名称, 计数与状态, 不输出备份内容 (R6).

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
var stores = pref.store.SettingsStores.INSTANCE;
var R = Packages.org.autojs.autojs6.R;
var File = java.io.File;
var WorkManager = Packages.androidx.work.WorkManager;
var TimeUnit = java.util.concurrent.TimeUnit;

var auto = archive.AndroidSettingsAutoBackup;
var Mechanism = archive.SettingsAutoBackup.Mechanism;
var General = keys.GeneralKeys;
var Explorer = keys.ExplorerKeys;
var PERIODIC_WORK = String(archive.SettingsPeriodicBackupWorker.WORK_NAME);
var CHANGE_WORK = String(archive.AndroidSettingsChangeBackup.WORK_NAME);
var KEEP = Number(archive.SettingsAutoBackup.KEEP);
var NOTIFICATION_ID = 0xBF5;
var DAY = 24 * 3600 * 1000;

var PRIVATE = new File(context.getFilesDir(), 'aj6-auto-backup-check');
var STATE = new File(PRIVATE, 'state.json');
var BLOCKED = new File(AJ6_OUT, 'blocked');

// The keys this check changes, put back by restore in this order; the change switch comes first, so that restoring
// the others takes no change backup.
var KEPT = [
    General.SETTINGS_AUTO_BACKUP_CHANGE, General.SETTINGS_AUTO_BACKUP_PERIODIC, General.SETTINGS_AUTO_BACKUP_PERIOD,
    General.SETTINGS_AUTO_BACKUP_OPERATION, Explorer.WORKING_DIRECTORY, Explorer.HIDDEN_FILES,
];
var SWITCHES = [General.SETTINGS_AUTO_BACKUP_CHANGE, General.SETTINGS_AUTO_BACKUP_PERIODIC, General.SETTINGS_AUTO_BACKUP_OPERATION].map(nameOf);

function nameOf(key) {
    return String(key.getName());
}

function str(id) {
    return String(context.getString(id));
}

// A java.lang.Boolean may come wrapped, and an object is always true in JavaScript.
function bool(value) {
    return String(value) === 'true';
}

function storeOf(key) {
    return stores.get(key.getStore());
}

function put(key, value) {
    storeOf(key).put(key.getName(), value, true);
}

function unset(key) {
    storeOf(key).remove(key.getName(), true);
}

function setSwitch(key, on) {
    put(key, java.lang.Boolean.valueOf(on));
}

function setPeriod(id) {
    put(General.SETTINGS_AUTO_BACKUP_PERIOD, str(id));
}

function readState() {
    return JSON.parse(String(files.read(STATE.getPath())));
}

function writeState(state) {
    PRIVATE.mkdirs();
    files.write(STATE.getPath(), JSON.stringify(state));
}

function directory() {
    return auto.directory();
}

/** Automatic backups of [mechanism] (a label, or every one when null), newest first. */
function backups(mechanism) {
    var prefix = 'autojs6-settings-auto-' + (mechanism ? mechanism + '-' : '');
    var children = directory().listFiles();
    var found = [];
    if (children) {
        for (var i = 0; i < children.length; i++) {
            var name = String(children[i].getName());
            if (bool(children[i].isFile()) && name.indexOf(prefix) === 0 && /\.zip$/.test(name)) found.push(children[i]);
        }
    }
    found.sort(function (a, b) {
        return Number(b.lastModified()) - Number(a.lastModified()) || (String(b.getName()) < String(a.getName()) ? -1 : 1);
    });
    return found;
}

function names(list) {
    return list.map(function (f) {
        return String(f.getName());
    });
}

function newSince(mechanism, before) {
    return backups(mechanism).filter(function (f) {
        return before.indexOf(String(f.getName())) < 0;
    });
}

function outcomeName(outcome) {
    return String(outcome.getClass().getSimpleName());
}

function works(name) {
    var infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS);
    var result = [];
    for (var i = 0; i < infos.size(); i++) result.push(infos.get(i));
    return result;
}

function pending(name) {
    return works(name).filter(function (info) {
        return !bool(info.getState().isFinished());
    });
}

function interval(info) {
    var periodicity = info.getPeriodicityInfo();
    return periodicity ? Number(periodicity.getRepeatIntervalMillis()) : null;
}

function describe(info) {
    var every = interval(info);
    return String(info.getState()) + (every ? ' every ' + every / DAY + ' d' : '') +
        ', next in ' + Math.round((Number(info.getNextScheduleTimeMillis()) - Date.now()) / 1000) + ' s';
}

function describeAll(name) {
    return works(name).map(describe).join('; ') || 'none';
}

function waitFor(seconds, test) {
    var deadline = Date.now() + seconds * 1000;
    while (Date.now() < deadline) {
        var value = test();
        if (value) return value;
        sleep(1000);
    }
    return test();
}

function cancelNotification() {
    Packages.androidx.core.app.NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
}

function deleteDirectory(dir) {
    var children = dir.listFiles();
    if (children) {
        for (var i = 0; i < children.length; i++) {
            if (bool(children[i].isDirectory())) deleteDirectory(children[i]); else children[i].delete();
        }
    }
    dir.delete();
}

function prepare() {
    if (bool(STATE.isFile())) {
        check('prepare', false, 'a kept state exists already; run restore first');
        return;
    }
    var kept = {};
    KEPT.forEach(function (key) {
        var store = storeOf(key);
        kept[nameOf(key)] = bool(store.contains(key.getName())) ? String(store.get(key.getName())) : null;
    });
    var dir = directory();
    var state = {
        kept: kept,
        directory: String(dir.getPath()),
        directoryExisted: bool(dir.isDirectory()),
        before: names(backups(null)),
    };
    writeState(state);
    out('INFO directory ' + state.directory + (state.directoryExisted ? '' : ' (absent)') + ', automatic backups ' + state.before.length +
        ' (periodic ' + backups('periodic').length + ', change ' + backups('change').length + ', operation ' + backups('operation').length + ')');
    out('INFO stored ' + KEPT.slice(0, 4).map(function (key) {
        var value = kept[nameOf(key)];
        return nameOf(key).replace(/^.*auto_backup_/, '') + '=' + (value === null ? 'absent' : value.replace(/^.*_period_/, ''));
    }).join(' '));
    var defaults = [General.SETTINGS_AUTO_BACKUP_PERIODIC, General.SETTINGS_AUTO_BACKUP_CHANGE, General.SETTINGS_AUTO_BACKUP_OPERATION].map(function (key) {
        return bool(key.defaultOrNull());
    });
    var period = String(General.SETTINGS_AUTO_BACKUP_PERIOD.defaultOrNull());
    check('switch defaults', String(defaults) === 'false,false,true' && period === str(R.string.key_settings_auto_backup_period_weekly),
        'periodic / change / operation ' + defaults.join(' / ') + ', period ' + period.replace(/^.*_period_/, ''));
    if (!bool(General.SETTINGS_AUTO_BACKUP_PERIODIC.get())) {
        check('no periodic work while off', pending(PERIODIC_WORK).length === 0, describeAll(PERIODIC_WORK));
    }
    setSwitch(General.SETTINGS_AUTO_BACKUP_CHANGE, false);
    setSwitch(General.SETTINGS_AUTO_BACKUP_PERIODIC, false);
}

function operation() {
    setSwitch(General.SETTINGS_AUTO_BACKUP_OPERATION, true);
    var before = names(backups('operation'));
    var written = auto.afterOperation(context);
    var made = newSince('operation', before);
    check('operation backup while on', outcomeName(written) === 'Written' && made.length === 1, outcomeName(written) + ', ' + made.length + ' new');
    if (made.length) out('INFO newest ' + made[0].getPath());

    setSwitch(General.SETTINGS_AUTO_BACKUP_OPERATION, false);
    before = names(backups('operation'));
    var off = auto.afterOperation(context);
    var none = newSince('operation', before).length;
    check('operation backup while off', outcomeName(off) === 'Off' && none === 0, outcomeName(off) + ', ' + none + ' new');
    setSwitch(General.SETTINGS_AUTO_BACKUP_OPERATION, true);
}

function periodic() {
    var before = names(backups('periodic'));
    setPeriod(R.string.key_settings_auto_backup_period_weekly);
    setSwitch(General.SETTINGS_AUTO_BACKUP_PERIODIC, true);
    var scheduled = waitFor(10, function () {
        var list = pending(PERIODIC_WORK);
        return list.length ? list : null;
    }) || [];
    check('switch on schedules one weekly work', scheduled.length === 1 && interval(scheduled[0]) === 7 * DAY, describeAll(PERIODIC_WORK));
    var id = scheduled.length ? String(scheduled[0].getId()) : null;

    var started = Date.now();
    var first = waitFor(AJ6_FIRST_RUN_WAIT, function () {
        var made = newSince('periodic', before);
        return made.length ? made : null;
    }) || [];
    check('first run of the work writes a backup', first.length === 1, first.length + ' new after ' + Math.round((Date.now() - started) / 1000) + ' s');
    if (first.length) {
        // A copy for the script to pull, since the eight backups below prune this one.
        var copy = new File(AJ6_OUT, String(first[0].getName()));
        copy.getParentFile().mkdirs();
        files.copy(first[0].getPath(), copy.getPath());
        out('INFO newest ' + copy.getPath());
    }

    [[R.string.key_settings_auto_backup_period_daily, 1], [R.string.key_settings_auto_backup_period_monthly, 30], [R.string.key_settings_auto_backup_period_weekly, 7]].forEach(function (period) {
        setPeriod(period[0]);
        var list = waitFor(10, function () {
            var now = pending(PERIODIC_WORK);
            return now.length === 1 && interval(now[0]) === period[1] * DAY ? now : null;
        }) || pending(PERIODIC_WORK);
        var same = list.length === 1 && String(list[0].getId()) === id && interval(list[0]) === period[1] * DAY;
        check('period ' + period[1] + ' d updates the work in place', same, describeAll(PERIODIC_WORK) + (same ? ', same id' : ''));
    });
    var written = newSince('periodic', before).length;
    check('changing the period writes no backup', written === first.length, written + ' new');

    // Eight more backups: the newest 7 of the mechanism are kept (T20).
    var outcomes = [];
    for (var i = 0; i < 8; i++) outcomes.push(outcomeName(auto.write(context, Mechanism.PERIODIC)));
    var kept = backups('periodic');
    check('seven periodic backups kept', kept.length === KEEP && outcomes.every(function (o) {
        return o === 'Written';
    }), kept.length + ' kept after 8 more (' + outcomes.join(',') + ')');

    setSwitch(General.SETTINGS_AUTO_BACKUP_PERIODIC, false);
    var cancelled = waitFor(10, function () {
        return pending(PERIODIC_WORK).length === 0;
    });
    check('switch off cancels the work', cancelled, describeAll(PERIODIC_WORK));
}

function notify() {
    var state = readState();
    cancelNotification();
    BLOCKED.getParentFile().mkdirs();
    files.write(BLOCKED.getPath(), 'x');
    Packages.org.autojs.autojs.util.WorkingDirectoryUtils.setAbsolutePath(new File(BLOCKED, 'Scripts').getPath());
    var target = String(directory().getPath());
    try {
        check('working directory moved under a file', target.indexOf(String(BLOCKED.getPath())) === 0, target);
        // The switch schedules the work, whose first run fails (the work is due again a period later); a direct call
        // fails as well.
        setSwitch(General.SETTINGS_AUTO_BACKUP_PERIODIC, true);
        var ran = waitFor(AJ6_NOTIFY_WAIT, function () {
            var list = pending(PERIODIC_WORK);
            return list.length === 1 && String(list[0].getState()) === 'ENQUEUED' && Number(list[0].getNextScheduleTimeMillis()) - Date.now() > DAY;
        });
        out('INFO periodic work ' + (ran ? 'ran: ' : 'did not run yet: ') + describeAll(PERIODIC_WORK));
        var direct = auto.write(context, Mechanism.PERIODIC);
        var name = outcomeName(direct);
        check('backup into the blocked directory fails', name === 'Failed', name + (name === 'Failed' ? ' (' + direct.getError().getClass().getSimpleName() + ')' : ''));
        setSwitch(General.SETTINGS_AUTO_BACKUP_PERIODIC, false);
        var exists = bool(new File(target).exists());
        check('nothing written while blocked', !exists, 'target ' + (exists ? 'exists' : 'absent'));
    } finally {
        var kept = state.kept[nameOf(Explorer.WORKING_DIRECTORY)];
        if (kept === null) unset(Explorer.WORKING_DIRECTORY); else put(Explorer.WORKING_DIRECTORY, kept);
        check('working directory restored', String(directory().getPath()) === state.directory, String(directory().getPath()));
    }
}

function changeStart() {
    var state = readState();
    setSwitch(General.SETTINGS_AUTO_BACKUP_CHANGE, false);
    state.changeBefore = names(backups('change'));
    state.changeStartedAt = Date.now();
    writeState(state);
    setSwitch(General.SETTINGS_AUTO_BACKUP_CHANGE, true);
    sleep(2000);
    check('switching it on schedules nothing', pending(CHANGE_WORK).length === 0, describeAll(CHANGE_WORK));
    var hidden = Explorer.HIDDEN_FILES;
    var original = String(hidden.storedKeyOrDefault());
    var show = str(R.string.key_hidden_files_show);
    var other = original === show ? str(R.string.key_hidden_files_not_show) : show;
    [other, original, other].forEach(function (value) {
        put(hidden, value);
        sleep(2000);
    });
    var list = pending(CHANGE_WORK);
    check('three changes schedule one change backup', list.length === 1, describeAll(CHANGE_WORK));
    out('INFO directory ' + directory().getPath());
}

// The quiet time is waited for by the script on the computer: a script of the app waiting in the background may be
// frozen by the system (ColorOS) until the app comes to the front again.
function changeVerify() {
    var state = readState();
    var made = newSince('change', state.changeBefore);
    var after = Math.round((Date.now() - state.changeStartedAt) / 1000);
    if (made.length) {
        var at = Math.round((Number(made[made.length - 1].lastModified()) - state.changeStartedAt) / 1000);
        check('one change backup for the three changes', made.length === 1, made.length + ' new ' + after + ' s after the first change, the first written after ' + at + ' s');
        out('INFO newest ' + made[0].getPath());
    } else {
        check('one change backup for the three changes', false, 'none after ' + after + ' s; work ' + describeAll(CHANGE_WORK));
    }
    setSwitch(General.SETTINGS_AUTO_BACKUP_CHANGE, false);
    var hidden = Explorer.HIDDEN_FILES;
    var kept = state.kept[nameOf(hidden)];
    if (kept === null) unset(hidden); else put(hidden, kept);
    sleep(2000);
    check('switch off: a change schedules nothing', pending(CHANGE_WORK).length === 0, describeAll(CHANGE_WORK));
}

function restore() {
    var state = readState();
    KEPT.forEach(function (key) {
        var kept = state.kept[nameOf(key)];
        if (kept === null) unset(key);
        else put(key, SWITCHES.indexOf(nameOf(key)) >= 0 ? java.lang.Boolean.valueOf(kept === 'true') : kept);
    });
    var removed = 0;
    backups(null).forEach(function (f) {
        if (state.before.indexOf(String(f.getName())) < 0 && bool(f.delete())) removed++;
    });
    var dir = directory();
    if (!state.directoryExisted && bool(dir.isDirectory()) && dir.list().length === 0) {
        dir.delete();
        var parent = dir.getParentFile();
        if (bool(parent.isDirectory()) && parent.list().length === 0) parent.delete();
    }
    cancelNotification();
    deleteDirectory(BLOCKED.getParentFile());
    deleteDirectory(PRIVATE);
    var same = KEPT.every(function (key) {
        var store = storeOf(key);
        var kept = state.kept[nameOf(key)];
        var stored = bool(store.contains(key.getName()));
        return kept === null ? !stored : stored && String(store.get(key.getName())) === kept;
    });
    var left = backups(null).length;
    check('restored', same && left === state.before.length, removed + ' backups deleted, ' + left + ' left as before, periodic work ' +
        (pending(PERIODIC_WORK).length ? 'pending' : 'none') + ', change work ' + (pending(CHANGE_WORK).length ? 'pending' : 'none'));
}

try {
    ({prepare: prepare, operation: operation, periodic: periodic, notify: notify, 'change-start': changeStart, 'change-verify': changeVerify, restore: restore})[AJ6_PHASE]();
} catch (e) {
    check(AJ6_PHASE, false, 'threw ' + e + (e.lineNumber ? ' (line ' + e.lineNumber + ')' : ''));
}
out('done');
files.write(RESULT_FILE, resultLines.join('\n'));
