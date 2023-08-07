/**
 * @author TonyJiangWJ
 */
console.show()
// 指定是否用精简版模型 速度较快 默认为true
let useSlim = false
// cpu线程数量，实际好像没啥作用
let cpuThreadNum = 4
let start = new Date()
let img = images.read('test.png')
let results = paddle_ocr.detect(img, cpuThreadNum, useSlim)
toastLog('识别结束, 耗时：' + (new Date() - start) + 'ms')
log('识别结果：' + JSON.stringify(Array.from(results).map(result => ({ label: result.label, confidence: result.confidence, bounds: result.bounds }))))
// 回收图片
img.recycle()
