/**
 * Toolkit: switching between apps (roadmap P4.3).
 *
 * launchAndWait starts an app and waits until it is in front (or reports that it already was);
 * backToApp presses back until a package is in front again; backUntil presses back until a
 * condition holds (a selector, a node or a function), checking before the first press and polling
 * after every press; findAny / clickAny take the first of several candidates. The system Settings
 * app and the About page of AutoJs6 are the two apps here, so the sample runs on any device.
 *
 * zh-CN: 工具集: 应用切换 (Roadmap P4.3).
 * launchAndWait 启动应用并等到它在前台 (或报告它本来就在前台); backToApp 反复按返回直到某个包回到前台; backUntil 反复按返回直到
 * 条件成立 (选择器, 节点或函数), 第一次按下前先检查, 每次按下后轮询; findAny / clickAny 取多个候选中最先出现的. 这里以系统设置
 * 与 AutoJs6 的关于页面为两个应用, 因此示例可在任何设备上运行.
 */

"use strict";

auto.waitFor();

const settingsIntent = new android.content.Intent(android.provider.Settings.ACTION_SETTINGS);
const settingsPackage = context.getPackageManager().resolveActivity(settingsIntent, 0).activityInfo.packageName;
const selfPackage = context.getPackageName();
const settingsList = scrollable().packageName(settingsPackage);

/* Another activity in a task of its own on top of Settings: one back press uncovers Settings whatever else this app has open. zh-CN: 设置之上, 自成一个任务的另一个活动: 无论本应用还开着什么, 按一次返回即露出设置. */
function coverSettings() {
    app.startActivity({ packageName: selfPackage, className: "org.autojs.autojs.ui.settings.AboutActivity", flags: ["activity_new_task", "activity_multiple_task"] });
    waitAsync(() => currentPackage() === selfPackage, 6000).sync();
    sleep(1000);
}

/*
 * 1. Launch and wait. Some systems refuse app switches for a few seconds right after the
 * launcher was used; the first attempt then times out with a FlowError, which retry covers.
 * zh-CN: 1. 启动并等待. 某些系统在刚用过启动器后的几秒内拒绝应用切换; 首次尝试会以 FlowError 超时, retry 可以覆盖.
 */
const launched = retry(attempt => {
    if (attempt > 0) console.log("   attempt " + (attempt + 1));
    return launchAndWait(settingsPackage, { timeout: 4000 });
}, { times: 2, delay: 1000 });
console.log("1. " + launched.packageName + (launched.launched ? " launched" : " was in front") + " after " + launched.elapsed + " ms");

/* 2. Already in front: with bringToFront false nothing is launched. zh-CN: 已在前台: bringToFront 为 false 时不启动. */
const again = launchAndWait(settingsPackage, { timeout: 3000, bringToFront: false });
console.log("2. launched: " + again.launched + " (already in front)");

/* 3. Back to an app after another one covered it. zh-CN: 另一个应用盖住之后回到原应用. */
coverSettings();
console.log("3. In front now:", currentPackage());
console.log("3. backToApp ->", backToApp(settingsPackage, { maxTimes: 3, delay: 2500 }), "; in front:", currentPackage());

/* 4. Back until a condition holds; the value is what the condition found. zh-CN: 反复按返回直到条件成立; 返回值是条件找到的东西. */
coverSettings();
const found = backUntil(settingsList, { maxTimes: 3, delay: 2500 });
console.log("4. backUntil found a", found.className(), "of", found.packageName());

/* 5. Nothing to do when the condition already holds. zh-CN: 条件已成立时什么都不做. */
console.log("5. backUntil without a press ->", backUntil(() => currentPackage() === settingsPackage, { maxTimes: 1 }));

/* 6. The first of several candidates. zh-CN: 多个候选中最先出现的. */
const any = findAny(["__no_such_row__", settingsList, "__another__"], 3000);
console.log("6. findAny matched candidate #" + any.index + ":", any.node.className());

/* 7. Giving up: a FlowError with reason "maxTimes" (here without any press). zh-CN: 放弃: reason 为 "maxTimes" 的 FlowError (这里一次也不按). */
try {
    backUntil("__no_such_row__", { maxTimes: 0 });
} catch (e) {
    console.log("7. " + e.name + " " + e.code + " (" + e.reason + ") after " + e.attempts + " check(s):", e.message);
}

/* 8. The Flow forms. zh-CN: Flow 形式. */
coverSettings();
flow.backToApp(settingsPackage, { maxTimes: 3, delay: 2500 })
    .then(pkg => console.log("8. Flow back to", pkg))
    .then(() => flow.launchAndWait(settingsPackage, { bringToFront: false }))
    .then(r => console.log("8. Flow launchAndWait: launched=" + r.launched))
    .catch(e => console.error("8. Flow failed:", e))
    .sync();

console.log("Done.");
