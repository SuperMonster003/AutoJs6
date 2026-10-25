/**
 * 说话人识别 [v6.8.0+]
 *
 * speech.speaker 由 3-Macaw Speech 插件 1.3.0 及以上提供: 先在插件首页下载一个说话人模型, 再下载一个多说话人的合成模型
 * (例如 Kokoro), 示例用它合成两种不同的嗓音代替真人录音.
 * 演示四个步骤:
 * 1. enroll 用同一嗓音的两句话注册一位说话人 (多段录音取平均, replace 先清空同名的旧声纹);
 * 2. verify 判断另一句话是否出自这位说话人, 返回相似度分数, 阈值与是否通过;
 * 3. identify 在全部已注册的说话人中找出最像的一位, 都未过阈值时 name 为 null;
 * 4. list 列出已注册的说话人, remove 删除本示例注册的声纹.
 * 声纹只保存在插件内, 脚本只得到名字与分数. 换成自己的录音 (WAV, MP3 等, 每段 2 - 10 秒为宜) 时,
 * 可选参数 (engines.execScriptFile 的 arguments): enroll 为注册用的录音路径数组, test 为待验证的录音路径,
 * output 为写入结果摘要 (JSON) 的文件.
 */

const NAME = '示例说话人';
let args = engines.myEngine().execArgv;
let dir = files.join(files.getSdcardPath(), 'Download', 'macaw-speaker');

if (!speech.isAvailable()) {
    toastLog('3-Macaw Speech 插件不可用, 请在插件中心安装或启用后重试');
    exit();
}
if (!speech.speaker || speech.capabilities().features.indexOf('speaker') < 0) {
    toastLog('说话人识别需要 3-Macaw Speech 1.3.0 及以上');
    exit();
}

/* 准备录音: 有参数时用自己的录音, 否则用合成模型的两个说话人各合成几句话. */
let enrollFiles, ownTest, otherTest = null, generated = false;
if (args.enroll) {
    enrollFiles = [].concat(args.enroll).map(String);
    ownTest = String(args.test || enrollFiles[0]);
} else {
    let model = speech.models.list({ kind: 'tts', installed: true }).filter((m) => (m.speakers || 1) >= 2)[0];
    if (!model) {
        toastLog('请先在 3-Macaw Speech 首页下载一个多说话人的合成模型 (例如 Kokoro), 或用 enroll 参数传入自己的录音');
        exit();
    }
    let chinese = model.languages.indexOf('zh') >= 0;
    /* Kokoro multi-lang v1.1 的说话人 3 - 102 说中文, 0 - 2 说英文. */
    let voices = chinese ? ['3', '10'] : ['0', '1'];
    let lines = chinese
        ? ['今天的天气很好, 我们一起出去走走吧.', '请帮我打开客厅的灯, 再把空调调到二十六度.', '明天早上七点叫我起床, 别忘了带伞.']
        : ['The weather is lovely today, let us go for a walk.', 'Please turn on the living room light.', 'Wake me up at seven tomorrow morning.'];
    let make = (text, voice, file) => {
        let path = files.join(dir, file);
        files.createWithDirs(path);
        return speech.synthesize(text, path, { model: model.id, speaker: voice }).path;
    };
    console.log('用合成模型生成录音:', model.name, '| 说话人', voices.join(' / '));
    enrollFiles = [make(lines[0], voices[0], 'own-1.wav'), make(lines[1], voices[0], 'own-2.wav')];
    ownTest = make(lines[2], voices[0], 'own-3.wav');
    otherTest = make(lines[2], voices[1], 'other-1.wav');
    generated = true;
}

let summary = {};
try {
    /* 1. 注册: 返回累计样本数与本次用到的语音时长 (毫秒). */
    let enrolled = speech.speaker.enroll(NAME, enrollFiles, { replace: true });
    console.log('已注册:', enrolled.name, '| 样本', enrolled.samples, '| 语音', enrolled.durationMillis, 'ms | 模型', enrolled.modelId);
    summary.samples = enrolled.samples;

    /* 2. 验证: score 为余弦相似度, 不低于 threshold 即 matched; 可用 { threshold } 调严或放宽. */
    let own = speech.speaker.verify(NAME, ownTest);
    console.log('同一人的另一句话:', own.matched ? '通过' : '未通过', '| 分数', own.score, '| 阈值', own.threshold);
    summary.own = own.score;
    if (otherTest) {
        let other = speech.speaker.verify(NAME, otherTest);
        console.log('另一个人说的话:', other.matched ? '通过' : '未通过', '| 分数', other.score);
        summary.other = other.score;
    }

    /* 3. 辨认: candidates 按分数从高到低, 至多 topK 个. verifyAsync / identifyAsync 等返回 Promise, 适合在界面线程调用. */
    let who = speech.speaker.identify(otherTest || ownTest, { topK: 3 });
    console.log('辨认结果:', who.name === null ? '不是已注册的人' : who.name, '| 候选', JSON.stringify(who.candidates));
    summary.identified = who.name;

    /* 4. 列出已注册的说话人 (含在插件首页注册的). */
    console.log('已注册的说话人:', speech.speaker.list().map((s) => s.name + ' (' + s.samples + ' 段)').join(', '));
} catch (e) {
    let hint = {
        MODEL_MISSING: '请先在 3-Macaw Speech 首页下载一个说话人模型',
        AUDIO_TOO_SHORT: '录音中的有效语音不足 1 秒, 请换一段更长的录音',
        PLUGIN_INCOMPATIBLE: '说话人识别需要 3-Macaw Speech 1.3.0 及以上',
    }[e.code];
    toastLog(hint || '出错 [' + e.code + ']: ' + e.message);
    summary.error = e.code;
} finally {
    /* 删除本示例的声纹与合成的录音; 自己的说话人请在插件首页的说话人管理里管理. */
    try { speech.speaker.remove(NAME); } catch (e) { /* 插件不可用时无需删除. */ }
    if (generated) files.removeDir(dir);
}

toastLog(summary.error ? '示例未完成' : '说话人识别示例完成');
if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
