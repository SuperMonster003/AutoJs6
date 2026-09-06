/**
 * Flow basics: the asynchronous, chainable waits (roadmap P3, Appendix A.6).
 *
 * Every example of the roadmap appendix in a runnable form. The system Settings app is the
 * target, with generic selectors (a text view, a clickable row, a scrollable list) so the script
 * runs on any device and language; replace them with text('...') / /regex/ selectors of your
 * own app. Each example is written the asynchronous way (then / catch) and then finished with
 * sync() so the examples run one after another; in a real script you would keep the chain
 * asynchronous, or use sync() only inside a thread. A click on a row opens a page (or the
 * search), so the examples re-launch the Settings main page afterwards instead of guessing how
 * many back() presses it takes.
 *
 * zh-CN: Flow 基础: 异步链式等待 (Roadmap P3, 附录 A.6).
 * 附录中的每个示例都以可运行的形式给出. 目标是系统设置应用, 选择器用通用形式 (文本视图, 可点击的行, 可滚动列表), 因此任何设备与
 * 语言都能运行; 换成你自己应用的 text('...') / /正则/ 选择器即可. 每个示例先以异步写法 (then / catch) 给出, 再以 sync() 收尾使
 * 示例依次运行; 实际脚本里可以保持链是异步的, 或只在子线程里用 sync(). 点击一行会打开页面 (或搜索), 因此示例之后重新启动设置主页,
 * 而不是猜测需要按几次 back().
 */

"use strict";

auto.waitFor();

/* The selectors are limited to the Settings package, so the floating console of AutoJs6 never matches. zh-CN: 选择器限定在设置的包名内, 以免匹配到 AutoJs6 的悬浮控制台. */
const settingsIntent = new android.content.Intent(android.provider.Settings.ACTION_SETTINGS);
const settingsPackage = context.getPackageManager().resolveActivity(settingsIntent, 0).activityInfo.packageName;
const anyText = className("android.widget.TextView").packageName(settingsPackage);
const row = clickable().packageName(settingsPackage);
const list = scrollable().packageName(settingsPackage);

/*
 * Re-launches the Settings main page (also from a sub-page or the search). The old window may
 * still be reported while the new one starts, so the helper returns a short sleep: a then()
 * callback returning a Flow (or any thenable) makes the chain wait for it.
 * zh-CN: 重新启动设置主页 (从子页面或搜索也一样). 新窗口启动期间旧窗口可能仍被上报, 因此助手返回一个短休眠: then() 回调返回 Flow
 * (或任何 thenable) 时, 链会等待它.
 */
function reopenSettings() {
    app.startActivity({ action: "android.settings.SETTINGS", flags: ["activity_new_task", "activity_clear_task"] });
    return flow.sleep(1200);
}

/* ... and waits for its list before the next example. zh-CN: ... 并在下一个示例前等待其列表. */
function openSettings() {
    reopenSettings().wait(list, 5000).sync();
}

openSettings();

/* 1. Wait, then act in a callback. zh-CN: 等待, 在回调中动作. */
waitAsync(anyText, 3000, 200)
    .then(o => console.log("1. Found:", o.text()), e => console.error("1. Failed to find element:", e))
    .catch(error => console.error("1. An error occurred:", error))
    .sync();

/* 2. Wait and click in one step; the callbacks are those of then(). zh-CN: 等待并点击一步完成; 回调即 then() 的回调. */
openSettings();
waitThenClick(row, 3000, 200)
    .then(() => console.log("2. Element clicked successfully."), e => console.error("2. Failed to click element:", e))
    .sync();

/* 3. A sequence: every wait / click is one step, one catch at the end. zh-CN: 序列: 每个等待 / 点击是一步, 末尾一个 catch. */
openSettings();
waitAsync(row, 3000, 200)
    .click()
    .sleep(800).then(reopenSettings)
    .wait(row, 3000, 200)
    .click(o => console.log("3. Clicked another element:", o.className()), e => console.error("3. Failed to click another element:", e))
    .sleep(800).then(reopenSettings)
    .waitThenClick(row, 3000, 200)
    .then(() => console.log("3. Clicked yet another element successfully."))
    .catch(error => console.error("3. An error occurred during the sequence:", error))
    .sync();

