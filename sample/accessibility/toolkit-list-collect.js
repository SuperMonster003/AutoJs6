/**
 * Toolkit: scrolling through a list and collecting its items (roadmap P4.3).
 *
 * collectList gathers the items of a scrollable list across scroll steps (deduplicated by
 * content, so an item that stays on screen between two steps is taken once), optionally until
 * a predicate holds; scrollUntil scrolls in a direction until a target shows up. The system
 * Settings main page is the list here, so the sample runs on any device; the row texts differ
 * per device and language, replace the targets with those of your app.
 *
 * zh-CN: 工具集: 列表滚动采集 (Roadmap P4.3).
 * collectList 在多次滚动之间采集可滚动列表的条目 (按内容去重, 两步之间仍留在屏幕上的条目只取一次), 可选地采集到某个断言成立为止;
 * scrollUntil 朝某个方向滚动直到目标出现. 这里以系统设置主页为列表, 因此示例可在任何设备上运行; 行文本因设备与语言而异, 换成
 * 你应用的目标即可.
 */

"use strict";

auto.waitFor();

/* Limited to the Settings package: the launcher has text views and scrollable views too. zh-CN: 限定在设置的包名内: 启动器也有文本视图与可滚动视图. */
const settingsIntent = new android.content.Intent(android.provider.Settings.ACTION_SETTINGS);
const settingsPackage = context.getPackageManager().resolveActivity(settingsIntent, 0).activityInfo.packageName;
const item = className("android.widget.TextView").packageName(settingsPackage);
const list = scrollable().packageName(settingsPackage);

/*
 * The Settings main page. Some systems refuse app switches for a few seconds right after the
 * launcher was used (the first attempt then times out), which retry covers.
 * zh-CN: 设置主页. 某些系统在刚用过启动器后的几秒内拒绝应用切换 (首次尝试会超时), retry 可以覆盖这种情况.
 */
retry(attempt => {
    app.startActivity({ action: "android.settings.SETTINGS", flags: ["activity_new_task", "activity_clear_task"] });
    waitAsync(list, 4000).sync();
    if (attempt > 0) console.log("Settings opened on attempt " + (attempt + 1));
}, { times: 2, delay: 1000 });
sleep(1000);

/* 1. Everything the list shows within a few scroll steps. zh-CN: 几次滚动内列表显示的全部内容. */
const items = collectList(null, item, { maxSteps: 4, settle: 400 });
console.log("1. Collected " + items.length + " texts, first:", items.slice(0, 3).join(" | "), "... last:", items[items.length - 1]);

/* 2. Back up, collecting until an item matching a pattern shows up (that item is included). zh-CN: 往回滚, 采集到某个匹配的条目出现为止 (含该条目). */
const anchor = /Bluetooth|蓝牙|藍牙|Display|显示|顯示|Network|网络|網絡|WLAN|Wi.?Fi/;
const upwards = collectList(null, item, { direction: "up", maxSteps: 6, settle: 400, until: text => anchor.test(String(text)) });
console.log("2. Collected " + upwards.length + " texts upwards, stopped at:", upwards[upwards.length - 1]);

/* 3. Scroll until a target far down shows up, then back to the top. zh-CN: 滚动到远处的目标出现, 再回到顶部. */
const farTarget = /Accessibility|无障碍|無障礙|About|关于|關於|More settings|更多设置|更多設置/;
const far = scrollUntil(farTarget, { maxSteps: 15, settle: 500 });
console.log("3. Scrolled down to:", far.text());
console.log("3. Scrolled up to:", scrollUntil(anchor, { direction: "up", maxSteps: 15, settle: 500 }).text());

/* 4. A target that never shows up: a FlowError (reason "end" when the list ended, "maxSteps" otherwise). zh-CN: 永远不出现的目标: FlowError (列表到底为 "end", 否则为 "maxSteps"). */
try {
    scrollUntil("__no_such_row__", { maxSteps: 2, settle: 300 });
} catch (e) {
    console.log("4. " + e.name + " " + e.code + " (" + e.reason + ") after " + e.attempts + " step(s):", e.message);
}

/* 5. The Flow forms: the value of the chain is the container, the collected array is the next value. zh-CN: Flow 形式: 链的值为容器, 采集到的数组为下一个值. */
waitAsync(list, 3000)
    .collectList(item, { maxSteps: 2, settle: 400 })
    .then(texts => console.log("5. Flow collected " + texts.length + " texts"))
    .then(() => flow.scrollUntil(anchor, { direction: "up", maxSteps: 10, settle: 400 }))
    .then(node => console.log("5. Flow scrolled up to:", node.text()))
    .catch(e => console.error("5. Flow failed:", e))
    .sync();

console.log("Done.");
