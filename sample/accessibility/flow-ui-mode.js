"ui";

/**
 * Flow in UI mode: the main thread stays responsive (roadmap P3.4).
 *
 * Blocking steps of a chain run on a worker thread and the callbacks come back to the thread
 * that created the chain, so a chain started from a button handler never freezes the UI. The
 * page waits for its own widgets through the accessibility service: waitForVisible() resolves
 * once the hidden text is shown, waitForHidden() once it is hidden again. sync() is refused on
 * the UI thread; the "sync in thread" button shows the right way. The demo also runs by itself
 * two seconds after start, so it can be verified without touching the screen.
 *
 * zh-CN: UI 模式下的 Flow: 主线程保持响应 (Roadmap P3.4).
 * 链的阻塞步骤在工作线程运行, 回调回到创建链的线程, 因此在按钮回调里启动的链不会冻结界面. 页面经无障碍服务等待自己的控件:
 * waitForVisible() 在隐藏文本显示后兑现, waitForHidden() 在其再次隐藏后兑现. sync() 在 UI 线程被拒绝; "sync in thread" 按钮
 * 展示正确做法. 启动 2 秒后演示会自动运行一遍, 便于不触屏验证.
 */

ui.layout(
    <vertical padding="16">
        <text id="status" textSize="16sp" text="Ready." />
        <text id="counter" textSize="14sp" text="UI ticks: 0" />
        <button id="wait" text="Wait for the hidden text" />
        <button id="sync" text="sync() on the UI thread (refused)" />
        <button id="thread" text="sync() in a thread (fine)" />
        <text id="hidden" textSize="18sp" text="I am the hidden text" visibility="gone" />
    </vertical>
);

const HIDDEN = "I am the hidden text";

function setStatus(s) {
    ui.run(() => ui.status.setText(String(s)));
    console.log(s);
}

/* The UI keeps ticking while chains wait: proof that nothing blocks the main thread. zh-CN: 链等待期间界面照常计数: 证明主线程未被阻塞. */
let ticks = 0;
setInterval(() => ui.counter.setText("UI ticks: " + (++ticks)), 200);

function waitDemo() {
    setStatus("Waiting for the hidden text (shown in 1 s)...");
    setTimeout(() => ui.hidden.attr("visibility", "visible"), 1000);
    setTimeout(() => ui.hidden.attr("visibility", "gone"), 3000);
    return waitForVisible(text(HIDDEN), { timeout: 5000, stableFor: 200 })
        .then(o => setStatus("Visible: " + o.text() + " (callback on " + java.lang.Thread.currentThread().getName() + ")"))
        .waitForHidden(text(HIDDEN), { timeout: 5000, stableFor: 200 })
        .then(() => setStatus("Hidden again."))
        .catch(e => setStatus("Failed: " + e.code + " " + e.message));
}

ui.wait.on("click", () => waitDemo());

ui.sync.on("click", () => {
    try {
        flow.of(1).sync();
        setStatus("sync() unexpectedly succeeded on the UI thread");
    } catch (e) {
        setStatus("Refused as expected: " + e.message);
    }
});

ui.thread.on("click", () => {
    setStatus("sync() in a thread...");
    threads.start(() => {
        const value = waitAsync(textStartsWith("UI ticks"), 3000).map(o => o.text()).sync();
        setStatus("sync() in a thread returned: " + value);
    });
});

/* Self-driving demo for device verification. zh-CN: 供真机验证的自动演示. */
setTimeout(() => {
    auto.waitFor();
    ui.sync.performClick();
    waitDemo().then(() => ui.thread.performClick());
}, 2000);
