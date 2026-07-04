package com.benjaminwan.ocrlibrary

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap

class OcrEngine(context: Context) {
    companion object {
        const val numThread: Int = 4

        @Volatile
        private var nativeLibrariesLoaded = false

        @Synchronized
        private fun ensureNativeLibrariesLoaded() {
            if (nativeLibrariesLoaded) return
            System.loadLibrary("onnxruntime")
            System.loadLibrary("RapidOcr")
            nativeLibrariesLoaded = true
        }
    }

    private data class ModelSpec(
        val variant: String,
        val detName: String,
        val clsName: String,
        val recName: String,
        val keysName: String,
    ) {
        fun isAvailable(assets: AssetManager): Boolean {
            return listOf(detName, clsName, recName, keysName).all { assets.hasAsset(it) }
        }
    }

    private val modelSpec = resolveModelSpec(context.applicationContext.assets)

    init {
        ensureNativeLibrariesLoaded()
        val ret = init(
            context.applicationContext.assets,
            numThread,
            modelSpec.detName,
            modelSpec.clsName,
            modelSpec.recName,
            modelSpec.keysName,
        )
        if (!ret) throw IllegalArgumentException("Failed to initialize Rapid OCR (${modelSpec.variant})")
    }

    var padding: Int = 50
    var boxScoreThresh: Float = 0.5f
    var boxThresh: Float = 0.3f
    var unClipRatio: Float = 1.6f
    var doAngle: Boolean = true
    var mostAngle: Boolean = true

    fun detect(input: Bitmap, output: Bitmap, maxSideLen: Int) =
        detect(
            input, output, padding, maxSideLen,
            boxScoreThresh, boxThresh,
            unClipRatio, doAngle, mostAngle
        )

    external fun init(
        assetManager: AssetManager,
        numThread: Int,
        detName: String,
        clsName: String,
        recName: String,
        keysName: String,
    ): Boolean

    external fun detect(
        input: Bitmap,
        output: Bitmap,
        padding: Int,
        maxSideLen: Int,
        boxScoreThresh: Float,
        boxThresh: Float,
        unClipRatio: Float,
        doAngle: Boolean,
        mostAngle: Boolean,
    ): OcrResult

    external fun benchmark(input: Bitmap, loop: Int): Double

    private fun resolveModelSpec(assets: AssetManager): ModelSpec {
        return listOf(
            ModelSpec(
                variant = "pp-ocrv6",
                detName = "models/PP-OCRv6_det_small.onnx",
                clsName = "models/ch_ppocr_mobile_v2.0_cls_mobile.onnx",
                recName = "models/PP-OCRv6_rec_small.onnx",
                keysName = "models/ppocrv6_dict.txt",
            ),
            ModelSpec(
                variant = "pp-ocrv3",
                detName = "models/ch_PP-OCRv3_det_infer.onnx",
                clsName = "models/ch_ppocr_mobile_v2.0_cls_infer.onnx",
                recName = "models/ch_PP-OCRv3_rec_infer.onnx",
                keysName = "models/ppocr_keys_v1.txt",
            ),
        ).firstOrNull { it.isAvailable(assets) }
            ?: throw IllegalStateException("No embedded Rapid OCR assets found")
    }

}

private fun AssetManager.hasAsset(path: String): Boolean {
    return try {
        open(path).use { true }
    } catch (_: Throwable) {
        false
    }
}
