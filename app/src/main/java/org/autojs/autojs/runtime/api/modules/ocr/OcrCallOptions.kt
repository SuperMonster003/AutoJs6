package org.autojs.autojs.runtime.api.modules.ocr

import android.os.Build
import android.os.Bundle
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.api.modules.ocr.Ocr.Companion.OcrMode
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceFloatNumber
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.PaddleOcrOptionExtraKeys
import org.mozilla.javascript.NativeObject

/**
 * Reads the options of a script call with the JS coercion rules, where [lookup] gives the raw value of an option. An
 * option that is null or undefined counts as not given.
 *
 * zh-CN: 按 JS 强制转换规则读取脚本调用的选项, [lookup] 提供选项的原始值. 值为 null 或 undefined 的选项视为未给出.
 */
internal class OcrOptionReader(private val lookup: (String) -> Any?) {

    private fun given(name: String) = lookup(name).takeUnless { it.isJsNullish() }

    fun boolean(name: String, default: Boolean) = booleanOrNull(name) ?: default

    fun booleanOrNull(name: String) = given(name)?.let { coerceBoolean(it, false) }

    fun int(name: String, default: Int) = given(name)?.let { coerceIntNumber(it, default) } ?: default

    fun float(name: String, default: Float) = given(name)?.let { coerceFloatNumber(it, default) } ?: default

    fun string(name: String) = given(name)?.let { coerceString(it, "") }.orEmpty()

}

internal fun NativeObject.toOcrOptionReader() = OcrOptionReader { prop(it) }

/**
 * The options of one OCR call by the rules of its engine: what the plugin receives and the host's own [mergeLine]. Rhino's
 * `ocr` modules and the Node.js and Bun bridge all parse here, so their defaults stay the same.
 *
 * zh-CN: 一次 OCR 调用的选项, 按所用引擎的规则解析: 包括传给插件的内容与宿主自身处理的 [mergeLine]. Rhino 的 ocr 模块与 Node.js / Bun 桥都在此解析, 缺省值因而保持一致.
 */
