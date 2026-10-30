/**
 * 语种识别 [v6.8.0+]
 *
 * speech.detectLanguage 由 3-Macaw Speech 插件 1.3.0 及以上提供: 先在插件首页下载一个语种识别模型, 再下载一个合成模型, 示例用它
 * 合成中文与英文各一句代替真人录音 (合成模型只会一种语言时只合成那一种).
 * 插件先找出录音里有人说话的部分, 切成至多 30 秒的窗, 对前几个窗 (windows, 默认 3, 至多 60) 逐句判断 (插件 1.4.0 起; 1.3.0 每个窗判一种):
 * language 为说话时长最长的语种 (whisper 的两字母代码, 如 zh, en, ja), candidates 为各语种的时长占比, segments 为每句的结果.
 * 换成自己的录音 (WAV, MP3 等) 时, 可选参数 (engines.execScriptFile 的 arguments): file 为录音路径, windows 为判断的窗数,
 * output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let dir = files.join(files.getSdcardPath(), 'Download', 'macaw-language');

if (!speech.isAvailable()) {
    toastLog('3-Macaw Speech 插件不可用, 请在插件中心安装或启用后重试');
    exit();
}
if (speech.capabilities().features.indexOf('languageId') < 0) {
    toastLog('语种识别需要 3-Macaw Speech 1.3.0 及以上');
    exit();
}

/* 准备录音: 有参数时用自己的录音, 否则用合成模型各合成一句. */
let samples = [], generated = false;
if (args.file) {
    samples.push({ name: files.getName(String(args.file)), path: String(args.file) });
} else {
    let models = speech.models.list({ kind: 'tts', installed: true });
    let model = models.filter((m) => m.languages.indexOf('zh') >= 0 && m.languages.indexOf('en') >= 0)[0] || models[0];
    if (!model) {
        toastLog('请先在 3-Macaw Speech 首页下载一个合成模型, 或用 file 参数传入自己的录音');
        exit();
    }
    /* Kokoro multi-lang v1.1 的说话人 3 说中文, 0 说英文; 其他模型用默认说话人. */
    let kokoro = (model.speakers || 1) > 100;
    let lines = [
        { language: 'zh', text: '今天的天气很好, 我们一起去公园散步吧.', speaker: kokoro ? '3' : undefined },
        { language: 'en', text: 'The weather is lovely today, let us take a walk in the park.', speaker: kokoro ? '0' : undefined },
    ].filter((line) => model.languages.indexOf(line.language) >= 0);
    console.log('用合成模型生成录音:', model.name);
    lines.forEach((line) => {
        let path = files.join(dir, line.language + '.wav');
        files.createWithDirs(path);
        let options = { model: model.id };
        if (line.speaker !== undefined) options.speaker = line.speaker;
        samples.push({ name: line.language + '.wav', path: speech.synthesize(line.text, path, options).path });
    });
    generated = true;
}

let summary = {};
try {
    samples.forEach((sample) => {
        /* 长录音多判几窗更稳, 但每句都要判断一次, 窗数越多越慢; detectLanguageAsync 返回 Promise. */
        let options = {};
        if (args.windows) options.windows = Number(args.windows);
        let result = speech.detectLanguage(sample.path, options);
        let votes = result.candidates.map((c) => c.language + ' ' + Math.round(c.score * 100) + '%').join(', ');
        console.log(sample.name + ':', result.language, '| 时长占比', votes, '|', result.segments.length, '句 | 模型', result.modelId);
        summary[sample.name] = result.language;
    });
} catch (e) {
    let hint = {
        MODEL_MISSING: '请先在 3-Macaw Speech 首页下载一个语种识别模型',
        AUDIO_TOO_SHORT: '录音里没有找到人声',
        PLUGIN_INCOMPATIBLE: '语种识别需要 3-Macaw Speech 1.3.0 及以上',
    }[e.code];
    toastLog(hint || '出错 [' + e.code + ']: ' + e.message);
    summary.error = e.code;
} finally {
    if (generated) files.removeDir(dir);
}

toastLog(summary.error ? '示例未完成' : '语种识别示例完成');
if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
