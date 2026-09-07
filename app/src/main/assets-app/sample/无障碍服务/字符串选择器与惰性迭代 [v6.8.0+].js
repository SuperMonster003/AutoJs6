'auto';

/**
 * 字符串选择器与惰性迭代 [v6.8.0+]
 *
 * select(语法) 用一个字符串写出多个筛选条件: 以空白分隔, key=value / key~=正则 / key=/正则/标志 / 布尔键 / !布尔键,
 * 含空格的值用引号包裹. 全局 select('...') 从空选择器开始, 也可以追加到已有选择器上.
 * findIterator() 惰性搜索: 迭代器前进时才逐个产出匹配的控件, 提前停止就不再遍历其余节点.
 *
 * 示例以本脚本自己的悬浮窗为目标. 某些系统上悬浮窗只有在桌面之上才是活动窗口, 因此先回到桌面.
 */

home();
sleep(1500);

const win = floaty.rawWindow(
    <frame bg="#ffffff" padding="16">
        <vertical>
            <text text="选择器演示" textSize="18sp" textColor="#000000"/>
            <horizontal>
                <button id="ok" text="确定"/>
                <button id="cancel" text="取消"/>
                <button id="disabled" text="已禁用" enabled="false"/>
            </horizontal>
            <input id="name" hint="名称" w="200dp"/>
            <input id="pwd" hint="密码" inputType="textPassword" w="200dp"/>
            <text text="第 1 项" textColor="#000000"/>
            <text text="第 2 项" textColor="#000000"/>
            <text text="第 3 项" textColor="#000000"/>
        </vertical>
    </frame>
);
ui.run(() => {
    win.setSize(-2, -2);
    win.setPosition(80, 300);
    win.disabled.setEnabled(false);
});
sleep(300);
ui.run(() => win.requestFocus()); /* 使悬浮窗成为无障碍服务眼中的活动窗口. */
sleep(1500);

/* 1. key=value, 布尔键与 !布尔键, key=/正则/. */
console.log('1.', select('text=确定 clickable').findOne(3000).text());
console.log('1.', select('className=/Button$/ !enabled').findOnce().text());
console.log('1. 输入框', select('className=/EditText$/').find().length, '个, 其中非密码框', select('className=/EditText$/ !password').find().length, '个');

/* 2. 追加到已有选择器; key~=正则 (搜索语义). 控件集合是数组, 可以直接 map. */
console.log('2.', className('android.widget.Button').select('text~=^(确定|取消)$').find().map(w => w.text()).join(', '));

/* 3. 含空格的值用引号. */
console.log('3.', select('text="第 2 项"').exists(), select("text='第 9 项'").exists());

/* 4. 语法错误: 异常消息给出出错位置与候选键名. */
try {
    select('text=确定 clikable');
} catch (e) {
    console.log('4.', e.message);
}

/* 5. findIterator: 手动迭代, 找到目标就停止; visited / produced 说明访问了多少节点, 产出了多少匹配. */
const it = className('android.widget.Button').findIterator();
while (it.hasNext()) {
    let w = it.next();
    if (w.enabled() && w.text() === '取消') {
        console.log('5. 找到并停止于:', w.text());
        break;
    }
}
console.log('5. 已访问节点', it.visited, '个, 产出匹配', it.produced, '个; 已关闭:', it.isClosed());
it.close(); /* 提前结束搜索, 释放尚未到达的节点. */

/* 6. 迭代器可直接用于 for...of; break 后其余节点不再遍历. */
for (let w of textStartsWith('第 ').findIterator()) {
    console.log('6.', w.text());
    if (w.text() === '第 2 项') {
        break;
    }
}

/* 7. find(max) 限制数量 (负数取末尾); 集合的 first / last / at / slice. */
const twoItems = textStartsWith('第 ').find(2);
console.log('7. 前两项:', twoItems.map(w => w.text()).join(', '), '; 最后一项:', textStartsWith('第 ').find(-1).first().text());
const all = textStartsWith('第 ').find();
console.log('7. first / last / at(-1) / slice(1):', all.first().text(), all.last().text(), all.at(-1).text(), all.slice(1).length);

/* 8. 注意: pickup / wait / Flow 的条件参数中, 字符串仍是内容匹配; 需要字符串语法时显式传入 select('...'). */
console.log('8.', pickup('确定').text(), '|', waitAsync(select('text=取消 clickable'), 2000).sync().text());

ui.run(() => win.close());
console.log('完成');
