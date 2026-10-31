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
    detModelAsset: String = "models/ppocrv3/det/inference.onnx",
    recModelAsset: String = "models/ppocrv3/rec/inference.onnx",
    recConfigAsset: String = "models/ppocrv3/rec/inference.yml",
    clsModelAsset: String? = null,
) {
    private val ortManager = ORTSessionManager(context, engineConfig)
    private val detectionEngine: DetectionEngine
    private val recognitionEngine: RecognitionEngine
    private val classificationEngine: ClassificationEngine?
    private val rightToLeft: Boolean
    val coldLoadTimeMs: Long get() = ortManager.coldLoadTimeMs
    val executionProvider: String get() = ortManager.executionProvider

    init {
        val configured = try {
            // The orientation model is only loaded when the stage is enabled.
            ortManager.loadModels(detModelAsset, recModelAsset, clsModelAsset?.takeIf { config.useCls })
            val recModelConfig = ModelConfig.parse(context, recConfigAsset)
            recModelConfig
        } catch (t: Throwable) {
            ortManager.release()
            throw t
        }
        detectionEngine = DetectionEngine(ortManager, config)
        recognitionEngine = RecognitionEngine(ortManager, configured.characterList, configured.reverse)
        rightToLeft = configured.reverse
        classificationEngine = if (ortManager.hasClassifier) {
            ClassificationEngine(ortManager, config.clsThresh, config.clsBatchSize)
        } else {
            null
        }
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
                executionProvider = ortManager.executionProvider,
            )
        }

        // 2. Sort boxes
        val sortedBoxes = BoxSorter.sortInReadingOrder(boxes, rightToLeft)

        // 3. Crop every text region, classify orientation, then recognize in batches
        val crops = mutableListOf<org.opencv.core.Mat>()
        val cropBoxIndices = mutableListOf<Int>()
        var totalClsMs = 0L
        var totalRecPreMs = 0L
        var totalRecInfMs = 0L
        var totalRecPostMs = 0L
        var totalRecMs = 0L
        val allResults = mutableListOf<OCRResult>()
        val recInputShapes = mutableListOf<List<Int>>()
        val perLineRecMs = mutableListOf<Long>()
        val batchSize = config.recBatchSize.coerceAtLeast(1)

        try {
            for (index in sortedBoxes.indices) {
                val crop = QuadTextCrop.crop(srcMat, sortedBoxes[index])
                if (crop.rows() > 0 && crop.cols() > 0) {
                    crops.add(crop)
                    cropBoxIndices.add(index)
                } else {
                    crop.release()
                }
            }

            classificationEngine?.let { engine ->
                if (crops.isNotEmpty()) {
                    totalClsMs = engine.classifyAndRotate(crops).timeMs
                }
            }

            var i = 0
            while (i < crops.size) {
                val next = minOf(i + batchSize, crops.size)
                val batchResult = recognitionEngine.recognize(crops.subList(i, next))
                totalRecPreMs += batchResult.preprocessMs
                totalRecInfMs += batchResult.inferenceMs
                totalRecPostMs += batchResult.postprocessMs
                totalRecMs += batchResult.timeMs
                recInputShapes.add(batchResult.inputShape)
                if (batchSize == 1) {
                    perLineRecMs.add(batchResult.timeMs)
                }

                for (j in batchResult.texts.indices) {
                    val boxIdx = cropBoxIndices[i + j]
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
                i = next
            }
        } finally {
            crops.forEach { it.release() }
        }

        val totalElapsed = System.currentTimeMillis() - totalStart
        val pipelineOverhead = totalElapsed - detResult.timeMs - totalClsMs - totalRecMs

        return OCREngineResult(
            results = allResults,
            detectionTimeMs = detResult.timeMs,
            classificationTimeMs = totalClsMs,
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
            executionProvider = ortManager.executionProvider,
            detInputShape = detResult.inputShape,
            recInputShapes = recInputShapes,
            perLineRecMs = perLineRecMs,
        )
    }

    fun release() {
        ortManager.release()
    }
}
