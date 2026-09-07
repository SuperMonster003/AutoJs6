'auto';

/**
 * 流程 (Flow): 异步等待与链式动作 [v6.8.0+]
 *
 * waitAsync(cond, timeout) 与 flow.wait(cond, timeout) 立即返回一个 Flow 对象: 等待在后台线程进行, 脚本线程不被阻塞;
 * 链上追加的步骤 (click, wait, waitForHidden ...) 在前一步完成后依次执行, then / catch 回调回到脚本线程.
 * 需要顺序执行时用 sync() 阻塞到结果; 出错时得到 FlowError (code / step / elapsed / reason).
 *
 * 示例以本脚本自己的悬浮窗为目标, 不依赖其他应用; 换成真实应用的选择器即可.
 * 某些系统上悬浮窗只有在桌面之上才是活动窗口 (无障碍服务才看得到), 因此先回到桌面.
 */

home();
sleep(1500);

const win = floaty.rawWindow(
    <frame bg="#ffffff" padding="16">
        <vertical>
            <text text="Flow 演示" textSize="18sp" textColor="#000000"/>
            <button id="load" text="开始加载"/>
            <text id="status" text="尚未开始" textColor="#000000"/>
            <text id="counter" text="计数 0" textColor="#000000"/>
            <button id="dismiss" text="关闭提示"/>
        </vertical>
    </frame>
);

/* 只在 UI 线程上碰视图 (包括设置监听). */
ui.run(() => {
    win.setSize(-2, -2);
    win.setPosition(80, 300);
    /* "开始加载": 先显示 "加载中", 1.5 秒后变为 "加载完成"; 同时 "计数" 每 200 毫秒加一, 共 5 次. */
    win.load.click(() => {
        win.status.setText('加载中');
        win.status.postDelayed(() => win.status.setText('加载完成'), 1500);
        for (let i = 1; i <= 5; i++) {
            win.counter.postDelayed(() => win.counter.setText('计数 ' + i), i * 200);
        }
    });
    /* "关闭提示": 清空状态文本. */
    win.dismiss.click(() => win.status.setText(''));
});
sleep(300);
ui.run(() => win.requestFocus()); /* 使悬浮窗成为无障碍服务眼中的活动窗口. */
sleep(1500);

/* 1. 等待按钮出现并点击, 再等待 "加载完成" 出现. 链建立后脚本线程继续运行, 直到 sync() 才阻塞. */
const loading = waitAsync('开始加载', 3000)
    .click()
    .then(() => console.log('1. 已点击 "开始加载", 等待加载完成 ...'))
    .wait('加载完成', 5000)
    .then(w => console.log('1. 出现了:', w.text()));
console.log('1. 链已建立, 当前状态:', loading.state); /* pending */
loading.sync(); /* 阻塞直到链完成; 链上出错时这里抛出 FlowError. */

/* 2. waitForStable: 等待计数文本连续 600 毫秒不再变化. */
const stable = waitForStable(textStartsWith('计数'), { stableFor: 600, timeout: 5000 }).sync();
console.log('2. 计数已稳定:', stable.text());

/* 3. waitForHidden: 点击 "关闭提示" 后等待 "加载完成" 消失 (文本被清空). */
waitAsync('关闭提示', 3000)
    .click()
    .waitForHidden('加载完成', 3000)
    .then(() => console.log('3. "加载完成" 已消失'))
    .sync();

/* 4. 出错: 目标不存在时超时拒绝, catch 得到 FlowError; 之后 sync() 返回 catch 的返回值而不再抛出. */
const reason = waitAsync('不存在的文本', 800)
    .then(() => '不应到达这里')
    .catch(e => `${e.name} ${e.code}: 步骤 ${e.step}, 已等待 ${e.elapsed} 毫秒, 尝试 ${e.attempts} 次`)
    .sync();
console.log('4.', reason);

/* 5. 组合: all 等待多个目标 (结果数组), race 取最先完成的; 落败的一方用 cancel() 取消. */
const both = flow.all('开始加载', textStartsWith('计数')).sync();
console.log('5. all:', both.map(w => w.text()).join(' | '));
const slow = waitAsync('不存在的文本', 10000);
const first = flow.race(slow, flow.sleep(300).then(() => '定时器先到')).sync();
console.log('5. race:', first, '; 取消落败者:', slow.cancel(), slow.state);

/* 6. 限时等待结果: sync(timeout) 到时抛出 TIMEOUT (reason "sync"), 链本身继续等待, 可用 cancel() 取消. */
const pending = waitAsync('不存在的文本', 60000);
try {
    pending.sync(500);
} catch (e) {
    console.log('6. sync 限时到期:', e.code, e.reason, '; 链仍在等待:', pending.state);
}
console.log('6. 取消:', pending.cancel(), '状态:', pending.state);

ui.run(() => win.close());
console.log('完成');
