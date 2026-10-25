package org.autojs.autojs.runtime.api.modules.barcode

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.barcode.BarcodeArgs.ABSENT

/**
 * The `barcode` module on the new script module layer (roadmap P3.1): callable (as `barcode.recognizeText`), with
 * `recognizeText`, `recognizeTexts`, `detect` and `detectAll` in the order of the current `augment/barcode/Barcode`.
 * Arguments are checked by [BarcodeArgs] (the documented overloads, T4 / D10); reading the image, the formats and the
 * detection through the ML Kit barcode plugin stay with the current functions, which return the current results
 * (strings, `WrappedBarcode`, appendix C Q24).
 *
 * zh-CN: 新脚本模块层上的 barcode 模块 (P3.1): 可调用 (同 barcode.recognizeText), 有 recognizeText, recognizeTexts, detect 与 detectAll, 顺序同现有
 * augment/barcode/Barcode. 参数由 [BarcodeArgs] 检查 (文档重载, T4 / D10); 读取图片, 解析格式与经 ML Kit 条码插件识别仍由现有函数完成, 返回现有结果 (字符串,
 * WrappedBarcode, 附录 C Q24).
 */
@ScriptModule(name = "barcode")
class BarcodeModule(private val runtime: ScriptRuntime) {

    @ScriptInvoke
    fun invoke(image: Any? = ABSENT, options: Any? = ABSENT, isAll: Any? = ABSENT): Any? {
        val args = BarcodeArgs.selection(MODULE, image, options, isAll)
        return if (Barcode.shouldTakeAsAll(args)) Barcode.recognizeTexts(runtime, args) else Barcode.recognizeText(runtime, args)
    }

    @ScriptFunction
    fun recognizeText(image: Any? = ABSENT, options: Any? = ABSENT, isAll: Any? = ABSENT): Any? =
        Barcode.recognizeText(runtime, BarcodeArgs.selection("$MODULE.recognizeText", image, options, isAll))

    @ScriptFunction
    fun recognizeTexts(image: Any? = ABSENT, options: Any? = ABSENT): List<String> =
        Barcode.recognizeTexts(runtime, BarcodeArgs.list("$MODULE.recognizeTexts", image, options))

    @ScriptFunction
    fun detect(image: Any? = ABSENT, options: Any? = ABSENT, isAll: Any? = ABSENT): Any? =
        Barcode.detect(runtime, BarcodeArgs.selection("$MODULE.detect", image, options, isAll))

    @ScriptFunction
    fun detectAll(image: Any? = ABSENT, options: Any? = ABSENT): Any =
        Barcode.detectAll(runtime, BarcodeArgs.list("$MODULE.detectAll", image, options))

    private companion object {
        const val MODULE = "barcode"
    }

}
