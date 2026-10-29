'auto';

/**
 * 选择器规范化与候选偏好 [v6.8.0+]
 *
 * normalize(...) 使选择器先统一文本格式再比较. 选项: trim (去除首尾空白), caseInsensitive (忽略大小写),
 * fullWidth (全角字母, 数字, 标点与全角空格转为半角), whitespace (连续空白折叠为一个空格, 含不换行空格; 移除零宽字符),
 * nfkc (Unicode NFKC 兼容字符转换, 例如 ① 转为 1). normalize() 与 normalize(true) 启用全部选项, normalize(false) 关闭,
 * normalize({ ... }) 只启用给出的选项. text / desc / content 系列条件采用全部选中的选项; id / className / packageName
 * 只采用 trim 与 caseInsensitive. 字面条件 (text, textContains 等) 两侧都规范化; 正则条件 (textMatch 等) 只规范化控件的值,
 * caseInsensitive 时正则忽略大小写. normalize 是选择器的设置而不是条件: 写在条件之前或之后都作用于整个选择器, 写了多次时最后一个生效.
 *
 * prefer(键...) 在多个控件都符合条件时决定单个结果取哪一个. 键: visible (对用户可见), clickable (可点击), enabled (已启用),
 * center (中心离屏幕中心近), area (面积大), top (靠上). 按给出的顺序逐键比较, 都相同时取先找到的控件.
 * 只影响单个结果: findOnce() / findOne() / untilFindOne() 与 pickup 的单个控件 (及其罗盘), 不影响 find() 与 findOnce(index).
 * 含 visible 时, 罗盘 k (向上找可点击的控件) 只停在对用户可见的可点击控件上.
 *
 * 两者都有同名全局函数 (从空选择器开始), 也可以写在对象选择器 ({ normalize: ..., prefer: ... }) 与字符串选择器
 * (select('... normalize prefer=visible')) 中. auto.explain 的报告说明 normalize 与 prefer 是否生效以及取了哪个控件
 * (参阅 示例代码 > 无障碍服务 > 选择器诊断与节点树导出).
 *
 * 示例以本脚本自己的悬浮窗为目标. 某些系统上悬浮窗只有在桌面之上才是活动窗口, 因此先回到桌面.
 */

home();
sleep(1500);

const NBSP = String.fromCharCode(0xa0); /* 不换行空格. */

const win = floaty.rawWindow(
    <frame bg="#ffffff" padding="16">
        <vertical>
            <text text="规范化与偏好演示" textSize="18sp" textColor="#000000"/>
            <text id="greeting" textColor="#000000"/>
            <text text="第①项" textColor="#000000"/>
            <horizontal>
                <text text="保存" textColor="#000000"/>
                <button text="保存"/>
            </horizontal>
            <button id="submitOff" text="提交"/>
            <button text="提交"/>
            <horizontal id="hiddenRow">
                <text text="下一步" textColor="#000000"/>
            </horizontal>
            <horizontal id="shownRow">
                <text text="下一步" textColor="#000000"/>
            </horizontal>
            <frame w="240" h="96">
                <text text="标签" textSize="24sp" textColor="#000000" layout_gravity="bottom|left"/>
                <text text="标签" textSize="12sp" textColor="#000000" layout_gravity="top|right"/>
            </frame>
        </vertical>
    </frame>
);
ui.run(() => {
    win.setSize(-2, -2);
    win.setPosition(80, 300);
    /* 含全角字母, 首尾空白与两个不换行空格的文本. */
    win.greeting.setText('  Ｈｅｌｌｏ' + NBSP + NBSP + 'World ');
    win.submitOff.setEnabled(false);
    win.hiddenRow.setClickable(true);
    win.shownRow.setClickable(true);
    /* 移出窗口: 这一行仍在控件树中, 但对用户不可见 (如同列表中滚出可见区域的项). */
    win.hiddenRow.setTranslationY(-5000);
});
sleep(300);
ui.run(() => win.requestFocus()); /* 使悬浮窗成为无障碍服务眼中的活动窗口. */
sleep(1500);

/* 1. 两侧规范化后比较: 不加 normalize 时匹配不到 "  Ｈｅｌｌｏ  World ". */
console.log('1. 不规范化:', text('Hello World').exists(), '; normalize():', text('Hello World').normalize().exists(), '; 小写也匹配:', text('hello world').normalize().exists());

/* 2. 选项对象只启用给出的选项: 不含 caseInsensitive 时大小写仍须一致. */
const opts = { trim: true, fullWidth: true, whitespace: true };
console.log('2. 大小写一致:', text('Hello World').normalize(opts).exists(), '; 大小写不同:', text('hello world').normalize(opts).exists(),
    '; 加上 caseInsensitive:', text('hello world').normalize(Object.assign({ caseInsensitive: true }, opts)).exists());

