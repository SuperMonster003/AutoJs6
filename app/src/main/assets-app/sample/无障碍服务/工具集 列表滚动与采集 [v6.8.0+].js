'auto';

/**
 * 工具集: 列表滚动与采集 [v6.8.0+]
 *
 * collectList(container, item, options) 逐步滚动容器并采集每一步看到的项 (默认取控件内容, 自动去重);
 * scrollUntil(target, options) 滚动到目标出现为止并返回它. 两者默认用滚动动作, 也可用手势 (method: "gesture").
 *
 * 列表是本脚本的悬浮窗, 因此示例可在任何设备上运行; 换成真实应用的选择器即可.
 * 某些系统上悬浮窗只有在桌面之上才是活动窗口 (无障碍服务才看得到), 因此先回到桌面.
 */

home();
sleep(1500);

const win = floaty.rawWindow(
    <frame bg="#ffffff" padding="16">
        <vertical>
            <text text="列表演示" textSize="18sp" textColor="#000000"/>
            <scroll id="scroll" w="220dp" h="200dp">
                <vertical id="items"/>
            </scroll>
        </vertical>
    </frame>
);
/* 只在 UI 线程上碰视图: 往列表里放 40 行文本. */
ui.run(() => {
    win.setSize(-2, -2);
    win.setPosition(80, 300);
    for (let i = 1; i <= 40; i++) {
        let row = ui.inflate(<text textSize="16sp" textColor="#000000" padding="8"/>, win.items, false);
        row.setText('第 ' + i + ' 项');
        win.items.addView(row);
    }
});
sleep(300);
ui.run(() => win.requestFocus()); /* 使悬浮窗成为无障碍服务眼中的活动窗口. */
sleep(1500);

/* 容器: 本应用的可滚动控件; 项: 以 "第 " 开头的文本. */
const list = scrollable().packageName(context.getPackageName());
const item = textStartsWith('第 ');

/* 1. collectList: 从当前位置向下最多滚 5 步, 采集看到的项. */
const items = collectList(list, item, { maxSteps: 5, settle: 300 });
console.log('1. 采集到', items.length, '项:', items.slice(0, 3).join(', '), '...', items[items.length - 1]);

/* 2. until: 某一项满足条件时停止 (含该项); maxItems 限制数量. */
const firstTen = collectList(list, item, { direction: 'up', maxSteps: 10, settle: 300, until: text => text === '第 1 项' });
console.log('2. 向上采集到 "第 1 项" 为止:', firstTen.length, '项, 最后一项:', firstTen[firstTen.length - 1]);
console.log('2. 最多 3 项:', collectList(list, item, { maxSteps: 2, maxItems: 3, settle: 300 }).join(', '));

/* 3. scrollUntil: 滚动到远处的项, 再滚回顶部. */
const far = scrollUntil('第 30 项', { container: list, maxSteps: 20, settle: 300 });
console.log('3. 滚到了:', far.text(), far.bounds());
console.log('3. 回到顶部:', scrollUntil('第 1 项', { container: list, direction: 'up', maxSteps: 20, settle: 300 }).text());

/* 4. 目标不存在: 滚到底 (reason "end") 或步数用尽 (reason "maxSteps") 时以 FlowError 拒绝. */
try {
    scrollUntil('第 99 项', { container: list, maxSteps: 3, settle: 200 });
} catch (e) {
    console.log('4.', e.code, 'reason:', e.reason, '-', e.message);
}

/* 5. 同样的事写成 Flow 链: 等到容器, 采集, 再滚动到目标; 直到 sync() 才阻塞. */
waitAsync(list, 3000)
    .collectList(item, { maxSteps: 2, settle: 300 })
    .then(collected => console.log('5. Flow 采集:', collected.length, '项'))
    .then(() => flow.scrollUntil('第 20 项', { container: list, maxSteps: 15, settle: 300 }))
    .then(w => console.log('5. Flow 滚到:', w.text()))
    .catch(e => console.error('5. Flow 失败:', e))
    .sync();

ui.run(() => win.close());
console.log('完成');