/* 4. Stability: act only after the target stopped changing (default 500 ms). zh-CN: 稳定检测: 目标停止变化 (默认 500 ms) 后才动作. */
openSettings();
waitForStable(row, 3000, 200)
    .click()
    .sleep(800).then(reopenSettings)
    .waitForStableThenClick(row, 3000, 200, o => console.log("4. Clicked:", o.className()), e => console.error("4. Failed:", e))
    .sleep(800).then(reopenSettings)
    .waitThenClick(row, 3000, () => console.log("4. Clicked yet another element."), e => console.error("4. Failed:", e))
    .sleep(800).then(reopenSettings)
    .clickWhenStable(row, 3000, 200)
    .then(() => console.log("4. Sequence done."))
    .catch(error => console.error("4. An error occurred during the sequence:", error))
    .sync();

/* 5. Sleep inside a chain, and the shorter forms. zh-CN: 链中的休眠, 以及更短的写法. */
openSettings();
waitForStable(row, 3000, 200)
    .sleep(300)
    .click()
    .then(() => console.log("5. sleep(300) then click"))
    .catch(error => console.error("5.", error))
    .sync();
openSettings();
waitForStable(row, 3000, 200).clickAfter(300, 600).then(() => console.log("5. clickAfter(300, 600)")).sync(); /* sleep 300..600 ms, then click. */
openSettings();
clickWhenStableAfter(row, 3000, 200, 300).then(() => console.log("5. clickWhenStableAfter(row, 3000, 200, 300)")).sync(); /* stable, wait 300 ms, click. */

/* 6. The synchronous bridge: block a non-UI thread until the whole chain settled. zh-CN: 同步桥接: 在非 UI 线程阻塞到整条链完成. */
openSettings();
const el = waitAsync(row, 5000).click().sleep(800).then(reopenSettings).wait(anyText, 8000).sync();
console.log("6. sync() returned the last value:", el.className());

/* 7. Mixing with legacy code: run() executes synchronous lookups on a worker thread. zh-CN: 与旧式代码混用: run() 在工作线程执行同步查找. */
openSettings();
flow.run(() => anyText.findOne(3000))
    .filter(o => o != null)
    .map(o => o.text())
    .then(t => console.log("7. Legacy findOne on a worker thread found:", t), e => console.error("7. Legacy mix failed:", e))
    .sync();

/* 8. Combinators: race between conditions, all of several chains. zh-CN: 组合器: 条件之间的 race, 多条链的 all. */
const first = flow.race([text("__no_such_text__"), list, anyText]).sync();
console.log("8. race resolved with:", first.className());
const both = flow.all([waitAsync(list, 3000), flow.sleep(100).map(() => "slept")]).sync();
console.log("8. all resolved with:", both.length, "values:", both[0].className(), both[1]);

/* 9. Errors: a FlowError is an Error with code / step / selector / elapsed / attempts. zh-CN: 错误: FlowError 是带 code / step / selector / elapsed / attempts 的 Error. */
waitAsync(text("__no_such_text__"), 600, 100)
    .catch(e => console.log("9.", e.name, e.code, e.step, e.selector, e.elapsed + " ms", e.attempts + " attempts"))
    .sync();

/* 10. Promise interop: a Flow is a thenable, and toPromise() gives a real Promise. zh-CN: Promise 互操作: Flow 是 thenable, toPromise() 给出真正的 Promise. */
Promise.resolve(flow.of(21).map(x => x * 2)).then(v => console.log("10. Promise adopted a Flow:", v));
waitAsync(list, 3000).toPromise().then(o => console.log("10. toPromise():", o.className()));

/* 11. Aliases and namespaces: the same wait under four names. zh-CN: 别名与命名空间: 同一个等待的四个名字. */
console.log(
    "11. four names, one wait:",
    typeof waitAsync, typeof wait.async, typeof auto.wait, typeof flow.wait,
    "clickWait:", typeof clickWait, "waitForGone:", typeof waitForGone, "$flow:", $flow === flow,
);

setTimeout(() => {
    home();
    console.log("flow-basics done; pending flows:", flow.pending);
}, 1500);
