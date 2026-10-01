/**
 * 设为默认安装器 [v6.8.0+]
 *
 * 需要在插件中心安装, 启用并授权 3-Setup Installer.
 * 默认只查询状态. 确认希望修改系统默认安装器后, 自行将 applyChange 改为 true.
 * 设置操作需要已授权的 Shizuku 或 Root; 本示例不自动请求授权, 不清除默认项.
 * 某些系统已有其他默认安装器时, 需要用户先在系统设置中清除原默认项.
 */

let applyChange = false;
let authorizer = 'shizuku'; // 也可改为已经授权的 'root'.

main();

function main() {
    if (typeof installer === 'undefined') {
        toastLog('此示例需要支持 installer 模块的 AutoJs6 6.8.0 或更高版本');
        return;
    }
    try {
        if (!installer.isAvailable()) {
            toastLog('3-Setup Installer 不可用: ' + (installer.status.reason || '请检查插件中心'));
            return;
        }
        console.log('插件:', installer.status);
        let isDefault = installer.isDefault();
        console.log('3-Setup Installer 当前是否为默认安装器:', isDefault);
        if (isDefault) {
            toastLog('3-Setup Installer 已是默认安装器');
            return;
        }
        if (!applyChange) {
            toastLog('只读查询完成; 确认需要修改后, 请自行将 applyChange 改为 true');
            return;
        }
        let state = installer.authorizer.state(authorizer);
        if (!state.available || !state.running || !state.granted) {
            toastLog(authorizer + ' 尚未就绪, 请自行启动服务并完成授权后重试');
            console.log(state);
            return;
        }
        installer.setDefaultAsync(true, { authorizer: authorizer }).then(reached => {
            if (reached) {
                toastLog('已设为默认安装器');
            } else {
                toastLog('未达到所请求的默认状态, 请检查系统的默认应用设置');
            }
        }).catch(reportError);
    } catch (error) {
        if (!(error instanceof installer.InstallerError)) throw error;
        reportError(error);
    }
}

function reportError(error) {
    toastLog(error.toString());
    if (error.systemMessage) console.error(error.systemMessage);
}