/* 3. 设置而不是条件: 全局函数 normalize(...) 从空选择器开始, 写在条件之前同样生效; 写了多次时最后一个生效. */
console.log('3. 写在条件之前:', normalize().text('HELLO WORLD').exists(), '; normalize().normalize(false):', text('Hello World').normalize().normalize(false).exists());
console.log('3. toString:', text('Hello World').normalize({ trim: true, caseInsensitive: true }).toString());

/* 4. 其他字面条件两侧规范化; 正则条件只规范化控件的值, caseInsensitive 使正则忽略大小写. */
console.log('4. textContains:', textContains('o w').normalize().exists(), '; textStartsWith:', textStartsWith('hello').normalize().exists(),
    '; textMatch:', textMatch(/^HELLO WORLD$/).normalize().exists());

/* 5. nfkc: 兼容字符转为标准形式 (① 转为 1). */
console.log('5. 不规范化:', text('第1项').exists(), '; nfkc:', text('第1项').normalize({ nfkc: true }).exists());

/* 6. 名称类条件 (id / className / packageName) 只采用 trim 与 caseInsensitive. */
console.log('6. className:', className('android.widget.button').exists(), '; 忽略大小写:', className('android.widget.button').normalize().exists());

/* 7. 对象选择器与字符串选择器. */
console.log('7. 对象选择器:', pickup({ text: 'hello world', normalize: true }) !== null,
    pickup({ text: 'Hello World', normalize: { trim: true, fullWidth: true, whitespace: true } }) !== null);
console.log('7. 字符串选择器:', select('text="hello world" normalize').exists(), select('text="hello world" !normalize').exists());

/* 8. 默认取先找到的控件, prefer 按偏好取. 两个 "保存": 先找到的是文本标签 (不可点击), 其后是按钮. */
console.log('8. 默认:', text('保存').findOnce().className(), '; prefer(clickable):', text('保存').prefer('clickable').findOnce().className());

/* 9. 两个 "提交" 按钮, 上面的已禁用. 键的顺序即优先级. */
console.log('9. 默认:', text('提交').findOnce().enabled(), '; prefer(enabled):', text('提交').prefer('enabled').findOnce().enabled());
console.log('9. prefer(top, enabled):', text('提交').prefer('top', 'enabled').findOnce().enabled(), '; prefer(enabled, top):', text('提交').prefer('enabled', 'top').findOnce().enabled());

/* 10. 两个 "下一步", 第一个所在的行已移出窗口. findOne / untilFindOne / pickup 同样按偏好取单个结果. */
console.log('10. 默认:', text('下一步').findOnce().visibleToUser(), '; prefer(visible):', text('下一步').prefer('visible').findOne(2000).visibleToUser(),
    '; pickup:', pickup({ text: '下一步', prefer: 'visible' }).visibleToUser());

/* 11. 罗盘 k 从文本向上找可点击的控件 (这里是整行); 含 visible 时只停在对用户可见的可点击控件上. */
const nextRow = pickup(text('下一步').prefer('visible'), 'k');
console.log('11. 罗盘 k:', nextRow.className(), '; 可点击:', nextRow.clickable(), '; 可见:', nextRow.visibleToUser(), '; 不加 prefer:', pickup(text('下一步'), 'k').visibleToUser());

/* 12. area / top / center: 两个 "标签", 先找到的字号大且在下方. find() 不受 prefer 影响, 仍按找到的顺序. */
const labels = text('标签').prefer('area').find();
const big = labels.first(), small = labels.last();
const same = (a, b) => a.bounds().equals(b.bounds());
console.log('12. area 取大的:', same(text('标签').prefer('area').findOnce(), big), '; top 取上方的:', same(text('标签').prefer('top').findOnce(), small));
const dist = w => Math.hypot(w.centerX() - device.width / 2, w.centerY() - device.height / 2);
console.log('12. center 取中心离屏幕中心近的:', dist(text('标签').prefer('center').findOnce()) <= Math.min(dist(big), dist(small)));

/* 13. 全局函数 prefer(...), 对象选择器 (一个键用字符串, 多个键用数组) 与字符串选择器. */
console.log('13.', prefer('enabled').text('提交').findOnce().enabled(), pickup({ text: '提交', prefer: ['enabled', 'top'] }).enabled(),
    select('text=提交 prefer=enabled').findOnce().enabled());

/* 14. 选项或键写错时报错. */
try {
    text('Hello').normalize({ trim: 'yes' });
} catch (e) {
    console.log('14.', e.message);
}
try {
    text('提交').prefer('left');
} catch (e) {
    console.log('14.', e.message);
}

ui.run(() => win.close());
console.log('完成');
