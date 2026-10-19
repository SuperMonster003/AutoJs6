/**
 * 唤醒词与听写 [v6.8.0+]
 *
 * speech 模块由 3-Macaw Speech 插件提供: 先在插件中心安装并启用, 再打开插件首页授予麦克风权限,
 * 下载唤醒词模型与一个识别模型.
 * speech.wake 持续等待唤醒词; 每次唤醒后, thenListen 在同一段录音上听写一句话, 然后继续等待下一次唤醒.
 * 关键词直接写原文 (中文按常用读音, 英文单词须在模型词典中), 可以同时监听多个;
 * 也可以写成 { keyword, threshold, boost }, threshold 越低越容易唤醒.
 * 默认监听 "小六小六" 与 "hey macaw", 听写 3 轮或 3 分钟后结束.
 * 可选参数 (engines.execScriptFile 的 arguments): keyword 更换唤醒词, rounds 为轮数, output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let keywords = args.keyword ? [String(args.keyword)] : ['小六小六', 'hey macaw'];
let maxRounds = Number(args.rounds) || 3;

if (!speech.isAvailable()) {
    toastLog('3-Macaw Speech 插件不可用, 请在插件中心安装或启用后重试');
    exit();
}
if (!speech.capabilities().microphoneGranted) {
    toastLog('3-Macaw Speech 尚未获得麦克风权限, 请打开插件首页授予后重试');
    exit();
}

let rounds = [];
let wake;
try {
    /* thenListen 可以是 true, 也可以是 speech.listen 的选项: 这里每轮停顿 1.2 秒算说完, 6 秒没开口就放弃这一轮. */
    wake = speech.wake(keywords, { thenListen: { silenceEndMillis: 1200, timeoutMillis: 6000 } });
} catch (e) {
    toastLog(e.code === 'MODEL_MISSING' ? '请先在 3-Macaw Speech 首页下载唤醒词模型与识别模型' : '无法开始监听 [' + e.code + ']: ' + e.message);
    exit();
}

wake.on('ready', () => toastLog('请说唤醒词: ' + keywords.join(' / ')));

/* wake: 命中一个关键词; e.listen 是这一轮听写的会话, 它的 stop() / cancel() 只结束这一轮. */
wake.on('wake', (e) => {
    let index = rounds.length + 1;
    console.log('第 ' + index + ' 次唤醒:', e.keyword);
    toast('请说话');
    let text = '';
    e.listen.on('partial', (p) => console.log('...', p.text));
    e.listen.on('final', (f) => { text += f.text; });
    e.listen.on('end', (end) => {
        console.log('听到:', text || '(没有听到内容)', '| 本轮结束原因:', end.reason);
        rounds.push({ keyword: e.keyword, chars: text.length, reason: end.reason });
        if (rounds.length >= maxRounds) wake.stop();
    });
});

wake.on('warning', (e) => console.warn('提示 [' + e.code + ']: ' + e.message));
wake.on('error', (e) => console.error('出错 [' + e.code + ']: ' + e.message));
wake.on('end', (e) => {
    clearTimeout(timer);
    console.log('唤醒词会话结束:', e.reason, '| 共 ' + rounds.length + ' 轮');
    if (args.output) files.write(String(args.output), JSON.stringify({ reason: e.reason, rounds: rounds }));
});

/* 唤醒词会话会一直占用麦克风: 3 分钟后主动结束, 释放给其他应用. */
let timer = setTimeout(() => {
    if (wake.isActive()) wake.stop();
}, 3 * 60 * 1000);
