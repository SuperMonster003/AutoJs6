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

import android.graphics.Bitmap
import org.autojs.plugin.paddle.ocr.onnx.PaddleOCRConfig
import org.autojs.plugin.paddle.ocr.onnx.model.OCRBox
import org.autojs.plugin.paddle.ocr.onnx.postprocess.DBPostProcessor
import org.autojs.plugin.paddle.ocr.onnx.preprocess.DetPreprocessResult
import org.autojs.plugin.paddle.ocr.onnx.preprocess.DetPreprocessor
import org.opencv.core.Mat

class DetectionEngine(
    private val ortManager: ORTSessionManager,
    private val config: PaddleOCRConfig,
) {
    data class DetectionResult(
        val boxes: List<OCRBox>,
        val preprocessMs: Long,
        val inferenceMs: Long,
        val postprocessMs: Long,
        val timeMs: Long,
        val inputShape: List<Int>,
    )

    fun detect(bitmap: Bitmap): DetectionResult {
        return detect(config) {
            DetPreprocessor.preprocess(
                bitmap,
                config.detLimitSideLen,
                config.detLimitType,
                config.detMaxSideLimit,
                config.detImgMode,
            )
        }
    }

    /** [config] overrides the construction-time settings for this call only. */
    fun detect(src: Mat, config: PaddleOCRConfig = this.config): DetectionResult {
        return detect(config) {
            DetPreprocessor.preprocess(
                src,
                config.detLimitSideLen,
                config.detLimitType,
                config.detMaxSideLimit,
                config.detImgMode,
            )
        }
    }

    private fun detect(config: PaddleOCRConfig, preprocessor: () -> DetPreprocessResult): DetectionResult {
        val preStart = System.currentTimeMillis()
        val preResult = preprocessor()
        val preprocessMs = System.currentTimeMillis() - preStart

        val infStart = System.currentTimeMillis()
        val (outputData, outputShape) = ortManager.runDetection(preResult.tensorData, preResult.shape)
        val inferenceMs = System.currentTimeMillis() - infStart

        val postStart = System.currentTimeMillis()
        val boxes = DBPostProcessor.process(
            pred = outputData,
            predShape = outputShape,
            thresh = config.detThresh,
            boxThresh = config.detBoxThresh,
            unclipRatio = config.detUnclipRatio,
            maxCandidates = config.detMaxCandidates,
            useDilation = config.detUseDilation,
            scoreMode = config.detScoreMode,
            boxType = config.detBoxType,
            originalH = preResult.originalH,
            originalW = preResult.originalW,
        )
        val postprocessMs = System.currentTimeMillis() - postStart

        return DetectionResult(
            boxes = boxes,
            preprocessMs = preprocessMs,
            inferenceMs = inferenceMs,
            postprocessMs = postprocessMs,
            timeMs = preprocessMs + inferenceMs + postprocessMs,
            inputShape = listOf(1, 3, preResult.shape[2].toInt(), preResult.shape[3].toInt()),
        )
    }
}
