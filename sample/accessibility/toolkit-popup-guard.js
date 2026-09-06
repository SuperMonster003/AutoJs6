/**
 * Toolkit: dismissing popups, once and as a guard (roadmap P4.3).
 *
 * dismissPopups(targets) closes the first popup button found (null when there is none);
 * dismissPopups(targets, { watch: true }) returns a guard that watches the windows and closes
 * every matching button as it appears, on the toolkit thread, while the script goes on with its
 * own work; the script does not exit while the guard runs (stop() releases it). The popups are
 * floating windows of this script shown over the launcher, so the sample runs anywhere.
 *
 * zh-CN: 工具集: 弹窗关闭与弹窗守护 (Roadmap P4.3).
 * dismissPopups(targets) 关闭找到的第一个弹窗按钮 (没有则为 null); dismissPopups(targets, { watch: true }) 返回一个守护, 它
 * 监视窗口并在匹配的按钮出现时随即关闭 (在工具集线程上), 脚本则继续自己的工作; 守护运行期间脚本不会退出 (stop() 释放它).
 * 弹窗是本脚本显示在桌面之上的悬浮窗, 因此示例在任何地方都能运行.
 */

"use strict";

auto.waitFor();

/* Floating windows are only active windows over the launcher on some systems. zh-CN: 某些系统上悬浮窗只有在桌面之上才是活动窗口. */
home();
sleep(1500);

const BUTTONS = ["OK", "Not now", "确定", "以后再说", "Allow", "允许"];

function showPopup(title) {
    const w = floaty.rawWindow(
        <frame bg="#ffffff" padding="16">
            <vertical>
                <text id="title" textSize="18sp" textColor="#000000"/>
                <horizontal>
                    <button id="later" text="Not now"/>
                    <button id="ok" text="OK"/>
                </horizontal>
            </vertical>
        </frame>
    );
    /* Views are touched on the UI thread only. zh-CN: 只在 UI 线程上碰视图. */
    ui.run(() => {
        w.setSize(-2, -2);
        w.setPosition(80, 300);
        w.title.setText(title);
        w.ok.click(() => w.close());
        w.later.click(() => w.close());
    });
    sleep(300);
    ui.run(() => w.requestFocus());
    return w;
}

/* 1. Nothing to dismiss: null, no error. zh-CN: 没有可关闭的: null, 不报错. */
console.log("1. Without a popup:", dismissPopups(BUTTONS, 500));

/* 2. One popup, dismissed once; the result tells which target matched and how it was clicked. zh-CN: 一个弹窗, 关闭一次; 结果说明匹配了哪个目标, 怎样点击的. */
showPopup("Rate this app");
sleep(1000);
const once = dismissPopups(BUTTONS, { timeout: 3000 });
console.log("2. Dismissed target #" + once.index + " (" + once.target + ") by " + once.method);
sleep(800);

/* 3. A guard, then popups appearing while the script is busy with something else. zh-CN: 守护, 然后在脚本忙别的事时弹窗陆续出现. */
const guard = dismissPopups(BUTTONS, {
    watch: true,
    debounce: 150,
    onDismissed: d => console.log("   guard closed \"" + d.target + "\" by " + d.method + " (this callback runs on the script thread when it is idle)"),
    onError: e => console.warn("   guard error:", e),
});
console.log("3. Guard started:", String(guard));
for (let i = 1; i <= 3; i++) {
    showPopup("Special offer " + i);
    let waited = 0;
    while (guard.count < i && waited < 5000) {
        sleep(100);
        waited += 100;
    }
    console.log("   popup " + i + " closed by the guard after " + waited + " ms; dismissed so far: " + guard.count);
    sleep(500);
}
console.log("3. Guard passes: " + guard.passes + ", dismissed: " + guard.dismissed.map(d => d.target).join(", ") + ", running: " + guard.isRunning);
guard.stop();
console.log("3. Guard stopped:", String(guard));

/* 4. The Flow form runs one pass and passes the chain value through. zh-CN: Flow 形式跑一轮并透传链的值. */
showPopup("Update available");
sleep(1000);
flow.of("payload")
    .dismissPopups(BUTTONS, { timeout: 3000 })
    .then(value => console.log("4. Flow dismissed a popup; the value is still:", value))
    .catch(e => console.error("4. Flow failed:", e))
    .sync();

console.log("Done.");
