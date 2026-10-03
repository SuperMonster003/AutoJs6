// 自动回答一个 shell 提示, 等待输出, 再接收退出事件.
(function () {
    if (!terminal.isAvailable()) {
        toastLog('请先在 AutoJs6 插件中心安装并启用 3-Shell Terminal');
        return;
    }
    let session;
    try {
        session = terminal.exec('sleep 1; printf "name? "; read name; echo "received:$name"; sleep 1; exit 3', {
            cwd: files.cwd(), show: true, keepOpen: false,
        });
        session.on('output', function (line) {
            console.log('output: ' + line);
            if (line.indexOf('name?') >= 0) session.write('AutoJs6\n');
        });
        session.on('overflow', function (bytes) {
            console.warn('输出读取不及时, 丢弃了 ' + bytes + ' 字节');
        });
        session.once('exit', function (code) {
            toastLog('终端会话退出码: ' + code);
        });
        // 同步等待期间, 上面的 output 监听器仍在脚本线程处理提示并写入回答.
        let matched = session.waitFor(/received:AutoJs6/, 15000);
        console.log('匹配输出: ' + matched);
    } catch (e) {
        if (session) session.removeAllListeners();
        toastLog('会话驱动失败 [' + e.code + ']: ' + e.message);
    }
})();
