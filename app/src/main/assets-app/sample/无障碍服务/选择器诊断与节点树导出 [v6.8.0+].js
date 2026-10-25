'auto';

/**
 * 选择器诊断与节点树导出 [v6.8.0+]
 *
 * auto.explain(selector, root?) 解释选择器为什么匹配或不匹配: 逐个过滤器单独与叠加的命中数, 失败的一步, "差一点" 的节点与文本报告.
 * auto.explain(selector, compass?, resultType?) 还说明单个结果经历的过程: 取哪个节点 (含 prefer 的选择), 罗盘每一步到达的节点,
 * 结果类型解析到的方法与参数 (只解析, 不调用), 以及快路径, 命中刷新与 normalize 是否生效.
 * auto.dump(options?) 与 UiObject.dumpSubtree(options?) 以文本 / JSON / XML 导出节点树; JSON.stringify(控件) 得到其属性对象.
 * auto.stats 汇总自上次重置以来的搜索, 轮询查询与 Flow 步骤耗时.
 *
 * 示例以本脚本自己的悬浮窗为目标. 某些系统上悬浮窗只有在桌面之上才是活动窗口, 因此先回到桌面.
 */

home();
sleep(1500);

const win = floaty.rawWindow(
    <frame bg="#ffffff" padding="16">
        <vertical>
            <text text="诊断演示" textSize="18sp" textColor="#000000"/>
            <text text="请先登录" textColor="#000000"/>
            <checkbox id="agree" text="同意条款"/>
            <button id="login" text="登录"/>
        </vertical>
    </frame>
);
ui.run(() => {
    win.setSize(-2, -2);
    win.setPosition(80, 300);
});
sleep(300);
ui.run(() => win.requestFocus()); /* 使悬浮窗成为无障碍服务眼中的活动窗口. */
sleep(1500);

/* 1. 能匹配的选择器: 每一步的命中数与最终结果. */
const ok = auto.explain(text('登录').clickable());
console.log('1. 找到:', ok.found, '数量:', ok.count, '搜索节点:', ok.nodes);
console.log(ok.text);

/* 2. 不能匹配的选择器: failingIndex / failingFilter 指出第一个使结果为空的过滤器, nearMisses 是只差这一步的节点. */
const bad = auto.explain(text('登录').checkable());
console.log('2. 找到:', bad.found, '; 失败于第', bad.failingIndex, '个过滤器', bad.failingFilter, '; 差一点的节点', bad.nearMisses.length, '个');
bad.steps.forEach(step => console.log('   过滤器', step.index, step.filter, '单独', step.matched, '叠加', step.cumulative));

/* 3. 限定根节点后解释 (只在悬浮窗内搜索). */
const panel = text('诊断演示').findOne(3000).parent();
console.log('3. 限定在悬浮窗内:', auto.explain('同意条款', panel).count, '个');

/* 4. 单个结果经历的过程: 从 "同意条款" 出发, 罗盘 p1c3 先到父节点, 再到其第 4 个子节点 ("登录" 按钮), 结果类型 'text' 解析为节点方法. */
const pick = auto.explain(text('同意条款').prefer('visible'), 'p1c3', 'text');
console.log('4. 单个结果: 第', pick.targetIndex + 1, '个 (共', pick.count, '个)');
pick.trail.forEach(hop => console.log('   罗盘', hop.step, '->', hop.node === null ? '无' : hop.node.className()));
console.log('4. 结果类型:', pick.result.kind, '; 快路径:', pick.fastPath, '; 命中刷新:', pick.refreshOnHit);
console.log(pick.text);

/* 5. dumpSubtree: 文本形式 (默认) 与 JSON 形式; auto.dump 的选项相同, 并可指定 root 与 format. */
const tree = panel.dumpSubtree({ maxDepth: 2 });
console.log('5. 文本形式:\n' + tree);
const json = JSON.parse(panel.dumpSubtree('json'));
console.log('5. JSON 形式的键:', Object.keys(json).join(', '));
console.log('5. XML 形式 (1 层):', auto.dump({ root: panel, format: 'xml', maxDepth: 1 }).length, '个字符');

/* 6. toJSON: JSON.stringify 直接得到控件自身的属性. */
console.log('6.', JSON.stringify(text('登录').findOnce()));

/* 7. stats: 先重置, 做几次查询, 再看汇总. */
auto.stats.reset();
text('登录').findOne(2000);
className('android.widget.Button').find();
waitAsync('同意条款', 1000).sync();
text('不存在的文本').findOne(300);
const stats = auto.stats;
console.log('7. 搜索', stats.searches.count, '次, 轮询查询', stats.queries.count, '次 (命中', stats.queries.found, '次), 最慢的选择器:', stats.slowest === null ? '无' : stats.slowest.selector);
console.log(stats.text);

ui.run(() => win.close());
console.log('完成');
