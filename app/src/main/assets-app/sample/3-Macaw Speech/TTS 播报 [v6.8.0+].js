/**
 * TTS 播报 [v6.8.0+]
 *
 * speech 模块由 3-Macaw Speech 插件提供: 先在插件中心安装并启用, 再在插件首页下载至少一个合成模型.
 * 演示三种用法:
 * 1. speech.synthesize 把文本合成为 WAV 文件 (可以指定模型, 说话人与语速), 再用 media 播放;
 * 2. speech.speak 经系统 TTS 引擎直接朗读, 未指定说话人时按文本的语言选择;
 * 3. tts 模块以 engine 选项指定 3-Macaw Speech, 不必修改系统的首选引擎.
 * 可选参数 (engines.execScriptFile 的 arguments): file 为合成的 WAV 文件, output 为写入结果摘要 (JSON) 的文件.
 */

const ENGINE = 'io.github.supermonster003.autojs6.plugin.three.macaw.speech';
let args = engines.myEngine().execArgv;
let path = args.file ? String(args.file) : files.join(files.getSdcardPath(), 'Download', 'macaw-tts.wav');

if (!speech.isAvailable()) {
    toastLog('3-Macaw Speech 插件不可用, 请在插件中心安装或启用后重试');
    exit();
}

let installed = speech.models.list({ kind: 'tts', installed: true });
if (installed.length === 0) {
    toastLog('请先在 3-Macaw Speech 首页下载一个合成模型');
    exit();
}
let model = speech.models.getDefault('tts') || installed[0];
let chinese = model.languages.indexOf('zh') >= 0;
let pick = (zh, en) => chinese ? zh : en;
console.log('合成模型:', model.name, '(' + model.id + ')', '| 语言:', model.languages.join(', '));

/* 1. 合成到文件: 返回时长, 采样率与实际使用的说话人; 只有一个说话人的模型会忽略 speaker, 并在 warnings 中说明. */
files.createWithDirs(path);
let result = speech.synthesize(pick('今天的任务已经完成, 共处理三个文件.', "Today's task is done, three files were processed."), path, {
    model: model.id,
    speed: 1.1
});
console.log('已合成:', result.path, '| 时长', result.durationMillis, 'ms | 采样率', result.sampleRate, 'Hz | 说话人', result.speakerId);
result.warnings.forEach((w) => console.warn('提示 [' + w.code + ']: ' + w.message));

media.playMusic(result.path);
sleep(result.durationMillis + 500);
media.stopMusic();

/* 2. speech.speak 与 3. tts: 都返回 Promise, 朗读结束后兑现. */
let spoken = {};
speech.speak(pick('这是 3-Macaw Speech 的朗读.', 'This is 3-Macaw Speech speaking.'), { model: model.id })
    .then((voice) => {
        spoken.speak = voice.voice;
        console.log('speech.speak 使用的语音:', voice.voice);
        return tts(pick('播报结束.', 'That is all.'), { engine: ENGINE });
    })
    .then((voice) => {
        spoken.tts = voice.voice;
        console.log('tts 使用的语音:', voice.voice);
    })
    .catch((e) => {
        spoken.error = String(e.code || '') + ' ' + e.message;
        console.error('朗读失败 [' + e.code + ']: ' + e.message);
    })
    .then(() => {
        if (args.output) {
            files.write(String(args.output), JSON.stringify({
                model: result.modelId, durationMillis: result.durationMillis, sampleRate: result.sampleRate,
                sizeBytes: result.sizeBytes, warnings: result.warnings.length, speak: spoken.speak, tts: spoken.tts, error: spoken.error
            }));
        }
    });
