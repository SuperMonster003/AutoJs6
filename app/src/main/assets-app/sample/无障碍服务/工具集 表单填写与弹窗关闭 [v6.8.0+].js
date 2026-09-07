'auto';

/**
 * 工具集: 表单填写与弹窗关闭 [v6.8.0+]
 *
 * typeInto / toggle / smartClick / clickIfExists / dismissPopups 是同步的高阶函数 (同时存在于全局与 automator 模块):
 * 目标可以是文本, 正则, 选择器或控件; 失败时抛出 FlowError. 每个函数在 Flow 上也有对应的链式步骤.
 *
 * 表单与弹窗都是本脚本的悬浮窗, 因此示例不需要账号即可在任何设备上运行; 换成真实应用的选择器即可.
 * 某些系统上悬浮窗只有在桌面之上才是活动窗口 (无障碍服务才看得到), 因此先回到桌面.
 */

home();
sleep(1500);

const form = floaty.rawWindow(
    <frame bg="#ffffff" padding="16">
        <vertical>
            <text text="演示登录" textSize="18sp" textColor="#000000"/>
            <input id="user" hint="用户名" w="240dp"/>
            <input id="pass" hint="密码" inputType="textPassword" w="240dp"/>
            <checkbox id="remember" text="记住我"/>
            <button id="signIn" text="登录"/>
            <text id="status" text="" textColor="#000000"/>
        </vertical>
    </frame>
);
/* 只在 UI 线程上碰视图 (包括设置监听). */
ui.run(() => {
    form.setSize(-2, -2);
    form.setPosition(80, 300);
    form.signIn.click(() => form.status.setText('欢迎, ' + form.user.getText() + (form.remember.isChecked() ? ' (已记住)' : '')));
});
sleep(300);
ui.run(() => form.requestFocus()); /* 使悬浮窗成为无障碍服务眼中的活动窗口. */
sleep(1500);

/* 1. typeInto: 按可编辑控件索引 (0 为窗口里第一个输入框) 输入; 默认先清空, 并校验文本已写入. */
const userField = typeInto(0, 'alice', { timeout: 3000 });
console.log('1. 用户名:', userField.text());

/* 2. 按选择器定位密码框; 密码框上报的是圆点, 因此关闭校验. */
typeInto(className('android.widget.EditText').depth(userField.depth()).indexInParent(2), 's3cret', { verify: false, timeout: 3000 });
console.log('2. 密码框上报的文本:', JSON.stringify(className('android.widget.EditText').indexInParent(2).findOnce().text()));

/* 3. clear: false 时追加而不是替换. */
console.log('3. 追加后:', typeInto(0, '@example.com', { clear: false }).text());

/* 4. toggle: 把复选框设为想要的状态, 已经是该状态时不点击 (changed 为 false). */
const toggled = toggle('记住我', true, { timeout: 2000 });
console.log('4. 记住我:', toggled.checked, toggled.changed ? '(点击了)' : '(本来就是)', '; 再设一次:', toggle('记住我', true).changed ? '有变化' : '无变化');

/* 5. smartClick: 节点不可点击时上溯到可点击祖先, 再不行则手势点按中心; verify 指定点击后必须出现的目标. */
const clicked = smartClick('登录', { timeout: 2000, verify: textStartsWith('欢迎'), verifyTimeout: 3000 });
console.log('5. 登录按钮以', clicked.method, '方式点击; 结果:', textStartsWith('欢迎').findOnce().text());

/* 6. dismissPopups: 按候选按钮文本找到弹窗并关闭 (返回匹配的候选与点击方式); 没有弹窗时返回 null 而不报错. */
const BUTTONS = ['以后再说', '取消', '关闭', 'Not now', 'OK'];
console.log('6. 没有弹窗时:', String(dismissPopups(BUTTONS, 500)));
const popup = floaty.rawWindow(
    <frame bg="#ffffff" padding="16">
        <vertical>
            <text text="喜欢这个应用吗?" textSize="18sp" textColor="#000000"/>
            <horizontal>
                <button id="later" text="以后再说"/>
                <button id="rate" text="去评分"/>
            </horizontal>
        </vertical>
    </frame>
);
ui.run(() => {
    popup.setSize(-2, -2);
    popup.setPosition(120, 700);
    popup.later.click(() => popup.close());
    popup.rate.click(() => popup.close());
});
sleep(300);
ui.run(() => popup.requestFocus());
sleep(1000);
const closed = dismissPopups(BUTTONS, { timeout: 3000 });
console.log('6. 关闭了候选 #' + closed.index + ' (' + closed.target + '), 方式:', closed.method);
sleep(500);
ui.run(() => form.requestFocus());
sleep(800);

/* 7. clickIfExists: 目标存在才点击, 不存在时返回 false 且不报错. */
console.log('7. clickIfExists:', clickIfExists('不存在的按钮', 300), clickIfExists('登录', 1000));

/* 8. 错误是 FlowError 对象: 找不到字段. */
try {
    typeInto('不存在的输入框', 'x', { timeout: 300 });
} catch (e) {
    console.log('8.', e.name, e.code, '步骤', e.step, '已等待', e.elapsed, '毫秒:', e.message);
}

/* 9. 同样的事写成 Flow 链: 定位, 输入, 切换, 点击, 等待结果; 直到 sync() 才阻塞. */
ui.run(() => form.status.setText(''));
waitAsync(className('android.widget.EditText'), 3000)
    .typeInto('bob')
    .then(field => console.log('9. Flow 输入:', field.text()))
    .then(() => flow.toggle('记住我', false))
    .then(r => console.log('9. Flow 取消勾选:', r.changed))
    .then(() => flow.smartClick('登录'))
    .wait(textStartsWith('欢迎'), 3000)
    .then(status => console.log('9. Flow 结果:', status.text()))
    .catch(e => console.error('9. Flow 失败:', e))
    .sync();

ui.run(() => form.close());
console.log('完成');
