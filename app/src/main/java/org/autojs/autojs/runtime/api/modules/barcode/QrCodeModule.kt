package org.autojs.autojs.runtime.api.modules.barcode

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.barcode.BarcodeArgs.ABSENT

/**
 * The `qrcode` module on the new script module layer (roadmap P3.1): `barcode` restricted to QR codes, with the same
 * members and argument rules ([BarcodeArgs]); the detection stays with the current `augment/barcode/QrCode` functions
 * (`enableAllPotentialQrCodes`, falling back to `enableAllPotentialBarcodes`, as documented).
 *
 * zh-CN: 新脚本模块层上的 qrcode 模块 (P3.1): 限定为二维码的 barcode, 成员与参数规则相同 ([BarcodeArgs]); 识别仍由现有 augment/barcode/QrCode 函数完成
 * (按文档读取 enableAllPotentialQrCodes, 未提供时读取 enableAllPotentialBarcodes).
 */
@ScriptModule(name = "qrcode")
class QrCodeModule(private val runtime: ScriptRuntime) {

    @ScriptInvoke
    fun invoke(image: Any? = ABSENT, options: Any? = ABSENT, isAll: Any? = ABSENT): Any? {
        val args = BarcodeArgs.selection(MODULE, image, options, isAll)
        return if (Barcode.shouldTakeAsAll(args)) QrCode.recognizeTexts(runtime, args) else QrCode.recognizeText(runtime, args)
    }

    @ScriptFunction
    fun recognizeText(image: Any? = ABSENT, options: Any? = ABSENT, isAll: Any? = ABSENT): Any? =
        QrCode.recognizeText(runtime, BarcodeArgs.selection("$MODULE.recognizeText", image, options, isAll))

    @ScriptFunction
    fun recognizeTexts(image: Any? = ABSENT, options: Any? = ABSENT): List<String> =
        QrCode.recognizeTexts(runtime, BarcodeArgs.list("$MODULE.recognizeTexts", image, options))

    @ScriptFunction
    fun detect(image: Any? = ABSENT, options: Any? = ABSENT, isAll: Any? = ABSENT): Any? =
        QrCode.detect(runtime, BarcodeArgs.selection("$MODULE.detect", image, options, isAll))

    @ScriptFunction
    fun detectAll(image: Any? = ABSENT, options: Any? = ABSENT): Any =
        QrCode.detectAll(runtime, BarcodeArgs.list("$MODULE.detectAll", image, options))

    private companion object {
        const val MODULE = "qrcode"
    }

}
