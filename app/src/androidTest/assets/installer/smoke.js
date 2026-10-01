// Real engine / public Binder smoke. The native harness owns all input files and the no-code APK.
var report = { ok: false, steps: [], mode: INSTALLER_SMOKE.mode };
var ownerThread = String(java.lang.Thread.currentThread().getId());
var reported = false;

function expect(value, message) {
    if (!value) throw new Error('installer smoke: ' + message);
    report.steps.push(message);
}
function sameThread() {
    expect(String(java.lang.Thread.currentThread().getId()) === ownerThread, 'callback uses its creating script thread');
}
function checkError(error, code) {
    expect(error instanceof installer.InstallerError && error instanceof Error, 'failure is a real InstallerError: ' + error);
    expect(error.name === 'InstallerError' && error.code === code, 'error code ' + code + ': ' + error.code);
    return error;
}
function throwsCode(action, code) {
    var error;
    try { action(); } catch (e) { error = e; }
    expect(error !== undefined, 'operation throws ' + code);
    return checkError(error, code);
}
function rejectsCode(promise, code) {
    return promise.then(function () { throw new Error('expected rejection ' + code); }, function (error) {
        sameThread(); checkError(error, code);
    });
}
function finish(error) {
    if (reported) return;
    reported = true;
    report.ok = !error;
    if (error) report.error = String(error) + (error.stack ? '\n' + error.stack : '');
    runtime.putProperty('installer.smoke.report', JSON.stringify(report));
}
function checkedInstall(result, version) {
    expect(!Array.isArray(result) && result.ok === true, 'single install result succeeded');
    expect(result.packageName === INSTALLER_SMOKE.packageName, 'only the owned fixture package was installed');
    expect(result.versionCode === version, 'installed version ' + version);
    expect(result.authorizer === 'shizuku' && result.interaction === 'silent', 'actual Shizuku silent result');
}
function checkUsers(users) {
    expect(Array.isArray(users) && users.length > 0, 'users result is a non-empty array');
    var current = Math.floor(android.os.Process.myUid() / 100000);
    expect(users.some(function (user) { return user.id === current && typeof user.running === 'boolean'; }), 'users includes the host current user');
}

function inspectMode() {
    expect(installer === $installer, 'installer and $installer are the same global');
    expect(installer.isAvailable() && installer.status.available, 'installed plugin is available');
    expect(installer.status.contractVersion === 1, 'contract version is exposed');
    expect(typeof installer.isDefault() === 'boolean', 'default status is read without changing it');
    var available = installer.authorizer.available;
    expect(Array.isArray(available) && available.indexOf('none') >= 0, 'available authorizers include the non-privileged path');
    var aggregate = installer.authorizer.state('auto');
    expect(aggregate.name === 'auto' && typeof aggregate.available === 'boolean' && typeof aggregate.running === 'boolean' && typeof aggregate.granted === 'boolean', 'auto authorizer reports aggregate readiness');
    expect(installer.authorizer.request('none') === true, 'request none returns without a privilege prompt');
    try { checkUsers(installer.users()); } catch (error) { checkError(error, 'AUTHORIZER_REQUIRED'); }
    var file = new java.io.File(INSTALLER_SMOKE.v1);
    var info = installer.inspect(file);
    expect(info.format === 'apk' && info.installable === true, 'APK inspection is installable');
    expect(info.packageName === INSTALLER_SMOKE.packageName && info.versionCode === 1, 'File wrapper preserves APK identity');
    if (info.icon !== undefined) {
        expect(String(info.icon.getClass().getName()) === 'android.graphics.Bitmap' && info.icon.getWidth() > 0, 'icon is a decoded Bitmap');
        info.icon.recycle();
    }
    var aab = installer.inspect(INSTALLER_SMOKE.aab);
    expect(aab.format === 'aab' && aab.installable === false, 'AAB inspection does not advertise installation');
    expect(aab.packageName === INSTALLER_SMOKE.packageName && aab.versionCode === 7, 'AAB metadata was decoded');
    expect(Array.isArray(aab.modules) && aab.modules.length === 2, 'AAB modules were decoded');
    throwsCode(function () { installer.install([], {}); }, 'INVALID_ARGUMENT');
    throwsCode(function () { installer.install(INSTALLER_SMOKE.v1, { unknown: true }); }, 'INVALID_ARGUMENT');
    throwsCode(function () { installer.authorizer.request(undefined); }, 'INVALID_ARGUMENT');
    var cycle = []; cycle.push(cycle);
    throwsCode(function () { installer.install(cycle); }, 'INVALID_ARGUMENT');
    return installer.inspectAsync(android.net.Uri.fromFile(file)).then(function (other) {
        sameThread();
        expect(other.packageName === info.packageName && other.versionCode === 1, 'Uri wrapper survives Async snapshot');
        if (other.icon !== undefined) other.icon.recycle();
        return installer.authorizer.requestAsync('auto');
    }).then(function (granted) {
        sameThread();
        expect(typeof granted === 'boolean', 'requestAsync auto only reports aggregate readiness');
        return installer.usersAsync().then(function (users) {
            sameThread(); checkUsers(users);
        }, function (error) { sameThread(); checkError(error, 'AUTHORIZER_REQUIRED'); });
    }).then(function () {
        return rejectsCode(installer.installAsync(INSTALLER_SMOKE.v1, { timeout: -1 }), 'INVALID_ARGUMENT');
    });
}

