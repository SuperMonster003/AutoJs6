package com.baidu.paddle.lite.ocr

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import android.util.Log
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.OcrResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Native bridge based on com.baidu.paddle.lite.ocr.Predictor.
 * zh-CN: 基于 com.baidu.paddle.lite.ocr.Predictor 的 native bridge.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Jan 17, 2026.
 * Modified by SuperMonster003 as of Jan 18, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) as of Feb 13, 2026.
 */
class PredictorNativeBridge : NativeBridge {

    private val predictor = Predictor()

    @Volatile
    private var lastProfileKey: String? = null

    override fun init(context: Context, profile: ModelProfile, checkingBitmap: Bitmap) {

        val desiredThreadNum = profile.cpuThreadNum
        val desiredUseSlim = profile.useSlim
        val desiredUseOpenCL = profile.useOpenCL

        // Compute a simple cache key to avoid redundant re-init.
        // zh-CN: 计算简单缓存 key, 避免重复初始化.
        val key = "${profile.variantName}|t=$desiredThreadNum|slim=$desiredUseSlim|opencl=$desiredUseOpenCL|dir=${profile.modelDir ?: "-"}"
        if (predictor.isLoaded && lastProfileKey == key) return

        // Predictor.init() may do heavy work and should not block main thread.
        // zh-CN: Predictor.init() 可能较耗时, 不应阻塞主线程.
        val ok = if (Looper.getMainLooper() == Looper.myLooper()) {
            val latch = CountDownLatch(1)
            val initResult = booleanArrayOf(false)

            Thread {
                initResult[0] = initInternal(context, profile)
                latch.countDown()
            }.start()

            var interrupted = false
            try {
                val completed = latch.await(60, TimeUnit.SECONDS)
                if (!completed) {
                    interrupted = false
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                interrupted = true
            }
            !interrupted && latch.count == 0L && initResult[0]
        } else {
            initInternal(context, profile)
        }
        if (!ok) {
            throw IllegalStateException(context.getString(R.string.error_failed_to_initialize_paddle_ocr_predictor))
        }

        lastProfileKey = key
    }

    private fun initInternal(context: Context, profile: ModelProfile): Boolean {
        // Ensure cpuThreadNum updates take effect.
        // zh-CN: 确保 cpuThreadNum 更新生效.
        if (predictor.cpuThreadNum != profile.cpuThreadNum) {
            predictor.releaseModel()
            predictor.cpuThreadNum = profile.cpuThreadNum
        }

        // Apply per-variant model file names (both v3/v5).
        // zh-CN: 应用按变体区分的模型文件名 (同时覆盖 v3/v5).
        predictor.detModelFilename = profile.detModelFile
        predictor.recModelFilename = profile.recModelFile
        predictor.clsModelFilename = profile.clsModelFile

        return if (profile.variantName == VariantSpec.NAME_V5) {
            // Use v5 built-in OpenCLGuard + fallback logic.
            // zh-CN: 使用 v5 内置的 OpenCLGuard + fallback 逻辑.
            predictor.init(
                context.applicationContext,
                profile.useSlim,
                profile.useOpenCL,
            )
        } else {
            // v3: OpenCL is ignored at variant level; init by resolved modelDir/labelPath.
            // zh-CN: v3: OpenCL 在变体层被忽略; 按解析后的 modelDir/labelPath 初始化.
            predictor.init(
                context.applicationContext,
                requireNotNull(profile.modelDir) {
                    context.getString(R.string.error_missing_modeldir_for_variant, profile.variantName)
                },
                profile.labelAssetPath,
            )
        }
    }

    override fun recognizeText(bitmap: Bitmap, options: OcrOptions): List<String> {
        applyRuntimeOptions(options)
        val results = predictor.runOcr(bitmap)
        val out = ArrayList<String>(results.size)
        for (r in results) out.add(r.label)

        // Only print summary or first several items to avoid heavy I/O.
        // zh-CN: 仅打印摘要或前若干条, 避免大量 I/O.
        Log.i("PaddleOcrEngine", "recognized ${out.size} items")
        for (i in 0 until minOf(5, out.size)) {
            Log.d("PaddleOcrEngine", "item[$i]: ${out[i]}")
        }
        return out
    }

    override fun detect(bitmap: Bitmap, options: OcrOptions): List<OcrResult> {
        applyRuntimeOptions(options)
        val results = predictor.runOcr(bitmap)
        return results.map { r ->
            OcrResult().apply {
                text = r.label
                confidence = r.confidence
                bounds = r.bounds
                extras = Bundle()
            }
        }
    }

    private fun applyRuntimeOptions(options: OcrOptions) {
        val detLongSize = options.detLongSize
        if (detLongSize > 0) {
            predictor.setDetLongSize(detLongSize)
        }
        val scoreThreshold = options.scoreThreshold
        if (scoreThreshold >= 0f) {
            predictor.setScoreThreshold(scoreThreshold)
        }
    }

}
