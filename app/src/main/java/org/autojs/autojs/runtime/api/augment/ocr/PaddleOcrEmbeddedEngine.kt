package org.autojs.autojs.runtime.api.augment.ocr

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

    private fun getEngine(context: Context): PaddleOcrEngine {
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
        // Mirror the packaging-time selection rule:
        // - If neither exists -> throw.
        // - If both exist -> v5 first.
        // - If only one exists -> choose it.
        // zh-CN:
        // 复刻打包阶段的选择规则:
        // - 两者都不存在 -> 抛异常.
        // - 两者都存在 -> v5 优先.
        // - 仅存在一个 -> 选择唯一项.
        val hasV5 = assets.hasAsset("labels/ppocr_keys_ocrv5.txt") &&
                assets.hasAsset("models/pp-ocrv5-arm/PP-OCRv5_mobile_det.nb")

        val hasV3 = assets.hasAsset("labels/ppocr_keys_v1.txt") &&
                assets.hasAsset("models/ocr_v3_for_cpu/det_opt.nb")

        return when {
            hasV5 -> VariantSpec.v5()
            hasV3 -> VariantSpec.v3()
            else -> throw IllegalStateException(
                context.getString(R.string.error_no_embedded_paddle_ocr_assets_found)
            )
        }
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

}