/**
 * 音频标签 [v6.8.0+]
 *
 * speech.tag 由 3-Macaw Speech 插件 1.3.0 及以上提供: 先在插件首页下载一个音频标签模型. 它听出录音里有哪些声音 (说话, 音乐,
 * 狗叫, 雨声, 汽车等 527 种 AudioSet 标签, 英文名称), 适合给录音归类或检查环境声音.
 * 插件把录音切成 10 秒的窗逐一打分: labels 为分数最高的 topK 个标签 (默认 5, 至多 20), 各带分数与得分最高的那个窗的起止毫秒;
 * timeline: true 时另给每个窗的前 topK 个标签.
 * 默认用合成模型合成一句话来演示 (结果应以 Speech 为主); 换成自己的录音 (WAV, MP3, 视频的音轨等, 至多 30 分钟) 时,
 * 可选参数 (engines.execScriptFile 的 arguments): file 为录音路径, output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let dir = files.join(files.getSdcardPath(), 'Download', 'macaw-tag');

if (!speech.isAvailable()) {
    toastLog('3-Macaw Speech 插件不可用, 请在插件中心安装或启用后重试');
    exit();
}
if (speech.capabilities().features.indexOf('audioTagging') < 0) {
    toastLog('音频标签需要 3-Macaw Speech 1.3.0 及以上');
    exit();
}

let file, generated = false;
if (args.file) {
    file = String(args.file);
} else {
    let model = speech.models.list({ kind: 'tts', installed: true })[0];
    if (!model) {
        toastLog('请先在 3-Macaw Speech 首页下载一个合成模型, 或用 file 参数传入自己的录音');
        exit();
    }
    let text = model.languages.indexOf('zh') >= 0 ? '这是一段用来测试音频标签的语音.' : 'This is a short recording to try audio tagging.';
    file = files.join(dir, 'speech.wav');
    files.createWithDirs(file);
    speech.synthesize(text, file, { model: model.id });
    generated = true;
}

let summary = {};
try {
    /* 1. 整段的标签: 分数在 0 - 1 之间, 各标签互不排斥 (有人说话的音乐会同时得到 Speech 与 Music). */
    let result = speech.tag(file, { topK: 5 });
    console.log('时长', result.durationMillis, 'ms | 模型', result.modelId);
    result.labels.forEach((l) => {
        console.log('  ' + l.label + ' (' + l.mid + '): ' + l.score + ' | 最明显在 ' + (l.startMillis / 1000) + ' - ' + (l.endMillis / 1000) + ' 秒');
    });
    summary.top = result.labels.length ? result.labels[0].label : null;

    /* 2. 时间线: 每 10 秒一行, 适合找出某种声音出现在哪几段; tagAsync 返回 Promise. */
    let timeline = speech.tag(file, { topK: 2, timeline: true }).timeline;
    timeline.forEach((w) => {
        console.log('  ' + (w.startMillis / 1000) + ' 秒起:', w.labels.map((l) => l.label + ' ' + l.score).join(', '));
    });
    summary.windows = timeline.length;
} catch (e) {
    let hint = {
        MODEL_MISSING: '请先在 3-Macaw Speech 首页下载一个音频标签模型',
        PLUGIN_INCOMPATIBLE: '音频标签需要 3-Macaw Speech 1.3.0 及以上',
    }[e.code];
    toastLog(hint || '出错 [' + e.code + ']: ' + e.message);
    summary.error = e.code;
} finally {
    if (generated) files.removeDir(dir);
}

toastLog(summary.error ? '示例未完成' : '音频标签示例完成');
if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
