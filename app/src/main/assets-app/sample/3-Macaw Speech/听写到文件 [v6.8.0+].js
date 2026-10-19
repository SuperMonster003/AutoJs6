/**
 * 听写到文件 [v6.8.0+]
 *
 * speech 模块由 3-Macaw Speech 插件提供: 先在插件中心安装并启用, 再打开插件首页授予麦克风权限并下载一个识别模型.
 * 本示例打开一次听写: 说话时在控制台显示中间结果, 每句话结束 (停顿 1.5 秒) 后追加到文件;
 * 连续 20 秒没有新内容或总共 60 秒后结束. 录音与识别都在插件内离线完成.
 * 可选参数 (engines.execScriptFile 的 arguments): file 为输出文件, output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let path = args.file ? String(args.file) : files.join(files.getSdcardPath(), 'Download', 'macaw-dictation.txt');

if (!speech.isAvailable()) {
    toastLog('3-Macaw Speech 插件不可用, 请在插件中心安装或启用后重试');
    exit();
}
if (!speech.capabilities().microphoneGranted) {
    toastLog('3-Macaw Speech 尚未获得麦克风权限, 请打开插件首页授予后重试');
    exit();
}

files.createWithDirs(path);
let sentences = 0;
let session;
try {
    /* silenceEndMillis: 停顿多久算一句话结束; timeoutMillis: 多久没有新内容就结束; maxDurationMillis: 最长时长. */
    session = speech.listen({ silenceEndMillis: 1500, timeoutMillis: 20000, maxDurationMillis: 60000 });
} catch (e) {
    toastLog(e.code === 'MODEL_MISSING' ? '请先在 3-Macaw Speech 首页下载一个识别模型' : '无法开始听写 [' + e.code + ']: ' + e.message);
    exit();
}

/* 1. ready: 模型已加载, 开始收音. */
session.on('ready', (e) => {
    console.log('开始听写, 模型:', e.modelId, '| 输出:', path);
    toast('请开始说话');
});

/* 2. partial: 当前这句话到目前为止的文字 (流式模型逐词更新, 离线模型按语音段更新). */
session.on('partial', (e) => console.log('...', e.text));

/* 3. final: 一句话结束, 追加到文件. */
session.on('final', (e) => {
    if (!e.text) return;
    files.append(path, e.text + '\n');
    sentences += 1;
    console.log('第 ' + sentences + ' 句:', e.text);
});

/* 4. warning: 不结束会话的提示, 例如 PERMISSION_DENIED 表示录音被系统静音 (AutoJs6 回到前台后恢复). */
session.on('warning', (e) => console.warn('提示 [' + e.code + ']: ' + e.message));
session.on('error', (e) => console.error('出错 [' + e.code + ']: ' + e.message));

/* 5. end: 唯一的结束事件, reason 为 stopped / timeout / maxDuration / cancelled / error. */
session.on('end', (e) => {
    console.log('听写结束, 原因:', e.reason, '| 共 ' + sentences + ' 句');
    toastLog(sentences > 0 ? '已写入 ' + path : '没有听到内容');
    if (args.output) {
        files.write(String(args.output), JSON.stringify({ reason: e.reason, sentences: sentences, chars: (e.text || '').length }));
    }
});
