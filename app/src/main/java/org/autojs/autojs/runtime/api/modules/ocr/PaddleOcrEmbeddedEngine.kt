package org.autojs.autojs.runtime.api.modules.ocr

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import com.baidu.paddle.lite.ocr.PaddleOcrEngine
import com.baidu.paddle.lite.ocr.VariantSpec
import org.autojs.autojs.core.plugin.opencv.OpenCvPluginHost
import org.autojs.autojs6.R
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.OcrResult
import org.autojs.plugin.paddle.ocr.onnx.OnnxPaddleOcrAssets
import org.autojs.plugin.paddle.ocr.onnx.OnnxPaddleOcrEngine
import java.io.File

internal object PaddleOcrEmbeddedEngine {

    const val ONNX_RUNTIME_VARIANT_NAME = "v3"

    @Volatile
    private var engine: EmbeddedEngine? = null

    @Volatile
    private var resolvedCandidate: Candidate? = null

    fun resolveVariantName(context: Context): String? {
        return when (val candidate = resolvedCandidate ?: resolveCandidate(context.applicationContext)) {
            is Candidate.OnnxRuntime -> ONNX_RUNTIME_VARIANT_NAME
            is Candidate.PaddleLite -> candidate.spec.name
            is Candidate.MissingOnnxRuntimeLibraries, null -> null
        }
    }

    /**
     * Whether the packaged recognizer reads a right-to-left script; the Paddle Lite assets have no such model.
     * zh-CN: 打包的识别模型是否读取从右到左的文字; Paddle Lite 资产没有这类模型.
     */
    fun isRightToLeft(context: Context) = OnnxPaddleOcrAssets.isRightToLeft(context.assets)

    private fun getEngine(context: Context): EmbeddedEngine {
        // Lazily initialize embedded engine.
        // zh-CN: 懒初始化内置引擎.
        val cached = engine
        if (cached != null) return cached
        return synchronized(this) {
            engine ?: run {
                val appCtx = context.applicationContext
                val candidate = resolveCandidate(appCtx).also { resolvedCandidate = it }
                when (candidate) {
                    is Candidate.OnnxRuntime -> {
                        // The ONNX Runtime pipeline uses the OpenCV Java API, so the OpenCV runtime
                        // bundled at packaging time is loaded before the first session.
                        // zh-CN: ONNX Runtime 流水线使用 OpenCV Java API, 因此在首个会话前加载打包时带入的 OpenCV 运行库.
                        val onnx = OnnxPaddleOcrEngine(
                            appContext = appCtx,
                            slimAvailable = candidate.slimAvailable,
                            nativeRuntimeLoader = { OpenCvPluginHost.ensureLoaded(appCtx) },
                        )
                        EmbeddedEngine(
                            recognizeText = onnx::recognizeText,
                            detect = onnx::detect,
                        )
                    }
                    is Candidate.PaddleLite -> {
                        // libNative.so links its own OpenCV privately, so the OpenCV plugin runtime is not loaded here.
                        // zh-CN: libNative.so 私有链接自带的 OpenCV, 这里不加载 OpenCV 插件运行时.
                        val lite = PaddleOcrEngine(
                            appContext = appCtx,
                            variant = candidate.spec,
                        )
                        EmbeddedEngine(
                            recognizeText = lite::recognizeText,
                            detect = lite::detect,
                        )
                    }
                    is Candidate.MissingOnnxRuntimeLibraries -> throw IllegalStateException(
                        appCtx.getString(
                            R.string.error_embedded_paddle_ocr_onnx_runtime_libraries_missing,
                            candidate.libraries.joinToString(", "),
                        )
                    )
                    null -> throw IllegalStateException(
                        appCtx.getString(R.string.error_no_embedded_paddle_ocr_assets_found)
                    )
                }.also { engine = it }
            }
        }
    }

    private fun resolveCandidate(context: Context): Candidate? {
        val assets = context.assets
        val nativeLibraries = File(context.applicationInfo.nativeLibraryDir).list()?.toSet().orEmpty()
        return resolveCandidate(
            hasAsset = { path -> assets.hasAsset(path) },
            nativeLibraries = nativeLibraries,
        )
    }

