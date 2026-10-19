/**
 * 语音命令交给 Agent [v6.8.0+]
 *
 * 需要两个插件:
 * - 3-Macaw Speech (speech 模块): 在插件首页授予麦克风权限, 下载唤醒词模型与一个识别模型;
 * - 3-Stove Agent (ai.agent 模块): 在 3-Stone AI 中配置好默认模型, 或用 arguments 的 target 指定.
 * 流程: speech.wake 等待唤醒词 -> 唤醒后在同一段录音上听一句命令 (thenListen) -> ai.agent.run 执行 -> speech.speak 朗读结果摘要.
 * Agent 会读取这台手机的界面: 本示例只开放 observe 工具组, 最多 6 步; 需要它代为操作时再按需加入其他工具组.
 * 说出命令前想清楚: 识别错的字也会原样交给 Agent. 说 "退出" 或 3 分钟后结束.
 * 可选参数 (engines.execScriptFile 的 arguments): keyword 更换唤醒词 (默认 "小六小六"), target 指定 Agent 的模型,
 * rounds 为最多处理的命令数, output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let keyword = args.keyword ? String(args.keyword) : '小六小六';
let maxCommands = Number(args.rounds) || 5;
let options = { tools: ['observe'], budget: { maxSteps: 6 } };
if (args.target) options.target = String(args.target);

if (!speech.isAvailable()) {
    toastLog('3-Macaw Speech 插件不可用, 请在插件中心安装或启用后重试');
    exit();
}
if (!speech.capabilities().microphoneGranted) {
    toastLog('3-Macaw Speech 尚未获得麦克风权限, 请打开插件首页授予后重试');
    exit();
}

let handled = [];
let busy = false;
let wake;
try {
    wake = speech.wake([keyword], { thenListen: { silenceEndMillis: 1200, timeoutMillis: 8000 } });
} catch (e) {
    toastLog(e.code === 'MODEL_MISSING' ? '请先在 3-Macaw Speech 首页下载唤醒词模型与识别模型' : '无法开始监听 [' + e.code + ']: ' + e.message);
    exit();
}

wake.on('ready', () => toastLog('请说 "' + keyword + '", 再说出命令'));
wake.on('wake', (e) => {
    let command = '';
    e.listen.on('final', (f) => { command += f.text; });
    e.listen.on('end', () => {
        command = command.trim();
        if (!command) {
            toast('没有听到命令');
        } else if (/^(退出|结束|exit|stop)/i.test(command)) {
            wake.stop();
        } else if (busy) {
            toast('上一条命令还在执行');
        } else {
            handOver(command);
        }
    });
});
wake.on('error', (e) => console.error('唤醒词会话出错 [' + e.code + ']: ' + e.message));
wake.on('end', (e) => {
    clearTimeout(timer);
    console.log('唤醒词会话结束:', e.reason);
});

function handOver(command) {
    busy = true;
    console.log('命令:', command);
    let record = { chars: command.length };
    handled.push(record);
    let run;
    try {
        run = ai.agent.run(command, options);
    } catch (e) {
        finish(record, null, e);
        return;
    }
    run.on('progress', (event) => console.log(event.message));
    run.result.then((result) => finish(record, result, null), (error) => finish(record, null, error));
}

function finish(record, result, error) {
    if (result) {
        /* 兑现的结果也可能是 partial, failed, blocked 或 cancelled, 以 status 为准. */
        record.status = result.status;
        console.log('Agent:', result.status, result.summary);
    } else {
        record.status = 'error';
        record.code = String(error.code || '');
        console.error('Agent 出错', error.code, error.message, error.hint || '');
    }
    let text = result ? String(result.summary || result.status) : '命令没有完成';
    speech.speak(text.length > 500 ? text.slice(0, 500) : text)
        .then((voice) => { record.spoken = voice.voice; }, (e) => { record.spoken = 'error ' + e.code; })
        .then(() => {
            busy = false;
            if (args.output) files.write(String(args.output), JSON.stringify({ keyword: keyword, commands: handled }));
            if (handled.length >= maxCommands) wake.stop();
        });
}

let timer = setTimeout(() => {
    if (wake.isActive()) wake.stop();
}, 3 * 60 * 1000);
