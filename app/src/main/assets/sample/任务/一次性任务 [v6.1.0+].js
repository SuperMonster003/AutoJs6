tasks.addDisposableTask({
    date: Date.now() + 3.6e6 * 12,
    path: files.path('./test.js'),
    callback(task) {
        console.log(`已添加的一次性任务: ${task}`);
        console.log(`ID: ${task.id}`);
        console.log(`运行时间: ${new Date(task.nextTime).toLocaleString()}`);
        console.log(`脚本路径: ${task.scriptPath}`);
        console.info('可在 AutoJs6 任务面板查看或管理任务');
    },
});

console.launch();