    /**
     * Picks the embedded engine from the packaged assets and native libraries. The ONNX Runtime
     * pipeline comes first (PP-OCRv3 2.0.0 plugin, D8 / D20), then the Paddle Lite variants.
     * zh-CN: 按打包进来的资产与原生库选择内嵌引擎. ONNX Runtime 流水线优先 (PP-OCRv3 2.0.0 插件, D8 / D20), 其次是 Paddle Lite 变体.
     */
    @VisibleForTesting
    internal fun resolveCandidate(hasAsset: (String) -> Boolean, nativeLibraries: Set<String>): Candidate? {
        val onnxAssetsPackaged = hasAsset(ONNX_RUNTIME_MARKER_ASSET)
        val missingOnnxLibraries = OnnxPaddleOcrAssets.nativeLibraries.filterNot { it in nativeLibraries }
        if (onnxAssetsPackaged && missingOnnxLibraries.isEmpty()) {
            return Candidate.OnnxRuntime(
                // useSlim falls back to fp32 when the slim models are not packaged.
                // zh-CN: slim 模型未打包时 useSlim 回退到 fp32.
                slimAvailable = OnnxPaddleOcrAssets.slimAssets.all(hasAsset),
            )
        }
        embeddedVariantCandidates.firstOrNull { candidate ->
            candidate.requiredAssets.all(hasAsset)
        }?.let { return Candidate.PaddleLite(it.spec) }
        return if (onnxAssetsPackaged) Candidate.MissingOnnxRuntimeLibraries(missingOnnxLibraries) else null
    }

    private fun AssetManager.hasAsset(path: String): Boolean {
        return try {
            open(path).use { true }
        } catch (_: Throwable) {
            false
        }
    }

    fun recognizeText(context: Context, bitmap: Bitmap, options: OcrOptions): List<String> {
        // Run OCR with embedded engine.
        // zh-CN: 使用内置引擎执行 OCR.
        return getEngine(context).recognizeText(bitmap, options)
    }

    fun detect(context: Context, bitmap: Bitmap, options: OcrOptions): List<OcrResult> {
        // Run OCR detection with embedded engine.
        // zh-CN: 使用内置引擎执行 OCR 检测.
        return getEngine(context).detect(bitmap, options)
    }

    internal sealed interface Candidate {

        data class OnnxRuntime(val slimAvailable: Boolean) : Candidate

        data class PaddleLite(val spec: VariantSpec) : Candidate

        data class MissingOnnxRuntimeLibraries(val libraries: List<String>) : Candidate
    }

    private class EmbeddedEngine(
        val recognizeText: (Bitmap, OcrOptions) -> List<String>,
        val detect: (Bitmap, OcrOptions) -> List<OcrResult>,
    )

    private data class EmbeddedVariantCandidate(
        val spec: VariantSpec,
        val requiredAssets: List<String>,
    )

    private const val ONNX_RUNTIME_MARKER_ASSET ="${OnnxPaddleOcrAssets.ROOT}/${OnnxPaddleOcrAssets.DET_MODEL}"

    private val embeddedVariantCandidates = listOf(
        // Paddle Lite .nb runtime of the PP-OCRv3 Paddle Lite plugin (variant "v3-paddle-lite") and the
        // legacy v5 layout. PP-OCRv4 / v5 / v6 plugins are selected through OcrPluginHost only.
        // zh-CN: PP-OCRv3 Paddle Lite 插件 (变体 "v3-paddle-lite") 与旧 v5 布局的 Paddle Lite .nb 运行时. PP-OCRv4 / v5 / v6 插件只经 OcrPluginHost 选择.
        EmbeddedVariantCandidate(
            spec = VariantSpec.v5(),
            requiredAssets = listOf(
                "labels/ppocr_keys_ocrv5.txt",
                "models/pp-ocrv5-arm/PP-OCRv5_mobile_det.nb",
            ),
        ),
        EmbeddedVariantCandidate(
            spec = VariantSpec.v3(),
            requiredAssets = listOf(
                "labels/ppocr_keys_v1.txt",
                "models/ocr_v3_for_cpu/det_opt.nb",
            ),
        ),
    )

}
