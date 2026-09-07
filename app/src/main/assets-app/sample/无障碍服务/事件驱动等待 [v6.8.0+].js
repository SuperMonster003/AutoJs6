'auto';

/**
 * 事件驱动等待 [v6.8.0+]
 *
 * waitForIdle / waitForEvent / waitForToast / waitForNotification 由无障碍事件唤醒, 不轮询控件树:
 * 等界面安静下来, 等某类事件, 等文本匹配的 Toast, 等通知. 超时抛出 FlowError (code TIMEOUT).
 * 每个函数在 Flow 上也有对应的链式步骤 (flow.waitForToast 等), 可以先在后台开始等待, 再去触发事件.
 *
 * 示例用本脚本自己的悬浮窗与通知触发事件, 不依赖其他应用.
 */

const OWN = context.getPackageName();

/* 1. waitForIdle: 等待连续 500 毫秒没有无障碍事件. */
const idle = waitForIdle(500);
console.log('1. 安静了', idle.quietFor, '毫秒 (等待期间事件', idle.events, '个, 用时', idle.elapsed, '毫秒)');

/* 2. waitForEvent (Flow 形式): 先在后台开始等待本应用的窗口变化事件, 再显示悬浮窗触发它. */
const windowEvent = flow.waitForEvent(['window_state_changed', 'windows_changed'], { packageName: OWN }, 5000);
sleep(200);
const win = floaty.rawWindow(
    <frame bg="#ffffff" padding="16">
        <vertical>
            <text text="事件演示" textSize="18sp" textColor="#000000"/>
            <button id="btn" text="点我"/>
        </vertical>
    </frame>
);
ui.run(() => {
    win.setSize(-2, -2);
    win.setPosition(80, 300);
});
const ev = windowEvent.sync();
console.log('2. 事件:', android.view.accessibility.AccessibilityEvent.eventTypeToString(ev.eventType), '来自', ev.packageName, '类名', ev.className);
sleep(300);
ui.run(() => win.requestFocus());
sleep(1000);

/* 3. waitForEvent (同步形式) + 函数过滤器: 等待本应用的控件被点击. 同步等待会阻塞脚本线程 (定时器与 then 回调都不会执行), 因此由子线程在 0.5 秒后点击. */
threads.start(() => {
    sleep(500);
    click('点我');
});
const clicked = waitForEvent('view_clicked', e => e.packageName === OWN, 5000);
console.log('3. 点击事件的类名:', clicked.className);

/* 4. waitForToast: 等待其它应用显示的, 文本匹配 (子串 / 正则 / 函数) 的 Toast. 观察者会忽略本应用自己的 Toast,
 * 因此这里只演示超时: 没有匹配的 Toast 时以 FlowError 拒绝, attempts 为期间看到的 Toast 数. */
try {
    waitForToast('不会出现的 Toast', 800);
} catch (e) {
    console.log('4.', e.name, e.code, '步骤', e.step, '已等待', e.elapsed, '毫秒, 期间看到的 Toast', e.attempts, '个');
}

/* 5. waitForNotification (Flow 形式): 先在后台开始等待, 再发一条通知触发它.
 * 需要 AutoJs6 的通知权限; API 26 之前静默通知不产生无障碍事件, 因此在那里带振动. */
function postNotification(title, text) {
    if (device.sdkInt >= 26) {
        return notice(title, text);
    }
    return notice(new Packages.androidx.core.app.NotificationCompat.Builder(context, 'sample')
        .setSmallIcon(context.getApplicationInfo().icon)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(1)
        .setDefaults(android.app.Notification.DEFAULT_VIBRATE));
}
const notification = flow.waitForNotification('示例通知', 5000);
sleep(300);
postNotification('示例通知', '事件驱动等待');
try {
    const n = notification.sync();
    console.log('5. 通知:', n.getTitle(), '-', n.getText());
} catch (e) {
    console.log('5. 未等到通知 (' + e.code + '); 请检查 AutoJs6 是否允许发送通知');
}

/* 6. 链式: 等到通知后再等界面安静 300 毫秒, 然后取通知文本. */
const chained = flow.waitForNotification('链式通知', 5000)
    .then(n => n.getText())
    .waitForIdle(300);
sleep(300);
postNotification('链式通知', '链式步骤');
try {
    console.log('6. Flow:', chained.sync());
} catch (e) {
    console.log('6. Flow 未等到通知 (' + e.code + ')');
}
context.getSystemService(android.content.Context.NOTIFICATION_SERVICE).cancelAll();

ui.run(() => win.close());
console.log('完成');
