// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.autojs.plugin.paddle.ocr.onnx.engine

import android.content.Context
import android.graphics.Bitmap
import org.autojs.plugin.paddle.ocr.onnx.EngineConfig
import org.autojs.plugin.paddle.ocr.onnx.PaddleOCRConfig
import org.autojs.plugin.paddle.ocr.onnx.model.ModelConfig
import org.autojs.plugin.paddle.ocr.onnx.model.OCRError
import org.autojs.plugin.paddle.ocr.onnx.model.OCRResult
import org.autojs.plugin.paddle.ocr.onnx.postprocess.BoxSorter
import org.autojs.plugin.paddle.ocr.onnx.postprocess.QuadTextCrop
import org.autojs.plugin.paddle.ocr.onnx.util.BitmapUtils

class OCREngine(
    context: Context,
    private val config: PaddleOCRConfig,
    engineConfig: EngineConfig,
    detModelAsset: String = "models/det/inference.onnx",
    recModelAsset: String = "models/rec/inference.onnx",
    recConfigAsset: String = "models/rec/inference.yml",
    clsModelAsset: String? = null,
) {
    private val ortManager = ORTSessionManager(context, engineConfig)
    private val detectionEngine: DetectionEngine
    private val recognitionEngine: RecognitionEngine
    private val classificationEngine: ClassificationEngine?
    val coldLoadTimeMs: Long get() = ortManager.coldLoadTimeMs

    init {
        val configured = try {
            ortManager.loadModels(detModelAsset, recModelAsset, clsModelAsset)
            val recModelConfig = ModelConfig.parse(context, recConfigAsset)
            recModelConfig
        } catch (t: Throwable) {
            ortManager.release()
            throw t
        }
        detectionEngine = DetectionEngine(ortManager, config)
        recognitionEngine = RecognitionEngine(ortManager, configured.characterList)
        classificationEngine = if (ortManager.hasClassifier) ClassificationEngine(ortManager) else null
    }

    /** [config] overrides the construction-time settings for this call only (models are not reloaded). */
    fun run(bitmap: Bitmap, config: PaddleOCRConfig = this.config): OCREngineResult {
        val srcMat = BitmapUtils.bitmapToBGRMat(bitmap)
        return runWithOwnedMat(srcMat, config)
    }

    fun run(imageBytes: ByteArray, config: PaddleOCRConfig = this.config): OCREngineResult {
        val srcMat = BitmapUtils.imdecodeBGR(imageBytes)
        if (srcMat.empty()) {
            srcMat.release()
            throw OCRError.InvalidImage()
        }
        return runWithOwnedMat(srcMat, config)
    }

    private fun runWithOwnedMat(srcMat: org.opencv.core.Mat, config: PaddleOCRConfig): OCREngineResult {
        return try {
            run(srcMat, config)
        } finally {
            srcMat.release()
        }
    }

    private fun run(srcMat: org.opencv.core.Mat, config: PaddleOCRConfig): OCREngineResult {
        val totalStart = System.currentTimeMillis()
        val detResult = detectionEngine.detect(srcMat, config)
        val boxes = detResult.boxes

        if (boxes.isEmpty()) {
            val elapsed = System.currentTimeMillis() - totalStart
            return OCREngineResult(
                results = emptyList(),
                detectionTimeMs = detResult.timeMs,
                recognitionTimeMs = 0,
                totalTimeMs = elapsed,
                lineCount = 0,
                detPreprocessMs = detResult.preprocessMs,
                detInferenceMs = detResult.inferenceMs,
                detPostprocessMs = detResult.postprocessMs,
                detInputShape = detResult.inputShape,
                coldLoadTimeMs = ortManager.coldLoadTimeMs,
            )
        }

        // 2. Sort boxes
        val sortedBoxes = BoxSorter.sortInReadingOrder(boxes)

        // 3. Crop and recognize text regions
        var totalRecPreMs = 0L
        var totalRecInfMs = 0L
        var totalRecPostMs = 0L
        var totalRecMs = 0L
        val allResults = mutableListOf<OCRResult>()
        val recInputShapes = mutableListOf<List<Int>>()
        val perLineRecMs = mutableListOf<Long>()
        val batchSize = config.recBatchSize.coerceAtLeast(1)

        var i = 0
        while (i < sortedBoxes.size) {
            val batchCrops = mutableListOf<org.opencv.core.Mat>()
            val batchBoxIndices = mutableListOf<Int>()
            var next = i
            while (next < sortedBoxes.size && batchCrops.size < batchSize) {
                val crop = QuadTextCrop.crop(srcMat, sortedBoxes[next])
                if (crop.rows() > 0 && crop.cols() > 0) {
                    batchCrops.add(crop)
                    batchBoxIndices.add(next)
                } else {
                    crop.release()
                }
                next++
            }

            try {
                if (batchCrops.isNotEmpty()) {
                    // det -> (cls) -> rec: upside-down lines are turned upright before recognition.
                    // zh-CN: det -> (cls) -> rec: 倒置的文本行在识别前转正.
                    classificationEngine?.rotateUpsideDown(batchCrops)
                    val batchResult = recognitionEngine.recognize(batchCrops)
                    totalRecPreMs += batchResult.preprocessMs
                    totalRecInfMs += batchResult.inferenceMs
                    totalRecPostMs += batchResult.postprocessMs
                    totalRecMs += batchResult.timeMs
                    recInputShapes.add(batchResult.inputShape)
                    if (batchSize == 1) {
                        perLineRecMs.add(batchResult.timeMs)
                    }

                    for (j in batchResult.texts.indices) {
                        val boxIdx = batchBoxIndices[j]
                        val (text, confidence) = batchResult.texts[j]
                        if (confidence >= config.recScoreThresh) {
                            allResults.add(
                                OCRResult(
                                    box = sortedBoxes[boxIdx],
                                    text = text,
                                    confidence = confidence,
                                )
                            )
                        }
                    }
                }
            } finally {
                batchCrops.forEach { it.release() }
            }
            i = next
        }

        val totalElapsed = System.currentTimeMillis() - totalStart
        val pipelineOverhead = totalElapsed - detResult.timeMs - totalRecMs

        return OCREngineResult(
            results = allResults,
            detectionTimeMs = detResult.timeMs,
            recognitionTimeMs = totalRecMs,
            totalTimeMs = totalElapsed,
            lineCount = allResults.size,
            detPreprocessMs = detResult.preprocessMs,
            detInferenceMs = detResult.inferenceMs,
            detPostprocessMs = detResult.postprocessMs,
            recPreprocessMs = totalRecPreMs,
            recInferenceMs = totalRecInfMs,
            recPostprocessMs = totalRecPostMs,
            pipelineOverheadMs = pipelineOverhead,
            coldLoadTimeMs = ortManager.coldLoadTimeMs,
            detInputShape = detResult.inputShape,
            recInputShapes = recInputShapes,
            perLineRecMs = perLineRecMs,
        )
    }

    fun release() {
        ortManager.release()
    }
}