internal data class OcrCallOptions(
    val cpuThreadNum: Int = 0,
    val useSlim: Boolean = false,
    val useOpenCL: Boolean = false,
    val detLongSize: Int = 0,
    val scoreThreshold: Float = 0f,
    val mergeLine: Boolean = false,
    val extras: Map<String, Any> = emptyMap(),
) {

    val engineId get() = extras[PaddleOcrOptionExtraKeys.ENGINE_ID] as String?
    val engine get() = extras[PaddleOcrOptionExtraKeys.ENGINE] as String?
    val variant get() = extras[PaddleOcrOptionExtraKeys.VARIANT] as String?
    val profile get() = extras[PaddleOcrOptionExtraKeys.PROFILE] as String?

    fun toPluginOptions() = OcrOptions().also { options ->
        options.cpuThreadNum = cpuThreadNum
        options.useSlim = useSlim
        options.useOpenCL = useOpenCL
        options.detLongSize = detLongSize
        options.scoreThreshold = scoreThreshold
        options.extras = Bundle().apply {
            extras.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                    else -> throw IllegalStateException("Unexpected OCR option extra \"$key\"")
                }
            }
        }
    }

    companion object {

        private const val DEFAULT_CPU_THREAD_NUM = 4
        private const val DEFAULT_USE_SLIM = true
        private const val DEFAULT_USE_OPENCL = false
        private const val DEFAULT_MERGE_LINE = false

        private const val DEFAULT_MAX_SIDE_LEN = 1024
        private const val DEFAULT_BOX_SCORE_THRESH = 0.5f

        private const val DEFAULT_USE_RAW = true

        /** The options by the rules of [engine], as Rhino's automatic mode dispatches on the selected plugin. */
        fun forEngine(engine: String?, read: OcrOptionReader) = when (engine) {
            OcrMode.MLKIT.engine -> mlKit(read)
            OcrMode.RAPID.engine -> rapid(read)
            else -> paddle(read)
        }

        fun paddle(read: OcrOptionReader): OcrCallOptions {
            val extras = commonExtras(read)
            val detLimitSideLen = read.int(PaddleOcrOptionExtraKeys.DET_LIMIT_SIDE_LEN, 0)
            if (detLimitSideLen > 0) {
                extras[PaddleOcrOptionExtraKeys.DET_LIMIT_SIDE_LEN] = detLimitSideLen
            }
            // Only a given value is passed on, so a plugin without the classifier keeps its own default.
            // zh-CN: 只传递脚本给出的值, 不带分类器的插件保持自己的默认行为.
            read.booleanOrNull(PaddleOcrOptionExtraKeys.USE_CLS)?.let { extras[PaddleOcrOptionExtraKeys.USE_CLS] = it }
            val splitWords = read.boolean("splitWords", false)
            val useWordSegmentation = read.boolean("useWordSegmentation", false)
            return OcrCallOptions(
                cpuThreadNum = read.int("cpuThreadNum", DEFAULT_CPU_THREAD_NUM),
                useSlim = read.boolean("useSlim", DEFAULT_USE_SLIM),
                useOpenCL = read.boolean("useOpenCL", DEFAULT_USE_OPENCL),
                detLongSize = read.int("detLongSize", 0),
                scoreThreshold = read.float("scoreThreshold", -1f),
                mergeLine = read.boolean("mergeLine", DEFAULT_MERGE_LINE) && !splitWords && !useWordSegmentation,
                extras = extras,
            )
        }

        fun rapid(read: OcrOptionReader): OcrCallOptions {
            val maxSideLen = read.int("maxSideLen", read.int("detLongSize", 0))
            val boxScoreThresh = read.float("boxScoreThresh", DEFAULT_BOX_SCORE_THRESH)
            return OcrCallOptions(
                detLongSize = maxSideLen.takeIf { it > 0 } ?: DEFAULT_MAX_SIDE_LEN,
                scoreThreshold = read.float("scoreThreshold", boxScoreThresh),
                extras = commonExtras(read, ::normalizeRapidVariant),
            )
        }

        fun mlKit(read: OcrOptionReader) = OcrCallOptions(extras = commonExtras(read))

        private fun commonExtras(
            read: OcrOptionReader,
            normalizeVariant: (engineId: String, engine: String, variant: String) -> String = { _, _, variant -> variant },
        ): MutableMap<String, Any> {
            val extras = linkedMapOf<String, Any>()
            val engineId = read.string(PaddleOcrOptionExtraKeys.ENGINE_ID)
            val engine = read.string(PaddleOcrOptionExtraKeys.ENGINE)
            val variant = normalizeVariant(engineId, engine, read.string(PaddleOcrOptionExtraKeys.VARIANT))
            mapOf(
                PaddleOcrOptionExtraKeys.ENGINE_ID to engineId,
                PaddleOcrOptionExtraKeys.ENGINE to engine,
                PaddleOcrOptionExtraKeys.VARIANT to variant,
                PaddleOcrOptionExtraKeys.PROFILE to read.string(PaddleOcrOptionExtraKeys.PROFILE),
                PaddleOcrOptionExtraKeys.IMAGE_FORMAT to read.string("imageFormat"),
            ).filterValues { it.isNotBlank() }.let(extras::putAll)
            val useRaw = read.boolean("useRaw", DEFAULT_USE_RAW) || read.boolean("raw", false)
            if (useRaw && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                extras[PaddleOcrOptionExtraKeys.RAW_IMAGE] = true
            }
            val imageQuality = read.int("imageQuality", -1)
            if (imageQuality > 0) {
                extras[PaddleOcrOptionExtraKeys.IMAGE_QUALITY] = imageQuality
            }
            return extras
        }

        private fun normalizeRapidVariant(engineId: String, engine: String, variant: String): String {
            if (variant.isBlank()) return variant
            val rapidEngine = OcrMode.RAPID.engine!!
            val isRapidEngine = engineId.isBlank() && engine.isBlank() ||
                    engineId.startsWith(rapidEngine) ||
                    engine == rapidEngine
            if (!isRapidEngine) return variant
            return when (variant) {
                "v3" -> "pp-ocrv3"
                "v6" -> "pp-ocrv6"
                else -> variant
            }
        }

    }

}
