package org.autojs.plugin.paddle.ocr.onnx.preprocess

import kotlin.math.ceil
import kotlin.math.max

/**
 * Input geometry of the PP-OCRv3 text-line models, following PaddleOCR 2.7 `resize_norm_img`:
 * recognition uses `rec_image_shape = 3,48,320` and widens the batch to the widest crop,
 * classification uses `cls_image_shape = 3,48,192`. Crops keep their aspect ratio and are
 * right-padded with zeros.
 */
object TextLineResize {
    const val HEIGHT = 48
    const val REC_MIN_WIDTH = 320
    const val CLS_WIDTH = 192

    /** Upper bound for a recognition batch width; PaddleOCR has none, this keeps tensors bounded. */
    const val REC_MAX_WIDTH = 3200

    /** Batch tensor width: `int(48 * max(320 / 48, max(w / h)))`, capped at [REC_MAX_WIDTH]. */
    fun recBatchWidth(sizes: List<Pair<Int, Int>>): Int {
        var maxRatio = REC_MIN_WIDTH.toDouble() / HEIGHT
        for ((width, height) in sizes) {
            require(width > 0 && height > 0) { "Crop size must be positive: ${width}x$height" }
            maxRatio = max(maxRatio, width * 1.0 / height)
        }
        return (HEIGHT * maxRatio).toInt().coerceAtMost(REC_MAX_WIDTH)
    }

    /** Resized crop width inside a tensor of [targetWidth]: `min(targetWidth, ceil(48 * w / h))`. */
    fun resizedWidth(width: Int, height: Int, targetWidth: Int): Int {
        require(width > 0 && height > 0) { "Crop size must be positive: ${width}x$height" }
        val ratio = width / height.toDouble()
        val scaled = ceil(HEIGHT * ratio)
        return if (scaled > targetWidth) targetWidth else scaled.toInt()
    }
}
