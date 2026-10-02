// 需要安装并启用 3-Shell Terminal 插件.
// 脚本位于共享存储时, 还需为插件授予 "所有文件访问权限".
(function () {
    if (!terminal.isAvailable()) {
        toastLog('请先在 AutoJs6 插件中心安装并启用 3-Shell Terminal');
        return;
    }
    try {
        let session = terminal.open({cwd: files.cwd(), show: false});
        session.show();
        toastLog('已打开终端会话 ' + session.id + ': ' + session.cwd);
        // 脚本结束或离开终端界面后, 此会话仍会继续运行.
    } catch (e) {
        toastLog('打开终端失败 [' + e.code + ']: ' + e.message);
    }
})();
