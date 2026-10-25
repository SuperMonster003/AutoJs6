package org.autojs.autojs.runtime.api.modules.ocr

import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.OcrResult
import org.autojs.autojs.runtime.api.modules.ocr.Ocr.Companion.OcrMode
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceFloatNumber
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.PaddleOcrOptionExtraKeys
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

class OcrRapid(private val scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    fun invoke(vararg args: Any?): NativeArray = recognizeText(scriptRuntime, args)

    companion object : ArgumentGuards() {

        private const val ENGINE_RAPID_OCR = "rapid-ocr"
        private const val MODULE_LABEL_RAPID_OCR = "Rapid OCR"
        private const val DEFAULT_MAX_SIDE_LEN = 1024
        private const val DEFAULT_BOX_SCORE_THRESH = 0.5f
        private const val DEFAULT_USE_RAW = true

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recognizeText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            Ocr.recognizeTextWith(scriptRuntime, OcrMode.RAPID, argList)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            Ocr.detectWith(scriptRuntime, OcrMode.RAPID, argList)
        }

        fun recognizeTextImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject, selectedPlugin: OcrPluginHost.Discovered? = null): List<String> {
            if (isInrt) {
                return scriptRuntime.ocrRapid.recognizeText(image)
            }
            val ocrOptions = getOcrOptions(options)
            return runBlocking(scriptRuntime.coroutineContext) {
                val target = selectedPlugin ?: OcrPluginHost.selectOrThrow(
                    context = GlobalAppContext.get(),
                    engineId = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.ENGINE_ID),
                    engine = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.ENGINE) ?: ENGINE_RAPID_OCR,
                    variant = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.VARIANT),
                    moduleLabel = MODULE_LABEL_RAPID_OCR,
                )
                OcrPluginHost.recognizeText(GlobalAppContext.get(), target, image.bitmap, ocrOptions)
            }
        }

        fun detectImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject, selectedPlugin: OcrPluginHost.Discovered? = null): List<OcrResult> {
            if (isInrt) {
                return scriptRuntime.ocrRapid.detect(image)
            }
            val ocrOptions = getOcrOptions(options)
            return runBlocking(scriptRuntime.coroutineContext) {
                val target = selectedPlugin ?: OcrPluginHost.selectOrThrow(
                    context = GlobalAppContext.get(),
                    engineId = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.ENGINE_ID),
                    engine = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.ENGINE) ?: ENGINE_RAPID_OCR,
                    variant = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.VARIANT),
                    moduleLabel = MODULE_LABEL_RAPID_OCR,
                )
                OcrPluginHost.detect(GlobalAppContext.get(), target, image.bitmap, ocrOptions)
            }.map { OcrResult(it.text, it.confidence, it.bounds) }
        }

        private fun getOcrOptions(options: NativeObject): OcrOptions {
            val detLongSize = options.inquire("detLongSize", ::coerceIntNumber, 0)
            val maxSideLen = options.inquire("maxSideLen", ::coerceIntNumber, detLongSize)
            val boxScoreThresh = options.inquire("boxScoreThresh", ::coerceFloatNumber, DEFAULT_BOX_SCORE_THRESH)
            val scoreThreshold = options.inquire("scoreThreshold", ::coerceFloatNumber, boxScoreThresh)
            val useRaw = options.inquire("useRaw", ::coerceBoolean, DEFAULT_USE_RAW) ||
                    options.inquire("raw", ::coerceBoolean, false)
            val imageQuality = options.inquire("imageQuality", ::coerceIntNumber, -1)
            val imageFormat = options.inquire("imageFormat", ::coerceString, "")
            val engineId = options.inquire(PaddleOcrOptionExtraKeys.ENGINE_ID, ::coerceString, "")
            val engine = options.inquire(PaddleOcrOptionExtraKeys.ENGINE, ::coerceString, "")
            val variant = options.inquire(PaddleOcrOptionExtraKeys.VARIANT, ::coerceString, "")
            val normalizedVariant = normalizeRapidVariant(engineId, engine, variant)
            val profile = options.inquire(PaddleOcrOptionExtraKeys.PROFILE, ::coerceString, "")

            val extras = Bundle()
            engineId.takeIf { it.isNotBlank() }?.let { extras.putString(PaddleOcrOptionExtraKeys.ENGINE_ID, it) }
            engine.takeIf { it.isNotBlank() }?.let { extras.putString(PaddleOcrOptionExtraKeys.ENGINE, it) }
            normalizedVariant.takeIf { it.isNotBlank() }?.let { extras.putString(PaddleOcrOptionExtraKeys.VARIANT, it) }
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
                this.detLongSize = maxSideLen.takeIf { it > 0 } ?: DEFAULT_MAX_SIDE_LEN
                this.scoreThreshold = scoreThreshold
                this.extras = extras.takeIf { !it.isEmpty } ?: Bundle()
            }
        }

        private fun normalizeRapidVariant(engineId: String, engine: String, variant: String): String {
            if (variant.isBlank()) return variant
            val isRapidEngine = engineId.isBlank() && engine.isBlank() ||
                    engineId.startsWith(ENGINE_RAPID_OCR) ||
                    engine == ENGINE_RAPID_OCR
            if (!isRapidEngine) return variant
            return when (variant) {
                "v3" -> "pp-ocrv3"
                "v6" -> "pp-ocrv6"
                else -> variant
            }
        }

        private fun Bundle.getNonBlankString(key: String): String? {
            return getString(key)?.takeIf { it.isNotBlank() }
        }

    }

}
