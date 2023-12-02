/**
 * @author TonyJiangWJ
 */
!function internalApiForPaddleOcr() {
    console.show();

    // 指定是否用精简版模型, 速度较快, 默认为 true
    let useSlim = false;

    // CPU 线程数量, 实际好像没啥作用
    let cpuThreadNum = 4;

    let start = new Date();
    let img = images.read('test.png');
    let results = ocr.paddle.detect(img, { useSlim, cpuThreadNum });

    toastLog(`识别结束, 耗时: ${new Date() - start}ms`);

    log(`识别结果: ${JSON.stringify(
        Array.from(results).map((result) => {
            return { label: result.label, confidence: result.confidence, bounds: result.bounds };
        }))}`);

    // 回收图片
    img.recycle();
}();