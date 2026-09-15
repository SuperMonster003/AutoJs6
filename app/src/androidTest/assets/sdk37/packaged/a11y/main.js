/*
 * SDK 37 packaged example 1: accessibility smoke inside a user APK (Roadmap P3.4).
 * The runner enables this app's own AccessibilityServiceUsher before launching, so the script
 * only waits for the service and never opens Settings. The log window stays visible so that
 * the app's own window is what the service observes.
 *
 * zh-CN: SDK 37 打包示例 1: 用户 APK 内的无障碍冒烟 (Roadmap P3.4). 运行器在启动前启用本应用自身的
 * AccessibilityServiceUsher, 脚本只等待服务, 不打开设置. 日志窗口保持可见, 使服务观察到的是本应用的窗口.
 */
"use strict";
var common = require("./common.js");
var receipt = common.receipt;
var check = common.check;
receipt.example = "a11y";
common.save();

function frontIs(packageName, timeoutMillis) {
    var deadline = Date.now() + timeoutMillis;
    while (Date.now() < deadline) {
        if (currentPackage() === packageName) return true;
        sleep(200);
    }
    return currentPackage() === packageName;
}

auto.waitFor(30000);
check("a11y-service-bound", function () {
    return auto.service !== null && auto.service !== undefined;
});
check("a11y-own-window-front", function () {
    return frontIs(receipt.package, 10000);
});
check("a11y-root-window", function () {
    var root = auto.service.getRootInActiveWindow();
    return root !== null && String(root.getPackageName()) === receipt.package;
});

var windowEvents = [];
auto.registerEvent("WINDOW_STATE_CHANGED", function (event) {
    windowEvents.push({ packageName: String(event.packageName), className: String(event.className) });
});
home();
sleep(1000);
app.launch(receipt.package);
var landed = frontIs(receipt.package, 10000);
sleep(800);
check("a11y-window-event-after-transition", function () {
    receipt.windowEvents = windowEvents.length;
    return landed && windowEvents.some(function (event) {
        return event.packageName === receipt.package;
    });
});
auto.removeEvent("WINDOW_STATE_CHANGED");

receipt.ready = true;
common.save();
common.commonChecks(function () {
    common.finish();
});
