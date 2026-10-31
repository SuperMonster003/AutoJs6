package org.autojs.plugin.paddle.ocr.onnx

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.OcrResult
import org.autojs.plugin.paddle.ocr.api.PaddleOcrOptionExtraKeys
import org.autojs.plugin.paddle.ocr.onnx.model.ModelConfig
import org.autojs.plugin.paddle.ocr.onnx.model.OCRResult
import org.autojs.plugin.paddle.ocr.onnx.model.OCRRunResult
import java.io.IOException
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

    fun recognizeText(bitmap: Bitmap, options: OcrOptions): List<String> {
        return run(bitmap, options).results.map { it.text }
    }

    fun detect(bitmap: Bitmap, options: OcrOptions): List<OcrResult> {
        val runResult = run(bitmap, options)
        return runResult.results.map { it.toOcrResult(runResult.executionProvider) }
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

    private fun run(bitmap: Bitmap, options: OcrOptions): OCRRunResult {
        val key = sessionKeyOf(options, slimAvailable)
        val config = runConfigOf(options)
        // Sessions are not shared across threads while the cached engine may be swapped.
        // zh-CN: 缓存的引擎可能被替换, 因此会话不跨线程并发使用.
        synchronized(lock) {
            return obtain(key).recognize(bitmap, config)
        }
    }

    private fun obtain(key: SessionKey): PaddleOCR {
        val cached = loaded
        if (cached != null && loadedKey == key) return cached
        release()
        nativeRuntimeLoader()
        val root = if (key.slim) OnnxPaddleOcrAssets.SLIM_ROOT else OnnxPaddleOcrAssets.ROOT
        // The model directory shows whether useSlim took effect in the packaged application (paddle-ocr-v3 P3.5).
        // zh-CN: 模型目录表明 useSlim 在打包应用中是否生效 (paddle-ocr-v3 P3.5).
        Log.i(TAG, "Loading $root (threads ${key.threads}, nnapi ${if (key.useNnapi) "requested" else "off"}, cls ${if (key.useCls) "on" else "off"})")
        return PaddleOCR.create(
            context = appContext,
            config = PaddleOCRConfig(useCls = key.useCls),
            engineConfig = EngineConfig(numThreads = key.threads, useNnapi = key.useNnapi),
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
        val useNnapi: Boolean,
        val useCls: Boolean,
    )

    companion object {

        private const val TAG = "OnnxPaddleOcrEngine"

        const val DEFAULT_THREADS = 4

        const val DEFAULT_DET_LIMIT_SIDE_LEN = 960

        const val DEFAULT_REC_SCORE_THRESH = 0.1f

        // Detection tuning extras, same keys and defaults as the plugin (and the v4 engine).
        // zh-CN: 检测调参 extras, 键名与缺省值与插件 (及 v4 引擎) 相同.
        const val EXTRA_DET_LIMIT_TYPE = "detLimitType"
        const val EXTRA_DET_MAX_SIDE_LIMIT = "detMaxSideLimit"
        const val EXTRA_DET_THRESH = "detThresh"
        const val EXTRA_DET_BOX_THRESH = "detBoxThresh"
        const val EXTRA_DET_UNCLIP_RATIO = "detUnclipRatio"
        const val EXTRA_REC_BATCH_SIZE = "recBatchSize"

        fun sessionKeyOf(options: OcrOptions, slimAvailable: Boolean): SessionKey = SessionKey(
            // useSlim falls back to fp32 when the slim models are not packaged.
            // zh-CN: slim 模型未打包时 useSlim 回退到 fp32.
            slim = options.useSlim && slimAvailable,
            // 0 or a negative count uses 4 threads, the default of the Paddle Lite v3 engine.
            // zh-CN: 0 或负数使用 4 个线程, 与 Paddle Lite 版 v3 引擎的缺省值一致.
            threads = options.cpuThreadNum.takeIf { it > 0 } ?: DEFAULT_THREADS,
            // useOpenCL requests the NNAPI execution provider; sessions fall back to CPU when it is unavailable.
            // zh-CN: useOpenCL 请求 NNAPI 执行提供器; 不可用时会话回退到 CPU.
            useNnapi = options.useOpenCL,
            // The classifier only runs when the script asks for it.
            // zh-CN: 仅在脚本要求时运行方向分类.
            useCls = options.extras?.getBoolean(PaddleOcrOptionExtraKeys.USE_CLS, false) == true,
        )

        /**
         * Per-call settings (D25): det `limit_side_len` from extras, then `detLongSize`, then 960;
         * det tuning from the [EXTRA_DET_LIMIT_TYPE] family of extras; recognition score filter from
         * `scoreThreshold`, 0.1 when the host leaves it unset (-1) as the Paddle Lite v3 engine does.
         * zh-CN: 每次调用的设置 (D25): det `limit_side_len` 依次取 extras, `detLongSize`, 960;
         * det 调参取 [EXTRA_DET_LIMIT_TYPE] 等 extras; 识别分数过滤取 `scoreThreshold`, 宿主未设置 (-1) 时为 0.1, 与 Paddle Lite 版 v3 引擎一致.
         */
        fun runConfigOf(options: OcrOptions): PaddleOCRConfig {
            val extras = options.extras
            val extraLimit = extras?.getInt(PaddleOcrOptionExtraKeys.DET_LIMIT_SIDE_LEN, 0) ?: 0
            val limitSideLen = when {
                extraLimit > 0 -> extraLimit
                options.detLongSize > 0 -> options.detLongSize
                else -> DEFAULT_DET_LIMIT_SIDE_LEN
            }
            val defaults = PaddleOCRConfig(detBoxThresh = 0.5f, detUnclipRatio = 1.6f)
            return defaults.copy(
                detLimitSideLen = limitSideLen,
                detLimitType = extras?.getString(EXTRA_DET_LIMIT_TYPE) ?: defaults.detLimitType,
                detMaxSideLimit = extras?.getInt(EXTRA_DET_MAX_SIDE_LIMIT, 0)?.takeIf { it > 0 } ?: defaults.detMaxSideLimit,
                detThresh = extras?.getFloat(EXTRA_DET_THRESH, defaults.detThresh) ?: defaults.detThresh,
                detBoxThresh = extras?.getFloat(EXTRA_DET_BOX_THRESH, defaults.detBoxThresh) ?: defaults.detBoxThresh,
                detUnclipRatio = extras?.getFloat(EXTRA_DET_UNCLIP_RATIO, defaults.detUnclipRatio) ?: defaults.detUnclipRatio,
                recScoreThresh = options.scoreThreshold.takeIf { it >= 0f } ?: DEFAULT_REC_SCORE_THRESH,
                recBatchSize = (extras?.getInt(EXTRA_REC_BATCH_SIZE, 1) ?: 1).coerceAtLeast(1),
            )
        }

        private fun OCRResult.toOcrResult(executionProvider: String): OcrResult {
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
                result.extras = Bundle().apply {
                    putFloatArray(EXTRA_QUAD, quad)
                    putString(EXTRA_EXECUTION_PROVIDER, executionProvider)
                }
            }
        }

        const val EXTRA_QUAD = "quad"

        /** `cpu` or `nnapi`, the execution provider the sessions actually run on (same key as the plugin). */
        const val EXTRA_EXECUTION_PROVIDER = "executionProvider"
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

    /**
     * Whether the recognizer in [assets] reads a right-to-left script, by the switch the engine itself reads. The host's
     * line merge asks this of the packaged models and of an installed plugin with this layout alike.
     * zh-CN: [assets] 中的识别模型是否读取从右到左的文字, 依据引擎自身读取的开关. 宿主的行合并对打包的模型与采用此布局的已安装插件都这样判定.
     */
    fun isRightToLeft(assets: AssetManager): Boolean {
        return try {
            assets.open("$ROOT/$REC_CONFIG").bufferedReader().use { ModelConfig.readReverse(it.readText()) }
        } catch (_: IOException) {
            false
        }
    }
}
