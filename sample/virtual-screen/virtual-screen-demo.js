/**
 * 虚拟屏幕执行能力 — 显式 JS 模块验证脚本（高级用法）
 *
 * 常规用法不需要写 virtualScreen.* : 在菜单或设置里配置为「在虚拟屏运行」,
 * 存量脚本一行不改即可整体切到虚拟屏, 见 zero-change-demo.js 与 docs/virtual-screen/README.md.
 * 本文件用于需要自己建屏/管理多块屏/读取 flags 的场景.
 *
 * 运行前: 启动 Shizuku 并授权 AutoJs6; 推荐 Android 14+
 * 参见 docs/virtual-screen/README.md 的真机验证清单
 */

'ui';

ui.layout(
    <vertical padding="16">
        <text text="虚拟屏幕执行能力验证" textSize="20sp" gravity="center" />
        <text id="info" text="检查环境中..." textSize="13sp" margin="0 12" />
        <button id="create" text="1. 创建虚拟屏" />
        <button id="launch" text="2. 在虚拟屏启动设置" />
        <button id="capture" text="3. 截图到下载目录" />
        <button id="tap" text="4. 点击 / 滑动 / 返回" />
        <button id="release" text="5. 释放虚拟屏" />
        <button id="state" text="查看环境诊断" />
    </vertical>,
);

let screen = null;

function describe() {
    let s = virtualScreen.state();
    return [
        `Shizuku 可用: ${s.available}`,
        `Android API: ${s.sdkInt} (独立焦点需 ${s.ownFocusSdkInt}+)`,
        `独立焦点: ${s.ownFocusSupported}`,
        `存活虚拟屏: ${s.screenCount} ${JSON.stringify(Array.from(s.displayIds || []))}`,
        `当前会话: ${screen ? screen.getDisplayId() : '无'}`,
    ].join('\n');
}

function ensureScreen() {
    if (!screen || screen.isReleased()) {
        screen = virtualScreen.create({ width: 1080, height: 1920, dpi: 480 });
        console.log('已创建:', screen.state());
    }
    return screen;
}

function show() {
    ui.run(() => (ui.info.setText(describe())));
}

ui.state.on('click', show);
show();

ui.create.on('click', () => {
    try {
        ensureScreen();
        toastLog('虚拟屏 displayId = ' + screen.getDisplayId() +
            ', ownFocus = ' + screen.hasOwnFocus() + ', trusted = ' + screen.isTrusted());
    } catch (e) {
        toastLog('创建失败: ' + e);
    }
    show();
});

ui.launch.on('click', () => {
    try {
        let code = ensureScreen().launch('com.android.settings');
        toastLog('启动返回码: ' + code + ' (0 = 成功)');
        sleep(1200);
    } catch (e) {
        toastLog('启动失败: ' + e);
    }
});

ui.capture.on('click', () => {
    try {
        let img = ensureScreen().capture();
        let path = '/sdcard/Download/autojs6-virtual-screen.png';
        img.saveTo(path);
        console.log('帧时间戳:', ensureScreen().getFrameTime());
        img.recycle();
        toastLog('已保存: ' + path);
    } catch (e) {
        toastLog('截图失败: ' + e);
    }
});

ui.tap.on('click', () => {
    try {
        let s = ensureScreen();
        console.log('tap:', s.tap(540, 900));
        sleep(300);
        console.log('swipe:', s.swipe(540, 1500, 540, 500, 600));
        sleep(300);
        console.log('key(BACK):', s.key(4));
        console.log('text:', s.text('hello'));
    } catch (e) {
        toastLog('操作失败: ' + e);
    }
});

ui.release.on('click', () => {
    if (screen) {
        toastLog('释放结果: ' + screen.release());
        screen = null;
    } else {
        toastLog('没有存活的虚拟屏');
    }
    show();
});

// 脚本退出时无需手动释放: 会话已登记到 ScriptRuntime.closeableManager.
