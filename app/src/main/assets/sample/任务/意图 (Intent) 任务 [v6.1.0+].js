tasks.addIntentTask({
    action: 'android.intent.action.SCREEN_OFF',
    path: files.path('./test.js'),
    callback(task) {
        console.log(`已添加的意图任务: ${task}`);
        console.log(`ID: ${task.id}`);
        console.log(`意图动作: ${task.action}`);
        console.log(`脚本路径: ${task.scriptPath}`);
        console.info('可在 AutoJs6 任务面板查看或管理任务');
    }
});

console.launch();