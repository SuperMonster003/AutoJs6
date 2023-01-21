/**
 * @type {string}
 */
let appName = dialogs.rawInput('请输入要卸载的应用名称');
let packageName = app.getPackageName(appName);
if (!packageName) {
    toast(`应用 "${appName}" 不存在`);
} else {
    app.uninstall(packageName);
}