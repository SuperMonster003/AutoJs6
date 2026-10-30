package org.autojs.autojs.runtime.api.modules.ocr

import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.OcrResult
import org.autojs.autojs.runtime.api.OcrResultExtras
import org.autojs.autojs.runtime.api.modules.ocr.Ocr.Companion.OcrMode
import org.autojs.autojs.runtime.api.toScriptResult
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

/**
 * Modified by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) as of Feb 13, 2026.
 * Modified by SuperMonster003 as of Mar 26, 2026.
 */
@Suppress("unused")
class OcrPaddle(private val scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    fun invoke(vararg args: Any?): NativeArray = recognizeText(scriptRuntime, args)

    companion object : ArgumentGuards() {

        private const val ENGINE_PADDLE_OCR = "paddle-ocr"
        private const val MODULE_LABEL_PADDLE_OCR = "Paddle OCR"

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recognizeText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            Ocr.recognizeTextWith(scriptRuntime, OcrMode.PADDLE, argList)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            Ocr.detectWith(scriptRuntime, OcrMode.PADDLE, argList)
        }

        fun recognizeTextImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject, selectedPlugin: OcrPluginHost.Discovered? = null): List<String> {
            ApkBuilder.Lib.PADDLE_OCR.ensureLibFiles(OcrMode.PADDLE.value)
            val parsed = OcrCallOptions.paddle(options.toOcrOptionReader())
            val ocrOptions = parsed.toPluginOptions()
            return if (!isInrt) {
                runBlocking(scriptRuntime.coroutineContext) {
                    val target = selectedPlugin ?: OcrPluginHost.selectOrThrow(
                        context = GlobalAppContext.get(),
                        engineId = parsed.engineId,
                        engine = parsed.engine ?: ENGINE_PADDLE_OCR,
                        variant = parsed.variant,
                        // The profile picks the plugin whose modelProfile matches (e.g. a language pack, D23).
                        profile = parsed.profile,
                        moduleLabel = MODULE_LABEL_PADDLE_OCR,
                    )
                    OcrPluginHost.recognizeText(GlobalAppContext.get(), target, image.bitmap, ocrOptions)
                }
            } else {
                // Use embedded engine in packaged (INRT) app.
                // zh-CN: 打包应用 (INRT) 使用内置引擎 (本地推理), 不依赖插件.
                PaddleOcrEmbeddedEngine.recognizeText(GlobalAppContext.get(), image.bitmap, ocrOptions)
            }
        }

        fun detectImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject, selectedPlugin: OcrPluginHost.Discovered? = null): List<OcrResult> {
            ApkBuilder.Lib.PADDLE_OCR.ensureLibFiles(OcrMode.PADDLE.value)
            val parsed = OcrCallOptions.paddle(options.toOcrOptionReader())
            val ocrOptions = parsed.toPluginOptions()
            val results = if (!isInrt) {
                runBlocking(scriptRuntime.coroutineContext) {
                    val target = selectedPlugin ?: OcrPluginHost.selectOrThrow(
                        context = GlobalAppContext.get(),
                        engineId = parsed.engineId,
                        engine = parsed.engine ?: ENGINE_PADDLE_OCR,
                        variant = parsed.variant,
                        // The profile picks the plugin whose modelProfile matches (e.g. a language pack, D23).
                        profile = parsed.profile,
                        moduleLabel = MODULE_LABEL_PADDLE_OCR,
                    )
                    OcrPluginHost.detect(GlobalAppContext.get(), target, image.bitmap, ocrOptions)
                }.map { it.toScriptResult() }
            } else {
                // Use embedded engine in packaged (INRT) app.
                // zh-CN: 打包应用 (INRT) 使用内置引擎 (本地推理), 不依赖插件.
                PaddleOcrEmbeddedEngine.detect(GlobalAppContext.get(), image.bitmap, ocrOptions).map { it.toScriptResult() }
            }
            return if (parsed.mergeLine) mergeByLine(results) else results
        }

        private fun mergeByLine(results: List<OcrResult>): List<OcrResult> = OcrLineMerger.merge(
            results.map { OcrLineMerger.Segment(it.text, it.confidence, it.bounds.toOcrBox(), it.extras) },
            OcrResultExtras::mergeCommon,
        ).map { OcrResult(it.text, it.confidence, it.box.toRect(), it.extras) }

    }

}
