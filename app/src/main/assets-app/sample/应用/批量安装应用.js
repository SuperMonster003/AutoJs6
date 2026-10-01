/**
 * 批量安装应用 [v6.8.0+]
 *
 * 需要在插件中心安装, 启用并授权 3-Setup Installer.
 * 将 sources 中的空字符串替换为自己选择的本地安装包路径, 最多 32 个应用.
 * 顶层数组表示多个独立应用; 同一应用的分包使用 { splits: [路径1, 路径2] }.
 * 本示例显式使用 dialog, 安装前在插件界面确认; 不删除来源, 不卸载应用.
 */

let sources = ['', ''];

main();

function main() {
    if (!Array.isArray(sources) || sources.length < 1 || sources.length > 32) {
        toastLog('请将 sources 设置为包含 1 至 32 个应用的数组');
        return;
    }
    for (let index = 0; index < sources.length; index += 1) {
        let source = sources[index];
        let paths = typeof source === 'string' ? [source] : source && source.splits;
        if (!Array.isArray(paths) || paths.length < 1 || paths.length > 64) {
            toastLog('第 ' + (index + 1) + ' 项需要一个路径或包含 1 至 64 个路径的 splits');
            return;
        }
        for (let part = 0; part < paths.length; part += 1) {
            let path = paths[part];
            if (typeof path !== 'string' || !path.trim()) {
                toastLog('请先编辑 sources, 填写自己选择的安装包或分包路径');
                return;
            }
            path = files.path(path);
            if (!files.exists(path) || !files.isFile(path)) {
                toastLog('安装包不存在或不是文件: ' + path);
                return;
            }
        }
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
        let session = installer.session(sources, {
            authorizer: 'auto',
            interaction: 'dialog',
            continueOnError: true,
            deleteSource: false,
            timeout: 30 * 60 * 1000,
        });
        console.log('会话:', session.id);
        session.on('stage', (stage, detail) => {
            console.log('阶段:', stage, '批量索引:', detail.index, detail.packageName || '');
        }).on('progress', (progress, detail) => {
            console.log('进度:', Math.round(progress * 100) + '%', detail.bytesWritten, '/', detail.totalBytes);
        }).on('complete', results => {
            // 即使 sources 只有一项, results 仍是数组. 完成不等于每项都成功.
            results.forEach((result, index) => {
                console.log('第 ' + (index + 1) + ' 项:', result.ok, result.packageName || '(未解析)');
                if (result.error) reportError(result.error);
                if (result.notes) console.log(result.notes);
            });
            toastLog('批量任务已结束, 请查看逐项结果; 源文件保留');
        }).on('error', reportError).on('cancel', () => {
            toastLog('会话已取消; 已完成的安装不会回滚');
        });
        // 会话会保持脚本运行到终态. 用户停止脚本时会取消尚未完成的会话.
        // 如需同步取得结果可调用 session.wait(); 短 wait 超时只结束本次等待.
    } catch (error) {
        if (!(error instanceof installer.InstallerError)) throw error;
        reportError(error);
    }
}

function reportError(error) {
    console.error(error.toString());
    if (error.systemMessage) console.error(error.systemMessage);
}
