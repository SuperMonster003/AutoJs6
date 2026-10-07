// 截取屏幕并提取主要颜色, 可将最主要的颜色设为 AutoJs6 的主题色.
// 主题色页面的菜单提供 "撤销脚本修改", 可恢复之前的选择.
(function () {
    if (!images.requestScreenCapture()) {
        toastLog('未获得截图权限');
        return;
    }
    toastLog('3 秒后截图, 可先切换到要取色的界面');
    sleep(3000);

    let dominant = colors.extract(images.captureScreen(), { count: 5 });
    dominant.forEach((color, i) => console.log(`${i + 1}. ${colors.toHex(color)}`));

    if (dominant.length > 0 && dialogs.confirm('设为主题色', `将 ${colors.toHex(dominant[0])} 设为主题色?`)) {
        colors.theme.set(dominant[0]);
        toastLog('主题色: ' + colors.theme.current().summary);
    }
})();