function formsMode() {
    var state = installer.authorizer.state('shizuku');
    expect(state.available && state.running && state.granted, 'Shizuku was already granted; no authorization is requested');
    var options = { authorizer: 'shizuku', interaction: 'silent', timeout: 60000 };
    var failed = installer.install([INSTALLER_SMOKE.bad], options);
    expect(Array.isArray(failed) && failed.length === 1 && failed[0].ok === false, 'failed one-item batch keeps array shape');
    expect(failed[0].error instanceof installer.InstallerError, 'batch failure contains an InstallerError instance');
    checkedInstall(installer.install(new java.io.File(INSTALLER_SMOKE.v1), options), 1);
    report.installForms = 1;
    var sources = [android.net.Uri.fromFile(new java.io.File(INSTALLER_SMOKE.v2))];
    var promise = installer.installAsync(sources, options);
    sources[0] = '/this-mutation-must-not-change-the-snapshot.apk';
    return promise.then(function (results) {
        sameThread();
        expect(Array.isArray(results) && results.length === 1, 'Async one-item source array returns an array');
        checkedInstall(results[0], 2);
        expect(results[0].previousVersionCode === 1, 'Async update reports the previously installed version');
        report.installForms++;
        var session = installer.session({ splits: [new java.io.File(INSTALLER_SMOKE.v2)] }, {
            authorizer: 'shizuku', interaction: 'silent', timeout: 60000, deleteSource: true,
        });
        var id = session.id;
        var terminalCount = 0;
        var stages = 0;
        var progress = 0;
        session.on('stage', function () { sameThread(); stages++; });
        session.on('progress', function (value, detail) {
            sameThread(); progress++;
            expect(value >= 0 && value <= 1 && typeof detail.bytesWritten === 'number', 'progress has a bounded ratio and byte detail');
        });
        session.on('complete', function (result) { sameThread(); terminalCount++; checkedInstall(result, 2); });
        session.on('error', function (error) { sameThread(); terminalCount++; throw error; });
        session.on('cancel', function () { terminalCount++; throw new Error('unexpected session cancellation'); });
        var result = session.wait(60000);
        checkedInstall(result, 2);
        expect(result.sourceDeleted === true && !files.exists(INSTALLER_SMOKE.v2), 'successful owned source was deleted before returning');
        expect(files.exists(INSTALLER_SMOKE.v1), 'source without deleteSource stays present');
        expect(session.id === id && typeof id === 'string' && id.length > 0, 'session ID stays stable');
        expect(session.state === 'completed' && session.result === result, 'session terminal state and result agree');
        expect(stages > 0 && progress > 0 && terminalCount === 1, 'wait pumped stage/progress and exactly one completion');
        expect(session.cancel() === false, 'completed session cannot be cancelled again');
        report.installForms++;
        return installer.uninstallAsync(INSTALLER_SMOKE.packageName, { authorizer: 'shizuku', interaction: 'silent', timeout: 60000 });
    }).then(function (result) {
        sameThread();
        expect(result.ok === true && result.packageName === INSTALLER_SMOKE.packageName, 'uninstallAsync removed the owned fixture');
        report.uninstalled = true;
    });
}

