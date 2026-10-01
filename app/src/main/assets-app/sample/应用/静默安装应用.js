/**
 * 静默安装应用 [v6.8.0+]
 *
 * 需要在插件中心安装, 启用并授权 3-Setup Installer.
 * 先填写自己确认可安装的本地文件路径, 再选择已授权的 shizuku 或 root.
 * 本示例不自动请求特权授权, 不允许降级, 不删除来源, 不卸载应用.
 * interaction: 'silent' 不会回退为确认对话框; 系统要求确认时报告错误.
 */

let packagePath = ''; // 填写自己的 APK 或可安装容器路径, 不支持直接安装 AAB.
let authorizer = 'shizuku'; // 也可改为已经授权的 'root'.

main();

function main() {
    if (!packagePath.trim()) {
        toastLog('请先编辑本示例, 将 packagePath 替换为自己选择的安装包路径');
        return;
    }
    let path = files.path(packagePath);
    if (!files.exists(path) || !files.isFile(path)) {
        toastLog('安装包不存在或不是文件: ' + path);
        return;
    }
    if (typeof installer === 'undefined') {
        toastLog('此示例需要支持 installer 模块的 AutoJs6 6.8.0 或更高版本');
        return;
    }
    try {
        if (!installer.isAvailable()) {
            toastLog('3-Setup Installer 不可用: ' + (installer.status.reason || '请检查插件中心'));
            return;
        }
        let state = installer.authorizer.state(authorizer);
        if (!state.available || !state.running || !state.granted) {
            toastLog(authorizer + ' 尚未就绪, 请自行启动服务并完成授权后重试');
            console.log(state);
            return;
        }
        let info = installer.inspect(path);
        console.log('安装包:', info.packageName, info.versionName, info.format);
        if (!info.installable) {
            toastLog('此来源不能直接安装: ' + info.format);
            console.log(info.problems || []);
            return;
        }
        let result = installer.install(path, {
            authorizer: authorizer,
            interaction: 'silent',
            deleteSource: false,
            timeout: 10 * 60 * 1000,
        });
        console.log('安装结果:', result.ok, result.packageName, result.authorizer, result.interaction);
        console.log('说明:', result.notes || []);
        toastLog('安装已完成, 源文件保留');
    } catch (error) {
        if (!(error instanceof installer.InstallerError)) throw error;
        toastLog(error.toString());
        if (error.systemMessage) console.error(error.systemMessage);
    }
}
