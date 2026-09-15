/*
 * Checks shared by the three SDK 37 packaged-app examples (Roadmap P3.3 table rows 30-37, P3.4).
 * Every check records a boolean and a detail string into a JSON receipt in the app's external
 * files directory; tools/sdk37/run_packaged_examples.py reads the receipt back through adb.
 * The script never opens system settings or prompts: whatever it cannot observe unattended is
 * recorded as a value, and the runner decides.
 *
 * zh-CN: 三个 SDK 37 打包示例共用的检查 (Roadmap P3.3 表 30-37 行, P3.4). 每项检查把布尔结果与说明
 * 写入应用外部文件目录下的 JSON 回执, 由 tools/sdk37/run_packaged_examples.py 通过 adb 读回.
 * 脚本不会打开系统设置或弹出提示: 无人值守下观察不到的内容只记录数值, 由运行器判断.
 */
"use strict";

var api = android.os.Build.VERSION.SDK_INT;
var receiptFile = new java.io.File(context.getExternalFilesDir(null), "sdk37-example.json");
var receipt = {
    example: null,
    package: String(context.getPackageName()),
    api: api,
    target: context.getApplicationInfo().targetSdkVersion,
    checks: [],
    ready: false,
    done: false,
};

function save() {
    files.write(receiptFile.getAbsolutePath(), JSON.stringify(receipt));
}

function check(name, body) {
    var passed = false;
    var detail = "";
    try {
        var value = typeof body === "function" ? body() : body;
        passed = value === true;
        if (typeof value === "string") {
            // A string is a passing check with a note (for example "not applicable below API 33").
            passed = true;
            detail = value;
        } else if (!passed) {
            detail = "returned " + value;
        }
    } catch (e) {
        detail = "threw " + e;
    }
    receipt.checks.push({ name: name, passed: passed, detail: detail });
    save();
    console[passed ? "log" : "error"]("[" + (passed ? "PASS" : "FAIL") + "] " + name + (detail ? " (" + detail + ")" : ""));
    return passed;
}

function requestedPermissions() {
    var info = context.getPackageManager().getPackageInfo(context.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
    var names = [];
    var requested = info.requestedPermissions;
    if (requested) {
        for (var i = 0; i < requested.length; i++) names.push(String(requested[i]));
    }
    return names;
}

/* Rows 30 (package visibility), 31 (exact alarms), 33 (POST_NOTIFICATIONS), 34 (broadcast receivers, read-only DEX), 37 (local network). */
function commonChecks(next) {
    check("target-37", function () {
        return context.getApplicationInfo().targetSdkVersion === 37;
    });
    check("visibility-settings-app", function () {
        var name = app.getAppName("com.android.settings");
        var launchable = context.getPackageManager().getLaunchIntentForPackage("com.android.settings") !== null;
        return typeof name === "string" && name.length > 0 && launchable;
    });
    check("local-network-declared", function () {
        return requestedPermissions().indexOf("android.permission.ACCESS_LOCAL_NETWORK") >= 0;
    });
    check("post-notifications-runtime", function () {
        if (api < 33) return "not applicable below API 33";
        var granted = context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") === android.content.pm.PackageManager.PERMISSION_GRANTED;
        var enabled = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE).areNotificationsEnabled();
        receipt.postNotifications = { granted: granted, enabled: enabled };
        // Declared but not requested yet: a target 33+ app starts without the permission and without visible notifications.
        return !granted && !enabled;
    });
    check("exact-alarm-capability", function () {
        if (api < 31) return "not applicable below API 31";
        var alarms = context.getSystemService(android.content.Context.ALARM_SERVICE);
        receipt.canScheduleExactAlarms = alarms.canScheduleExactAlarms();
        return receipt.canScheduleExactAlarms === true;
    });
    check("load-dex", function () {
        runtime.loadDex(files.path("./sdk37-probe.dex"));
        return String(Packages.org.autojs.sdk37.fixture.DexProbe.value()) === "SDK37_DEX_OK";
    });
    // events.broadcast delivers through the script loop, so the round trip completes asynchronously.
    var delivered = null;
    events.broadcast.on("sdk37_ping", function (payload) {
        delivered = payload;
    });
    events.broadcast.emit("sdk37_ping", { example: receipt.example });
    setTimeout(function () {
        check("broadcast-roundtrip", function () {
            return delivered !== null && String(delivered.example) === String(receipt.example);
        });
        next();
    }, 1500);
}

function finish(extra) {
    if (extra) {
        Object.keys(extra).forEach(function (key) {
            receipt[key] = extra[key];
        });
    }
    receipt.done = true;
    save();
}

module.exports = { api: api, receipt: receipt, save: save, check: check, commonChecks: commonChecks, finish: finish };
