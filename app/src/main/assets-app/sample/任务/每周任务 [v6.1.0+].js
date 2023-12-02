tasks.addWeeklyTask({
    time: Date.now() + 3.6e6 * 12,
    path: files.path('./test.js'),
    daysOfWeek: ['六', 3, 'Fri', 'Sunday'],
    callback(task) {
        console.log(`已添加的每周任务: ${task}`);
        console.log(`ID: ${task.id}`);
        console.log(`运行时间: ${new Date(task.nextTime).toLocaleString()}`);
        console.log(`脚本路径: ${task.scriptPath}`);
        console.log(`周内日期: [ ${tasks.timeFlagToDays(task.timeFlag).join(', ')} ]`);
        console.info('可在 AutoJs6 任务面板查看或管理任务');
    },
});

console.launch();