package org.autojs.autojs.runtime.api.modules.ocr

import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.OcrResult
import org.autojs.autojs.runtime.api.modules.ocr.Ocr.Companion.OcrMode
import org.autojs.autojs.runtime.api.toScriptResult
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.PaddleOcrOptionExtraKeys
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

class OcrMLKit(private val scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    fun invoke(vararg args: Any?): NativeArray = recognizeText(scriptRuntime, args)

    @Suppress("unused")
    companion object : ArgumentGuards() {

        private const val ENGINE_MLKIT_OCR = "mlkit-ocr"
        private const val MODULE_LABEL_MLKIT_OCR = "ML Kit OCR"
        private const val DEFAULT_USE_RAW = true

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
            val ocrOptions = getOcrOptions(options)
            return runBlocking(scriptRuntime.coroutineContext) {
                val target = selectedPlugin ?: OcrPluginHost.selectOrThrow(
                    context = GlobalAppContext.get(),
                    engineId = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.ENGINE_ID),
                    engine = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.ENGINE) ?: ENGINE_MLKIT_OCR,
                    variant = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.VARIANT),
                    moduleLabel = MODULE_LABEL_MLKIT_OCR,
                )
                OcrPluginHost.recognizeText(GlobalAppContext.get(), target, image.bitmap, ocrOptions)
            }
        }

        fun detectImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject, selectedPlugin: OcrPluginHost.Discovered? = null): List<OcrResult> {
            ApkBuilder.Lib.MLKIT_OCR.ensureLibFiles(OcrMode.MLKIT.value)
            val ocrOptions = getOcrOptions(options)
            return runBlocking(scriptRuntime.coroutineContext) {
                val target = selectedPlugin ?: OcrPluginHost.selectOrThrow(
                    context = GlobalAppContext.get(),
                    engineId = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.ENGINE_ID),
                    engine = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.ENGINE) ?: ENGINE_MLKIT_OCR,
                    variant = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.VARIANT),
                    moduleLabel = MODULE_LABEL_MLKIT_OCR,
                )
                OcrPluginHost.detect(GlobalAppContext.get(), target, image.bitmap, ocrOptions)
            }.map { it.toScriptResult() }
        }

        private fun getOcrOptions(options: NativeObject): OcrOptions {
            val useRaw = options.inquire("useRaw", ::coerceBoolean, DEFAULT_USE_RAW) ||
                    options.inquire("raw", ::coerceBoolean, false)
            val imageQuality = options.inquire("imageQuality", ::coerceIntNumber, -1)
            val imageFormat = options.inquire("imageFormat", ::coerceString, "")
            val engineId = options.inquire(PaddleOcrOptionExtraKeys.ENGINE_ID, ::coerceString, "")
            val engine = options.inquire(PaddleOcrOptionExtraKeys.ENGINE, ::coerceString, "")
            val variant = options.inquire(PaddleOcrOptionExtraKeys.VARIANT, ::coerceString, "")
            val profile = options.inquire(PaddleOcrOptionExtraKeys.PROFILE, ::coerceString, "")

            val extras = Bundle()
            engineId.takeIf { it.isNotBlank() }?.let { extras.putString(PaddleOcrOptionExtraKeys.ENGINE_ID, it) }
            engine.takeIf { it.isNotBlank() }?.let { extras.putString(PaddleOcrOptionExtraKeys.ENGINE, it) }
            variant.takeIf { it.isNotBlank() }?.let { extras.putString(PaddleOcrOptionExtraKeys.VARIANT, it) }
            profile.takeIf { it.isNotBlank() }?.let { extras.putString(PaddleOcrOptionExtraKeys.PROFILE, it) }
            if (useRaw && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                extras.putBoolean(PaddleOcrOptionExtraKeys.RAW_IMAGE, true)
            }
            if (imageQuality > 0) {
                extras.putInt(PaddleOcrOptionExtraKeys.IMAGE_QUALITY, imageQuality)
            }
            if (imageFormat.isNotBlank()) {
                extras.putString(PaddleOcrOptionExtraKeys.IMAGE_FORMAT, imageFormat)
            }

            return OcrOptions().apply {
                this.extras = extras.takeIf { !it.isEmpty } ?: Bundle()
            }
        }

        private fun Bundle.getNonBlankString(key: String): String? {
            return getString(key)?.takeIf { it.isNotBlank() }
        }

    }

}
