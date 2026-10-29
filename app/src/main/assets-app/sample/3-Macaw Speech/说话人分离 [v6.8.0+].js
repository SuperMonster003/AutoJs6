/**
 * 说话人分离 [v6.8.0+]
 *
 * speech.diarize 由 3-Macaw Speech 插件 1.3.0 及以上提供: 先在插件首页下载一个分离模型和一个说话人模型, 再下载一个多说话人的
 * 合成模型 (例如 Kokoro), 示例用它的两种嗓音轮流合成几句话, 拼成一段 "对话" 代替真人录音.
 * 演示三件事:
 * 1. diarize 找出 "谁在什么时候说话": 每段给出开始 / 结束毫秒与说话人编号 (从 0 起, 按出场顺序);
 * 2. 已知人数时传 speakers 更准; onProgress 报告已处理的音频毫秒;
 * 3. identify: true 时用已注册的声纹 (见 "说话人识别" 示例) 为每位说话人取名, 认不出时 name 为 null.
 * 换成自己的录音 (WAV, MP3 等, 至多 30 分钟) 时, 可选参数 (engines.execScriptFile 的 arguments): file 为录音路径,
 * speakers 为已知人数, output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let dir = files.join(files.getSdcardPath(), 'Download', 'macaw-diarize');

if (!speech.isAvailable()) {
    toastLog('3-Macaw Speech 插件不可用, 请在插件中心安装或启用后重试');
    exit();
}
if (speech.capabilities().features.indexOf('diarization') < 0) {
    toastLog('说话人分离需要 3-Macaw Speech 1.3.0 及以上');
    exit();
}

/* 把几段同格式的 WAV 拼成一个文件 (只取各文件的 data 块). */
function joinWav(paths, target) {
    let header = null, chunks = [], total = 0;
    paths.forEach((path) => {
        let bytes = files.readBytes(path);
        let view = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        let offset = 12;
        while (offset + 8 <= bytes.length) {
            let id = String(new java.lang.String(bytes, offset, 4, 'US-ASCII'));
            let size = view.getInt(offset + 4);
            if (id === 'fmt ' && header === null) header = java.util.Arrays.copyOfRange(bytes, 12, offset + 8 + size);
            if (id === 'data') {
                chunks.push(java.util.Arrays.copyOfRange(bytes, offset + 8, offset + 8 + size));
                total += size;
                break;
            }
            offset += 8 + size + (size % 2);
        }
    });
    let out = new java.io.ByteArrayOutputStream();
    let le = (n) => java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(n).array();
    out.write(new java.lang.String('RIFF').getBytes('US-ASCII'));
    out.write(le(4 + header.length + 8 + total));
    out.write(new java.lang.String('WAVE').getBytes('US-ASCII'));
    out.write(header);
    out.write(new java.lang.String('data').getBytes('US-ASCII'));
    out.write(le(total));
    chunks.forEach((chunk) => out.write(chunk));
    files.writeBytes(target, out.toByteArray());
    return target;
}

/* 准备录音: 有参数时用自己的录音, 否则用合成模型的两个说话人轮流说四句话. */
let file, generated = false;
if (args.file) {
    file = String(args.file);
} else {
    let model = speech.models.list({ kind: 'tts', installed: true }).filter((m) => (m.speakers || 1) >= 2)[0];
    if (!model) {
        toastLog('请先在 3-Macaw Speech 首页下载一个多说话人的合成模型 (例如 Kokoro), 或用 file 参数传入自己的录音');
        exit();
    }
    let chinese = model.languages.indexOf('zh') >= 0;
    /* Kokoro multi-lang v1.1 的说话人 3 - 102 说中文, 0 - 2 说英文. */
    let voices = chinese ? ['3', '10'] : ['0', '1'];
    let lines = chinese
        ? ['你好, 请问明天的会议几点开始?', '上午十点, 在三楼的大会议室.', '好的, 我需要准备什么材料吗?', '带上上个月的销售报表就可以了.']
        : ['Hello, what time does the meeting start tomorrow?', 'At ten in the morning, in the big room upstairs.',
            'Okay, should I bring anything with me?', 'Just the sales report from last month.'];
    console.log('用合成模型生成对话:', model.name, '| 说话人', voices.join(' / '));
    let parts = lines.map((text, i) => {
        let path = files.join(dir, 'part-' + i + '.wav');
        files.createWithDirs(path);
        return speech.synthesize(text, path, { model: model.id, speaker: voices[i % 2] }).path;
    });
    file = joinWav(parts, files.join(dir, 'dialog.wav'));
    generated = true;
}

let summary = {};
try {
    /* 1. 分离: 不给 speakers 时由插件估计人数; 长录音用 diarizeAsync (返回 Promise) 不会阻塞界面线程. */
    let options = {
        onProgress: (done, total) => console.verbose('已处理', Math.round(done / 1000), '/', Math.round(total / 1000), '秒'),
    };
    if (args.speakers) options.speakers = Number(args.speakers);
    let result = speech.diarize(file, options);
    console.log('共', result.speakers, '位说话人,', result.segments.length, '段 | 时长', result.durationMillis, 'ms | 模型', result.modelId);
    result.segments.forEach((s) => {
        console.log('  说话人 ' + s.speaker + ': ' + (s.startMillis / 1000).toFixed(1) + ' - ' + (s.endMillis / 1000).toFixed(1) + ' 秒');
    });
    summary.speakers = result.speakers;
    summary.segments = result.segments.length;

    /* 2. 有已注册的声纹时顺便为说话人取名 (每人取最长的几段, 至多 10 秒). */
    if (speech.speaker.list().length > 0) {
        let named = speech.diarize(file, { speakers: result.speakers, identify: true });
        named.names.forEach((n) => console.log('  说话人 ' + n.speaker + ' 是:', n.name === null ? '未注册的人' : n.name, '| 分数', n.score));
    }
} catch (e) {
    let hint = {
        MODEL_MISSING: '请先在 3-Macaw Speech 首页下载分离模型与说话人模型',
        PLUGIN_INCOMPATIBLE: '说话人分离需要 3-Macaw Speech 1.3.0 及以上',
    }[e.code];
    toastLog(hint || '出错 [' + e.code + ']: ' + e.message);
    summary.error = e.code;
} finally {
    if (generated) files.removeDir(dir);
}

toastLog(summary.error ? '示例未完成' : '说话人分离示例完成');
if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
