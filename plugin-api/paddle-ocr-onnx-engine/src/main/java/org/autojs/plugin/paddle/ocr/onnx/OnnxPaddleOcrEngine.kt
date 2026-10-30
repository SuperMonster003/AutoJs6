package org.autojs.plugin.paddle.ocr.onnx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.OcrResult
import org.autojs.plugin.paddle.ocr.api.PaddleOcrOptionExtraKeys
import org.autojs.plugin.paddle.ocr.onnx.model.OCRResult
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Embedded PP-OCRv3 engine on ONNX Runtime for packaged applications. Models follow the plugin
 * asset layout ([OnnxPaddleOcrAssets]); native libraries (ONNX Runtime, OpenCV) are copied from
 * the installed plugins at packaging time, so [nativeRuntimeLoader] must load OpenCV before the
 * first session is created.
 * zh-CN: 打包应用使用的 ONNX Runtime 版 PP-OCRv3 内嵌引擎. 模型沿用插件的资产布局 ([OnnxPaddleOcrAssets]);
 * 原生库 (ONNX Runtime, OpenCV) 在打包时从已安装的插件复制, 因此 [nativeRuntimeLoader] 须在创建首个会话前加载 OpenCV.
 */
class OnnxPaddleOcrEngine(
    private val appContext: Context,
    private val slimAvailable: Boolean,
    private val nativeRuntimeLoader: () -> Unit,
) {

    private val lock = Any()

    private var loadedKey: SessionKey? = null

    private var loaded: PaddleOCR? = null

    @Volatile
    private var openClNoticeLogged = false

    fun recognizeText(bitmap: Bitmap, options: OcrOptions): List<String> {
        return run(bitmap, options).map { it.text }
    }

    fun detect(bitmap: Bitmap, options: OcrOptions): List<OcrResult> {
        return run(bitmap, options).map { it.toOcrResult() }
    }

    fun release() {
        synchronized(lock) {
            try {
                loaded?.release()
            } finally {
                loaded = null
                loadedKey = null
            }
        }
    }

    private fun run(bitmap: Bitmap, options: OcrOptions): List<OCRResult> {
        if (options.useOpenCL && !openClNoticeLogged) {
            openClNoticeLogged = true
            Log.i(TAG, "useOpenCL is ignored by the embedded ONNX Runtime engine (CPU execution provider)")
        }
        val key = sessionKeyOf(options, slimAvailable)
        val config = runConfigOf(options)
        // Sessions are not shared across threads while the cached engine may be swapped.
        // zh-CN: 缓存的引擎可能被替换, 因此会话不跨线程并发使用.
        synchronized(lock) {
            return obtain(key).recognize(bitmap, config).results
        }
    }

    private fun obtain(key: SessionKey): PaddleOCR {
        val cached = loaded
        if (cached != null && loadedKey == key) return cached
        release()
        nativeRuntimeLoader()
        val root = if (key.slim) OnnxPaddleOcrAssets.SLIM_ROOT else OnnxPaddleOcrAssets.ROOT
        return PaddleOCR.create(
            context = appContext,
            config = PaddleOCRConfig(),
            engineConfig = EngineConfig(numThreads = key.threads),
            detModelAssetPath = "$root/${OnnxPaddleOcrAssets.DET_MODEL}",
            recModelAssetPath = "$root/${OnnxPaddleOcrAssets.REC_MODEL}",
            // The character dictionary and the classifier are shared by both precisions.
            // zh-CN: 字典与方向分类器两种精度共用.
            recConfigAssetPath = "${OnnxPaddleOcrAssets.ROOT}/${OnnxPaddleOcrAssets.REC_CONFIG}",
            clsModelAssetPath = if (key.useCls) "${OnnxPaddleOcrAssets.ROOT}/${OnnxPaddleOcrAssets.CLS_MODEL}" else null,
        ).also {
            loaded = it
            loadedKey = key
        }
    }

    /**
     * Options that need a new set of sessions when they change.
     * zh-CN: 变化时需要重建会话的选项.
     */
    data class SessionKey(
        val slim: Boolean,
        val threads: Int,
        val useCls: Boolean,
    )

    companion object {

        private const val TAG = "OnnxPaddleOcrEngine"

        const val DEFAULT_DET_LIMIT_SIDE_LEN = 960

        fun sessionKeyOf(options: OcrOptions, slimAvailable: Boolean): SessionKey = SessionKey(
            // useSlim falls back to fp32 when the slim models are not packaged.
            // zh-CN: slim 模型未打包时 useSlim 回退到 fp32.
            slim = options.useSlim && slimAvailable,
            threads = options.cpuThreadNum.coerceAtLeast(0),
            // The classifier only runs when the script asks for it.
            // zh-CN: 仅在脚本要求时运行方向分类.
            useCls = options.extras?.getBoolean(PaddleOcrOptionExtraKeys.USE_CLS, false) == true,
        )

        /**
         * Per-call settings (D25): det `limit_side_len` from extras, then `detLongSize`, then 960
         * with `limit_type = max`; recognition score filter from `scoreThreshold` (default 0).
         * zh-CN: 每次调用的设置 (D25): det `limit_side_len` 依次取 extras, `detLongSize`, 960,
         * `limit_type = max`; 识别分数过滤取 `scoreThreshold` (默认 0).
         */
        fun runConfigOf(options: OcrOptions): PaddleOCRConfig {
            val extraLimit = options.extras?.getInt(PaddleOcrOptionExtraKeys.DET_LIMIT_SIDE_LEN, 0) ?: 0
            val limitSideLen = when {
                extraLimit > 0 -> extraLimit
                options.detLongSize > 0 -> options.detLongSize
                else -> DEFAULT_DET_LIMIT_SIDE_LEN
            }
            return PaddleOCRConfig(
                detLimitSideLen = limitSideLen,
                detLimitType = "max",
                detThresh = 0.3f,
                detBoxThresh = 0.5f,
                detUnclipRatio = 1.6f,
                recScoreThresh = options.scoreThreshold.takeIf { it >= 0f } ?: 0f,
            )
        }

        private fun OCRResult.toOcrResult(): OcrResult {
            val points = box.points
            val quad = FloatArray(points.size * 2)
            points.forEachIndexed { index, point ->
                quad[index * 2] = point.x
                quad[index * 2 + 1] = point.y
            }
            return OcrResult().also { result ->
                result.text = text
                result.confidence = confidence
                result.bounds = Rect(
                    floor(points.minOf { it.x }).toInt(),
                    floor(points.minOf { it.y }).toInt(),
                    ceil(points.maxOf { it.x }).toInt(),
                    ceil(points.maxOf { it.y }).toInt(),
                )
                result.extras = Bundle().apply { putFloatArray(EXTRA_QUAD, quad) }
            }
        }

        const val EXTRA_QUAD = "quad"
    }
}

/**
 * Asset layout shared with the ONNX Runtime Paddle OCR plugin (PP-OCRv3 2.0.0, every language flavor).
 * zh-CN: 与 ONNX Runtime 版 Paddle OCR 插件 (PP-OCRv3 2.0.0, 各语种 flavor) 一致的资产布局.
 */
object OnnxPaddleOcrAssets {

    const val ROOT = "models/ppocrv3"

    const val SLIM_ROOT = "models/ppocrv3-slim"

    const val DET_MODEL = "det/inference.onnx"

    const val REC_MODEL = "rec/inference.onnx"

    const val REC_CONFIG = "rec/inference.yml"

    const val CLS_MODEL = "cls/inference.onnx"

    val requiredAssets = listOf(
        "$ROOT/$DET_MODEL",
        "$ROOT/$REC_MODEL",
        "$ROOT/$REC_CONFIG",
        "$ROOT/$CLS_MODEL",
    )

    val slimAssets = listOf(
        "$SLIM_ROOT/$DET_MODEL",
        "$SLIM_ROOT/$REC_MODEL",
    )

    val nativeLibraries = listOf(
        "libonnxruntime.so",
        "libonnxruntime4j_jni.so",
    )
}