function lifecycleMode(stopOnly) {
    var callbacks = 0;
    var terminalCount = 0;
    var prepared = false;
    function callback() { sameThread(); runtime.putProperty('installer.smoke.callbacks', ++callbacks); }
    var session = installer.session(android.net.Uri.parse(INSTALLER_SMOKE.pipe), { authorizer: 'none', timeout: 30000 });
    session.on('progress', callback);
    session.on('complete', function () { callback(); terminalCount++; runtime.putProperty('installer.smoke.terminal', 'complete'); finish(new Error('blocked source completed')); });
    session.on('error', function (error) { callback(); terminalCount++; runtime.putProperty('installer.smoke.terminal', error.code); finish(error); });
    session.on('cancel', function () { callback(); terminalCount++; runtime.putProperty('installer.smoke.terminal', 'cancel'); });
    session.on('stage', function (stage) {
        callback();
        if (stage !== 'preparing' || prepared) return;
        prepared = true;
        runtime.putProperty('installer.smoke.ready', true);
        if (stopOnly) return; // Native harness force-stops this actual engine while the plugin is blocked reading.
        try {
            throwsCode(function () { session.wait(30); }, 'TIMEOUT');
            expect(session.state !== 'cancelled' && session.state !== 'failed', 'short wait timeout left the operation alive');
            expect(session.cancel() === true, 'explicit cancellation was accepted');
            throwsCode(function () { session.wait(15000); }, 'CANCELLED');
            expect(session.state === 'cancelled' && session.cancel() === false, 'cancelled session has one terminal state');
            var count = callbacks;
            setTimeout(function () {
                try {
                    expect(callbacks === count && terminalCount === 1, 'no late events after the cancellation terminal');
                    report.terminalCount = terminalCount;
                    finish();
                } catch (error) { finish(error); }
            }, 400);
        } catch (error) { session.cancel(); finish(error); }
    });
    // Exercise force-stop during a synchronous Condition wait as it pumps the preparing event.
    if (stopOnly) session.wait(30000);
}

function unavailableMode() {
    expect(installer.isAvailable() === false && installer.status.available === false, 'unavailable plugin probes return false');
    throwsCode(function () { installer.install(INSTALLER_SMOKE.v1); }, 'PLUGIN_UNAVAILABLE');
    throwsCode(function () { installer.session(INSTALLER_SMOKE.v1); }, 'PLUGIN_UNAVAILABLE');
    throwsCode(function () { installer.inspect(INSTALLER_SMOKE.v1); }, 'PLUGIN_UNAVAILABLE');
    throwsCode(function () { installer.uninstall(INSTALLER_SMOKE.packageName); }, 'PLUGIN_UNAVAILABLE');
    throwsCode(function () { installer.authorizer.state('none'); }, 'PLUGIN_UNAVAILABLE');
    throwsCode(function () { var names = installer.authorizer.available; }, 'PLUGIN_UNAVAILABLE');
    throwsCode(function () { installer.users(); }, 'PLUGIN_UNAVAILABLE');
    throwsCode(function () { installer.isDefault(); }, 'PLUGIN_UNAVAILABLE');
    throwsCode(function () { installer.setDefault(true, { authorizer: 'none' }); }, 'PLUGIN_UNAVAILABLE');
    return Promise.all([
        rejectsCode(installer.installAsync(INSTALLER_SMOKE.v1), 'PLUGIN_UNAVAILABLE'),
        rejectsCode(installer.inspectAsync(INSTALLER_SMOKE.v1), 'PLUGIN_UNAVAILABLE'),
        rejectsCode(installer.uninstallAsync(INSTALLER_SMOKE.packageName), 'PLUGIN_UNAVAILABLE'),
        rejectsCode(installer.usersAsync(), 'PLUGIN_UNAVAILABLE'),
        rejectsCode(installer.authorizer.requestAsync('none'), 'PLUGIN_UNAVAILABLE'),
        rejectsCode(installer.setDefaultAsync(true, { authorizer: 'none' }), 'PLUGIN_UNAVAILABLE'),
    ]);
}

try {
    switch (INSTALLER_SMOKE.mode) {
        case 'inspect': inspectMode().then(function () { finish(); }, finish); break;
        case 'forms': formsMode().then(function () { finish(); }, finish); break;
        case 'cancel': lifecycleMode(false); break;
        case 'stop': lifecycleMode(true); break;
        case 'unavailable': unavailableMode().then(function () { finish(); }, finish); break;
        default: throw new Error('unknown smoke mode');
    }
} catch (error) { finish(error); }
