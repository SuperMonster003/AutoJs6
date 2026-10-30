package org.autojs.autojs.runtime.api.modules.ocr

import kotlinx.coroutines.runBlocking
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.OcrResult
import org.autojs.autojs.runtime.api.modules.ocr.Ocr.Companion.OcrMode
import org.autojs.autojs.runtime.api.toScriptResult
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

class OcrMLKit(private val scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    fun invoke(vararg args: Any?): NativeArray = recognizeText(scriptRuntime, args)

    @Suppress("unused")
    companion object : ArgumentGuards() {

        private const val ENGINE_MLKIT_OCR = "mlkit-ocr"
        private const val MODULE_LABEL_MLKIT_OCR = "ML Kit OCR"

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recognizeText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            Ocr.recognizeTextWith(scriptRuntime, OcrMode.MLKIT, argList)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            Ocr.detectWith(scriptRuntime, OcrMode.MLKIT, argList)
        }

        fun recognizeTextImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject, selectedPlugin: OcrPluginHost.Discovered? = null): List<String> {
            ApkBuilder.Lib.MLKIT_OCR.ensureLibFiles(OcrMode.MLKIT.value)
            val parsed = OcrCallOptions.mlKit(options.toOcrOptionReader())
            return runBlocking(scriptRuntime.coroutineContext) {
                val target = selectedPlugin ?: OcrPluginHost.selectOrThrow(
                    context = GlobalAppContext.get(),
                    engineId = parsed.engineId,
                    engine = parsed.engine ?: ENGINE_MLKIT_OCR,
                    variant = parsed.variant,
                    moduleLabel = MODULE_LABEL_MLKIT_OCR,
                )
                OcrPluginHost.recognizeText(GlobalAppContext.get(), target, image.bitmap, parsed.toPluginOptions())
            }
        }

        fun detectImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject, selectedPlugin: OcrPluginHost.Discovered? = null): List<OcrResult> {
            ApkBuilder.Lib.MLKIT_OCR.ensureLibFiles(OcrMode.MLKIT.value)
            val parsed = OcrCallOptions.mlKit(options.toOcrOptionReader())
            return runBlocking(scriptRuntime.coroutineContext) {
                val target = selectedPlugin ?: OcrPluginHost.selectOrThrow(
                    context = GlobalAppContext.get(),
                    engineId = parsed.engineId,
                    engine = parsed.engine ?: ENGINE_MLKIT_OCR,
                    variant = parsed.variant,
                    moduleLabel = MODULE_LABEL_MLKIT_OCR,
                )
                OcrPluginHost.detect(GlobalAppContext.get(), target, image.bitmap, parsed.toPluginOptions())
            }.map { it.toScriptResult() }
        }

    }

}
