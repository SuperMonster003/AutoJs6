/**
 * Flow: timeouts, vanishing targets, stability and cancellation (roadmap P3.4).
 *
 * Shows every way a chain ends early: the timeout of one wait (with the FlowError fields), the
 * timeout of a whole chain (timeout(ms) covers the steps after it), sync(timeout), a target
 * that disappears (waitForHidden, and missing: 'fail' for a stability wait), cancel() of one
 * chain, flow.cancelAll(), and exit() while a chain is still pending (the runtime cancels it and
 * stops its worker threads). The system Settings app is the target. Note that timers of the
 * script thread do not fire while sync() blocks it (as with the synchronous wait / sleep), so
 * the examples use another chain, not setTimeout, to act while a chain is waited for.
 *
 * zh-CN: Flow: 超时, 目标消失, 稳定检测与取消 (Roadmap P3.4).
 * 展示链提前结束的每种方式: 单个等待的超时 (含 FlowError 字段), 整条链的超时 (timeout(ms) 约束其后的步骤), sync(timeout),
 * 目标消失 (waitForHidden, 以及稳定等待的 missing: 'fail'), 单条链的 cancel(), flow.cancelAll(), 以及链仍待定时 exit()
 * (运行时会取消它并停止工作线程). 目标是系统设置应用. 注意 sync() 阻塞脚本线程期间其定时器不会触发 (与同步的 wait / sleep 相同),
 * 因此示例用另一条链而非 setTimeout 在等待一条链的同时做动作.
 */

"use strict";

auto.waitFor();

/* Limited to the Settings package: the launcher has scrollable views too. zh-CN: 限定在设置的包名内: 启动器也有可滚动视图. */
const settingsIntent = new android.content.Intent(android.provider.Settings.ACTION_SETTINGS);
const settingsPackage = context.getPackageManager().resolveActivity(settingsIntent, 0).activityInfo.packageName;
const list = scrollable().packageName(settingsPackage);
const nothing = text("__there_is_no_such_text__");

/* Brings the Settings main page to the front and waits for its list. zh-CN: 把设置主页带到前台并等待其列表. */
function openSettings() {
    app.startActivity({ action: "android.settings.SETTINGS", flags: ["activity_new_task", "activity_clear_task"] });
    waitAsync(list, 5000).sync();
}

openSettings();

/* 1. One wait times out: the error carries what happened. zh-CN: 单个等待超时: 错误携带经过. */
waitAsync(nothing, 800, 100)
    .then(() => console.log("unexpected"), e => console.log("1. " + e.name + " " + e.code + ": step=" + e.step + " selector=" + e.selector + " elapsed=" + e.elapsed + " attempts=" + e.attempts))
    .sync();

/* 2. A chain timeout: the sleep is cut at the deadline and the next step rejects (reason "chain"). zh-CN: 链超时: 休眠在截止时截断, 下一步以 reason "chain" 拒绝. */
waitAsync(list, 3000)
    .timeout(500)
    .sleep(5000)
    .click()
    .catch(e => console.log("2. chain timeout:", e.code, e.reason))
    .sync();

/* 3. sync(timeout) gives up waiting while the chain goes on; cancel it explicitly. zh-CN: sync(timeout) 放弃等待而链继续; 显式取消它. */
const slow = flow.sleep(10000).map(() => "done");
try {
    slow.sync(300);
} catch (e) {
    console.log("3. sync(300):", e.code, e.reason, "state =", slow.state);
    console.log("3. cancel() ->", slow.cancel(), "state =", slow.state, "error =", slow.error.code);
}

/* 4. A target that disappears: waitForHidden resolves when it is gone for stableFor ms. zh-CN: 目标消失: 找不到持续 stableFor 毫秒后 waitForHidden 兑现. */
waitAsync(list, 3000)
    .then(() => home())
    .waitForHidden(list, { timeout: 5000, stableFor: 300 })
    .then(() => console.log("4. the list is gone"), e => console.log("4. failed:", e.code, e.reason))
    .sync();
openSettings();

/* 5. Stability: default (missing: 'reset') tolerates a disappearance, 'fail' rejects INVALID_TARGET. zh-CN: 稳定检测: 默认 (missing: 'reset') 容忍消失, 'fail' 以 INVALID_TARGET 拒绝. */
const stable = waitForStable(list, { timeout: 5000, stableFor: 300 }).map(o => o.className()).sync();
console.log("5. stable:", stable);
flow.sleep(400).then(() => home()); /* another chain, since timers do not fire during sync(). */
waitForStable(list, { timeout: 5000, stableFor: 3000, missing: "fail" })
    .then(() => console.log("5. unexpected"), e => console.log("5. missing: 'fail' ->", e.code, e.reason))
    .sync();
openSettings();

/* 6. cancel() one chain from another one; a cancelled chain rejects with CANCELLED. zh-CN: 从另一条链取消一条链; 被取消的链以 CANCELLED 拒绝. */
const pending = waitAsync(nothing, 60000);
flow.sleep(500).then(() => console.log("6. cancel() ->", pending.cancel()));
pending.catch(e => console.log("6. rejected with", e.code, "after", e.elapsed, "ms")).sync();

/* 7. flow.cancelAll() ends every pending chain of the script. zh-CN: flow.cancelAll() 结束脚本的全部待定链. */
const a = waitAsync(nothing, 60000);
const b = flow.sleep(60000);
console.log("7. pending before:", flow.pending, "cancelled:", flow.cancelAll(), "after:", flow.pending, a.state, b.state);

/* 8. exit() with a chain still pending: the runtime cancels it and stops the worker threads (its catch does not run any more, the script is gone; flow.trace(true) shows the cancellation). zh-CN: 链仍待定时 exit(): 运行时取消它并停止工作线程 (其 catch 不再运行, 脚本已结束; flow.trace(true) 可看到取消). */
const forever = waitAsync(nothing, 600000).catch(e => console.log("8. cancelled by exit:", e.code));
console.log("8. exiting with", flow.pending, "pending flow(s), state =", forever.state);
home();
exit();
