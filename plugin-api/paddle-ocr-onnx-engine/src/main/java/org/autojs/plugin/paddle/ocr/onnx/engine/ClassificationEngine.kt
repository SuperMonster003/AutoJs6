package org.autojs.plugin.paddle.ocr.onnx.engine

import org.autojs.plugin.paddle.ocr.onnx.postprocess.ClsDecision
import org.autojs.plugin.paddle.ocr.onnx.postprocess.ClsPostprocessor
import org.autojs.plugin.paddle.ocr.onnx.preprocess.RecPreprocessor
import org.opencv.core.Core
import org.opencv.core.Mat

/** Text-line orientation stage between detection and recognition (PaddleOCR 2.7 `TextClassifier`). */
class ClassificationEngine(
    private val ortManager: ORTSessionManager,
    private val thresh: Float,
    batchSize: Int,
) {
    private val batchSize = batchSize.coerceAtLeast(1)

    data class ClassificationResult(
        val decisions: List<ClsDecision>,
        val timeMs: Long,
    )

    /** Classifies [crops] and turns the ones labelled 180 in place, releasing the replaced mats. */
    fun classifyAndRotate(crops: MutableList<Mat>): ClassificationResult {
        val start = System.currentTimeMillis()
        val decisions = ArrayList<ClsDecision>(crops.size)
        var begin = 0
        while (begin < crops.size) {
            val end = minOf(begin + batchSize, crops.size)
            val input = RecPreprocessor.preprocessClsBatch(crops.subList(begin, end))
            val (output, shape) = ortManager.runClassification(input.tensorData, input.shape)
            ClsPostprocessor.decode(output, shape, thresh).forEachIndexed { offset, decision ->
                if (decision.rotate) {
                    val index = begin + offset
                    val rotated = Mat()
                    Core.rotate(crops[index], rotated, Core.ROTATE_180)
                    crops[index].release()
                    crops[index] = rotated
                }
                decisions += decision
            }
            begin = end
        }
        return ClassificationResult(decisions, System.currentTimeMillis() - start)
    }
}
