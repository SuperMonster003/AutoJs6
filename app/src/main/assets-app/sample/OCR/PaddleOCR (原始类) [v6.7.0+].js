/**
 * @author TonyJiangWJ
 */
!function originalClassesForPaddleOcr() {

    let hintForAutoJs670AndAbove = '自 AutoJs6 6.7.0 起, Paddle OCR 已进行功能插件化, 因此无法再使用内置原始类.\n\n' +
        '如需了解插件 API 使用示例, 可参考 "PaddleOCR (内置 API).js" 示例代码.';

    dialogs.build({
        title: R.string.text_prompt,
        content: hintForAutoJs670AndAbove,
        positiveText: R.string.text_exit,
        stubborn: true,
    }).on('positive', (d) => {
        d.dismiss();
        exit();
    }).show();

    exit();

    const Predictor = com.baidu.paddle.lite.ocr.Predictor;

    console.show();

    // 指定是否用精简版模型 速度较快
    let useSlim = false;

    // 创建检测器
    let predictor = new Predictor();

    // predictor.cpuThreadNum = 4 // 可以自定义使用 CPU 的线程数
    // predictor.checkModelLoaded = false // 可以自定义是否需要校验模型是否成功加载 默认开启 使用内置 Base64 图片进行校验 识别测试文本来校验模型是否加载成功

    // 初始化模型 首次运行时会比较耗时
    let loading = threads.disposable();

    // 建议在新线程中初始化模型
    threads.start(function () {
        loading.setAndNotify(predictor.init(context, useSlim));
        // loading.setAndNotify(predictor.init(context)) 为默认不使用精简版
        // 内置默认 modelPath 为 models/ocr_v3_for_cpu, 初始化自定义模型请写绝对路径否则无法获取到
        // 内置默认 labelPath 为 labels/ppocr_keys_v1.txt
        // let modelPath = files.path('./models/customize') // 指定自定义模型路径
        // let labelPath = files.path('./models/customize') // 指定自定义 label 路径
        // 使用自定义模型时 det rec cls 三个模型文件名称需要手动指定
        // predictor.detModelFilename = 'det_opt.nb'
        // predictor.recModelFilename = 'rec_opt.nb'
        // predictor.clsModelFilename = 'cls_opt.nb'
        // loading.setAndNotify(predictor.init(context, modelPath, labelPath))
    });

    let loadSuccess = loading.blockedGet();
    toastLog(`加载模型结果: ${loadSuccess}`);

    let start = new Date();
    let img = images.read('test.png');
    let results = predictor.runOcr(img.getBitmap());

    toastLog(`识别结束, 耗时: ${new Date() - start}ms`);

    log(`识别结果: ${JSON.stringify(results.toArray().map((result) => {
        return { label: result.label, confidence: result.confidence, bounds: result.bounds };
    }))}`);

    // 释放模型 用于释放 native 内存, 非必需
    // predictor.releaseModel()

    // 回收图片
    img.recycle();
}();
