/**
 * Toolkit: filling in a login form (roadmap P4.3).
 *
 * typeInto / toggle / smartClick on a form: the fields by editable index or by selector, a
 * password field (its text is reported masked, so the entry is not verified), a check box set to
 * a wanted state, and the submit button clicked through a clickable ancestor if needed. The form
 * is a floating window of this script shown over the launcher, so the sample runs on any device
 * without an account; replace the selectors with those of the real app. The synchronous functions
 * exist on the automator module and as globals; the Flow steps do the same asynchronously.
 *
 * zh-CN: 工具集: 登录表单填写 (Roadmap P4.3).
 * 在一个表单上演示 typeInto / toggle / smartClick: 按可编辑控件索引或选择器定位字段, 密码框 (其文本上报为掩码, 因此不校验输入),
 * 把复选框设为想要的状态, 以及点击提交按钮 (必要时上溯到可点击祖先). 表单是本脚本显示在桌面之上的悬浮窗, 因此示例不需要账号即可在
 * 任何设备上运行; 换成真实应用的选择器即可. 同步函数同时存在于 automator 模块与全局; Flow 步骤以异步方式做同样的事.
 */

"use strict";

auto.waitFor();

/*
 * The demo form. A floating window is only the active window (and thus visible to the
 * accessibility service) over the launcher on some systems, so the sample goes home first.
 * zh-CN: 演示用表单. 某些系统上悬浮窗只有在桌面之上才是活动窗口 (无障碍服务才看得到), 因此示例先回到桌面.
 */
home();
sleep(1500);
const form = floaty.rawWindow(
    <frame bg="#ffffff" padding="16">
        <vertical>
            <text text="Demo login" textSize="18sp" textColor="#000000"/>
            <input id="user" hint="Username" w="240dp"/>
            <input id="pass" hint="Password" inputType="textPassword" w="240dp"/>
            <checkbox id="remember" text="Remember me"/>
            <button id="signIn" text="Sign in"/>
            <text id="status" text="" textColor="#000000"/>
        </vertical>
    </frame>
);
/* Views are touched on the UI thread only (listeners included). zh-CN: 只在 UI 线程上碰视图 (包括监听). */
ui.run(() => {
    form.setSize(-2, -2);
    form.setPosition(80, 300);
    form.signIn.click(() => form.status.setText("Welcome, " + form.user.getText() + (form.remember.isChecked() ? " (remembered)" : "")));
});
/* Focus once the window has settled: it makes the window the active one for the accessibility service. zh-CN: 窗口安顿后再请求焦点: 这使它成为无障碍服务眼中的活动窗口. */
sleep(300);
ui.run(() => form.requestFocus());
sleep(1500);

/* 1. The user name: the first editable field of the window. zh-CN: 用户名: 窗口里第一个可编辑控件. */
const userField = typeInto(0, "alice", { timeout: 3000 });
console.log("1. Username typed:", userField.text());

/* 2. The password by selector; a password field reports dots, so verify is off. zh-CN: 密码按选择器; 密码框上报的是圆点, 因此关闭校验. */
typeInto(className("android.widget.EditText").depth(userField.depth()).indexInParent(2), "s3cret", { verify: false, timeout: 3000 });
console.log("2. Password typed; the field reports", JSON.stringify(pickup(className("android.widget.EditText").indexInParent(2)).text()));

/* 3. Appending instead of replacing. zh-CN: 追加而非替换. */
console.log("3. Appended:", typeInto(0, "@example.com", { clear: false }).text());

/* 4. The check box set to checked (nothing happens if it already is). zh-CN: 复选框设为选中 (已选中则什么都不做). */
const toggled = toggle("Remember me", true, { timeout: 2000 });
console.log("4. Remember me:", toggled.checked, toggled.changed ? "(clicked)" : "(already)", "again ->", toggle("Remember me", true).changed ? "changed" : "unchanged");

/* 5. Submit: the button by text (buttons may report it in capitals); smartClick climbs to a clickable ancestor when the text itself is not clickable. zh-CN: 提交: 按文本找按钮 (按钮上报的文本可能是大写); 文本本身不可点击时 smartClick 上溯到可点击祖先. */
const signIn = /^(Sign in|SIGN IN)$/;
const clicked = smartClick(signIn, { timeout: 2000 });
console.log("5. Sign in clicked by", clicked.method, "-", waitAsync(textStartsWith("Welcome"), 3000).sync().text());

/* 6. Errors are FlowError objects: a missing field. zh-CN: 错误是 FlowError 对象: 找不到字段. */
try {
    typeInto("__no_such_field__", "x", { timeout: 300 });
} catch (e) {
    console.log("6. " + e.name + " " + e.code + " in " + e.step + " after " + e.elapsed + " ms:", e.message);
}

/* 7. The same as a Flow chain: locate, type, toggle, click, without blocking until sync(). zh-CN: 同样的事写成 Flow 链: 定位, 输入, 切换, 点击, 直到 sync() 才阻塞. */
ui.run(() => form.status.setText(""));
waitAsync(className("android.widget.EditText"), 3000)
    .typeInto("bob")
    .then(field => console.log("7. Flow typed:", field.text()))
    .then(() => flow.toggle("Remember me", false))
    .then(r => console.log("7. Flow toggled off:", r.changed))
    .then(() => flow.smartClick(signIn))
    .wait(textStartsWith("Welcome"), 3000)
    .then(status => console.log("7. Flow result:", status.text()))
    .catch(e => console.error("7. Flow failed:", e))
    .sync();

ui.run(() => form.close());
console.log("Done.");
