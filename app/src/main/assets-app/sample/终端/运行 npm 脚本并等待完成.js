// 将本示例放到含 package.json 的项目目录, 按需修改下面的 npm 脚本名.
// 需要 3-Shell Terminal 和 Node.js Runtime 插件; 无需安装任何额外依赖即可调用已有脚本.
(function () {
    if (!terminal.isAvailable()) {
        toastLog('请先在 AutoJs6 插件中心安装并启用 3-Shell Terminal');
        return;
    }
    try {
        if (!terminal.env().node.available) {
            toastLog('请安装并启用 Node.js Runtime, 并检查终端的 Node.js 集成设置');
            return;
        }
        let session = terminal.npm.run('build', files.cwd(), {
            show: true,
            keepOpen: false, // 命令结束即退出会话, wait 才能取得该命令的退出码.
            wait: true,
            timeout: 600000, // 毫秒; 0 表示不限. 超时不会关闭仍在运行的会话.
        });
        toastLog('npm run build 退出码: ' + session.exitCode);
    } catch (e) {
        toastLog('运行 npm 脚本失败 [' + e.code + ']: ' + e.message);
    }
})();
