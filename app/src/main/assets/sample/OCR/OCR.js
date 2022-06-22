/**
 * create by TonyJiangWJ
 */
const img = images.read("./test.png")
console.show()
let cpuThreadNum = 4
// PaddleOCR 移动端提供了两种模型：ocr_v3_for_cpu与ocr_v3_for_cpu(slim)，此选项用于选择加载的模型,默认true使用v2的slim版(速度更快)，false使用v3的普通版(准确率更高）
let useSlim = true
let start = new Date()
// 识别图片中的文字，返回完整识别信息（兼容百度OCR格式）。
let result = $ocr.detect(img, cpuThreadNum, useSlim)
log(useSlim ? 'slim' : '' + '识别耗时：' + (new Date() - start) + 'ms')
// 可以使用简化的调用命令，默认参数：cpuThreadNum = 4, useSlim = true
// const result = $ocr.detect(img)
toastLog("完整识别信息: " + JSON.stringify(wrapList(result)))
start = new Date()
// 识别图片中的文字，只返回文本识别信息（字符串列表）。当前版本可能存在文字顺序错乱的问题 建议先使用detect后自行排序
const stringList = $ocr.recognizeText(img, cpuThreadNum, useSlim)
log('slim纯文本识别耗时：' + (new Date() - start) + 'ms')
// 可以使用简化的调用命令，默认参数：cpuThreadNum = 4, useSlim = true
// const stringList = $ocr.recognizeText(img)
toastLog("文本识别信息（字符串列表）: " + JSON.stringify(stringList))
// 增加线程数
cpuThreadNum = 8
start = new Date()
result = $ocr.detect(img, cpuThreadNum, useSlim)
log('8线程识别耗时：' + (new Date() - start) + 'ms')
toastLog("完整识别信息（兼容百度OCR格式）: " + JSON.stringify(wrapList(result)))
// 释放模型 用于释放native内存 非必要
// $ocr.release()
// 回收图片
img.recycle()

function wrapList(result) {
  result = runtime.bridges.getBridges().toArray(result)
  if (result && result.length > 0) {
    return result.map(javaObj => ({
          label: javaObj.getLabel(),
          confidence: javaObj.getConfidence(),
          bounds: javaObj.getBounds() ? (b => ({
              bottom: b.bottom,
              left: b.left,
              right: b.right,
              top: b.top
          }))(javaObj.getBounds()) : {},
          words: javaObj.getWords()
    }))
  }
  return null
}