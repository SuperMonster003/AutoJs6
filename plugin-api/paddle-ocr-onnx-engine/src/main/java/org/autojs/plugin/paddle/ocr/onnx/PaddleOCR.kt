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
package org.autojs.plugin.paddle.ocr.onnx

import android.content.Context
import android.graphics.Bitmap
import org.autojs.plugin.paddle.ocr.onnx.engine.OCREngine
import org.autojs.plugin.paddle.ocr.onnx.engine.OCREngineResult
import org.autojs.plugin.paddle.ocr.onnx.model.OCRError
import org.autojs.plugin.paddle.ocr.onnx.model.OCRRunResult

/**
 * Blocking PP-OCR pipeline. The caller loads the OpenCV native runtime before [create], so the
 * host decides which `libopencv_java4.so` is trusted (see the module README).
 * zh-CN: 阻塞式 PP-OCR 流水线. 调用方在 [create] 之前加载 OpenCV 原生运行库, 由宿主决定信任哪一份 `libopencv_java4.so` (见模块 README).
 */
class PaddleOCR private constructor(
    private val engine: OCREngine,
) {
    /** Time spent loading ONNX models (milliseconds). */
    val coldLoadTimeMs: Long get() = engine.coldLoadTimeMs

    companion object {

        fun create(
            context: Context,
            config: PaddleOCRConfig,
            engineConfig: EngineConfig,
            detModelAssetPath: String,
            recModelAssetPath: String,
            recConfigAssetPath: String,
            clsModelAssetPath: String? = null,
        ): PaddleOCR {
            val engine = OCREngine(
                context.applicationContext, config, engineConfig,
                detModelAsset = detModelAssetPath,
                recModelAsset = recModelAssetPath,
                recConfigAsset = recConfigAssetPath,
                clsModelAsset = clsModelAssetPath,
            )
            return PaddleOCR(engine)
        }
    }

    /**
     * Runs det -> (cls) -> rec. [config] overrides the creation-time detection and score settings
     * for this call; models are not reloaded.
     * zh-CN: 执行 det -> (cls) -> rec. [config] 仅对本次调用覆盖创建时的检测与分数设置, 不重新加载模型.
     */
    fun recognize(bitmap: Bitmap, config: PaddleOCRConfig? = null): OCRRunResult {
        if (bitmap.width == 0 || bitmap.height == 0) {
            throw OCRError.InvalidImage()
        }
        return (if (config == null) engine.run(bitmap) else engine.run(bitmap, config)).toRunResult()
    }

    fun recognize(imageBytes: ByteArray, config: PaddleOCRConfig? = null): OCRRunResult {
        if (imageBytes.isEmpty()) {
            throw OCRError.InvalidImage()
        }
        return (if (config == null) engine.run(imageBytes) else engine.run(imageBytes, config)).toRunResult()
    }

    private fun OCREngineResult.toRunResult() = OCRRunResult(
        results = results,
        detectionTimeMs = detectionTimeMs,
        recognitionTimeMs = recognitionTimeMs,
        totalTimeMs = totalTimeMs,
        lineCount = lineCount,
        detPreprocessMs = detPreprocessMs,
        detInferenceMs = detInferenceMs,
        detPostprocessMs = detPostprocessMs,
        recPreprocessMs = recPreprocessMs,
        recInferenceMs = recInferenceMs,
        recPostprocessMs = recPostprocessMs,
        pipelineOverheadMs = pipelineOverheadMs,
        coldLoadTimeMs = coldLoadTimeMs,
        detInputShape = detInputShape,
        recInputShapes = recInputShapes,
        perLineRecMs = perLineRecMs,
    )

    fun release() {
        engine.release()
    }
}
