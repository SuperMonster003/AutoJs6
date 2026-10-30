package org.autojs.plugin.paddle.ocr.onnx.postprocess

/** Orientation of one text-line crop: [angle] is 0 or 180, [rotate] tells whether to turn it. */
data class ClsDecision(
    val angle: Int,
    val score: Float,
    val rotate: Boolean,
)

/**
 * PaddleOCR 2.7 `ClsPostProcess` for `ch_ppocr_mobile_v2.0_cls`: labels `['0', '180']`, argmax per
 * row (first index wins a tie), and a crop is turned 180 degrees only when the label is 180 and
 * its probability is strictly greater than the threshold (`cls_thresh`, 0.9 by default).
 */
object ClsPostprocessor {
    val LABELS = intArrayOf(0, 180)

    fun decode(output: FloatArray, shape: LongArray, thresh: Float): List<ClsDecision> {
        require(shape.size == 2 && shape[1] == LABELS.size.toLong()) {
            "Unexpected orientation output shape: ${shape.joinToString(prefix = "[", postfix = "]")}"
        }
        val count = shape[0].toInt()
        require(output.size >= count * LABELS.size) { "Orientation output is truncated" }
        return List(count) { row ->
            val base = row * LABELS.size
            var best = 0
            for (label in 1 until LABELS.size) {
                if (output[base + label] > output[base + best]) best = label
            }
            val angle = LABELS[best]
            val score = output[base + best]
            ClsDecision(angle = angle, score = score, rotate = angle == 180 && score > thresh)
        }
    }
}
