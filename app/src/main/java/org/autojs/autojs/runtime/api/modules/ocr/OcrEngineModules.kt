package org.autojs.autojs.runtime.api.modules.ocr

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.ocr.Ocr.Companion.OcrMode
import org.autojs.autojs.runtime.api.augment.ocr.OcrMLKit
import org.autojs.autojs.runtime.api.augment.ocr.OcrPaddle
import org.autojs.autojs.runtime.api.augment.ocr.OcrRapid
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ocr.OcrArgs.ABSENT

/*
 * The engine modules `ocr.mlkit`, `ocr.paddle` and `ocr.rapid` on the new script module layer (roadmap P3.1): the
 * recognition of `ocr` with a fixed engine, whatever `ocr.mode` and `options.mode` select, with the argument rules of
 * [OcrArgs]; the recognition stays with the current engine augmentables (`augment/ocr/OcrMLKit` and the others). Their module
 * objects are the modes `ocr.tap` accepts besides the mode names (`ScriptRuntime.augmentedOcrMLKit` and the others).
 *
 * zh-CN: 新脚本模块层上的引擎模块 ocr.mlkit, ocr.paddle 与 ocr.rapid (P3.1): 固定引擎的 ocr 识别, 不受 ocr.mode 与 options.mode 影响, 参数规则同 [OcrArgs];
 * 识别仍由现有各引擎 (augment/ocr/OcrMLKit 等) 完成. 其模块对象是 ocr.tap 除模式名称外接受的模式 (ScriptRuntime.augmentedOcrMLKit 等).
 */

/** `ocr.mlkit`: the recognition with ML Kit OCR. zh-CN: ocr.mlkit: 使用 ML Kit OCR 识别. */
@ScriptModule(name = "mlkit", parent = OcrModule::class, dollarPrefix = false)
class OcrMLKitModule(private val runtime: ScriptRuntime) {

    @ScriptInvoke
    fun invoke(input: Any? = ABSENT, optionsOrRegion: Any? = ABSENT): Any =
        OcrMLKit.recognizeText(runtime, OcrArgs.recognition(MODULE, runtime, input, optionsOrRegion, OcrMode.MLKIT))

    @ScriptFunction
    fun recognizeText(input: Any? = ABSENT, optionsOrRegion: Any? = ABSENT): Any =
        OcrMLKit.recognizeText(runtime, OcrArgs.recognition("$MODULE.recognizeText", runtime, input, optionsOrRegion, OcrMode.MLKIT))

    @ScriptFunction
    fun detect(input: Any? = ABSENT, optionsOrRegion: Any? = ABSENT): Any =
        OcrMLKit.detect(runtime, OcrArgs.recognition("$MODULE.detect", runtime, input, optionsOrRegion, OcrMode.MLKIT))

    private companion object {
        const val MODULE = "ocr.mlkit"
    }

}

/** `ocr.paddle`: the recognition with Paddle OCR. zh-CN: ocr.paddle: 使用 Paddle OCR 识别. */
@ScriptModule(name = "paddle", parent = OcrModule::class, dollarPrefix = false)
class OcrPaddleModule(private val runtime: ScriptRuntime) {

    @ScriptInvoke
    fun invoke(input: Any? = ABSENT, optionsOrRegion: Any? = ABSENT): Any =
        OcrPaddle.recognizeText(runtime, OcrArgs.recognition(MODULE, runtime, input, optionsOrRegion, OcrMode.PADDLE))

    @ScriptFunction
    fun recognizeText(input: Any? = ABSENT, optionsOrRegion: Any? = ABSENT): Any =
        OcrPaddle.recognizeText(runtime, OcrArgs.recognition("$MODULE.recognizeText", runtime, input, optionsOrRegion, OcrMode.PADDLE))

    @ScriptFunction
    fun detect(input: Any? = ABSENT, optionsOrRegion: Any? = ABSENT): Any =
        OcrPaddle.detect(runtime, OcrArgs.recognition("$MODULE.detect", runtime, input, optionsOrRegion, OcrMode.PADDLE))

    private companion object {
        const val MODULE = "ocr.paddle"
    }

}

/** `ocr.rapid`: the recognition with Rapid OCR. zh-CN: ocr.rapid: 使用 Rapid OCR 识别. */
@ScriptModule(name = "rapid", parent = OcrModule::class, dollarPrefix = false)
class OcrRapidModule(private val runtime: ScriptRuntime) {

    @ScriptInvoke
    fun invoke(input: Any? = ABSENT, optionsOrRegion: Any? = ABSENT): Any =
        OcrRapid.recognizeText(runtime, OcrArgs.recognition(MODULE, runtime, input, optionsOrRegion, OcrMode.RAPID))

    @ScriptFunction
    fun recognizeText(input: Any? = ABSENT, optionsOrRegion: Any? = ABSENT): Any =
        OcrRapid.recognizeText(runtime, OcrArgs.recognition("$MODULE.recognizeText", runtime, input, optionsOrRegion, OcrMode.RAPID))

    @ScriptFunction
    fun detect(input: Any? = ABSENT, optionsOrRegion: Any? = ABSENT): Any =
        OcrRapid.detect(runtime, OcrArgs.recognition("$MODULE.detect", runtime, input, optionsOrRegion, OcrMode.RAPID))

    private companion object {
        const val MODULE = "ocr.rapid"
    }

}
