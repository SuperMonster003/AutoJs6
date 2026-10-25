package org.autojs.autojs.runtime.api.modules.ocr

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import com.baidu.paddle.lite.ocr.PaddleOcrEngine
import com.baidu.paddle.lite.ocr.VariantSpec
import org.autojs.autojs6.R
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.OcrResult

internal object PaddleOcrEmbeddedEngine {

    @Volatile
    private var engine: PaddleOcrEngine? = null

    @Volatile
    private var resolvedVariant: VariantSpec? = null

    fun resolveVariantName(context: Context): String? {
        return resolvedVariant?.name
            ?: resolveVariant(context.applicationContext.assets)?.name
    }

    private fun getEngine(context: Context): PaddleOcrEngine {
        // libNative.so links its own OpenCV privately, so the OpenCV plugin runtime is not loaded here.
        // zh-CN: libNative.so 私有链接自带的 OpenCV, 这里不加载 OpenCV 插件运行时.
        // Lazily initialize embedded engine.
        // zh-CN: 懒初始化内置引擎.
        val cached = engine
        if (cached != null) return cached
        return synchronized(this) {
            engine ?: run {
                val appCtx = context.applicationContext
                val variant = resolveVariantOrThrow(appCtx, appCtx.assets).also { resolvedVariant = it }
                PaddleOcrEngine(
                    appContext = appCtx,
                    variant = variant,
                ).also { engine = it }
            }
        }
    }

    private fun resolveVariantOrThrow(context: Context, assets: AssetManager): VariantSpec {
        // Resolve the first embedded Paddle Lite variant whose required assets are packaged.
        return resolveVariant(assets) ?: throw IllegalStateException(
            context.getString(R.string.error_no_embedded_paddle_ocr_assets_found)
        )
    }

    private fun resolveVariant(assets: AssetManager): VariantSpec? {
        return embeddedVariantCandidates.firstOrNull { candidate ->
            candidate.requiredAssets.all { path -> assets.hasAsset(path) }
        }?.spec
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

    private data class EmbeddedVariantCandidate(
        val spec: VariantSpec,
        val requiredAssets: List<String>,
    )

    private val embeddedVariantCandidates = listOf(
        // Embedded Paddle still uses the legacy Paddle Lite .nb runtime. Newer Paddle OCR
        // plugin variants v4/v5/v6 use ONNX runtime and are selected through OcrPluginHost.
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
