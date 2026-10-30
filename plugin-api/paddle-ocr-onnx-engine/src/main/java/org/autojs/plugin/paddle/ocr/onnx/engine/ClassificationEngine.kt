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

import org.autojs.plugin.paddle.ocr.onnx.preprocess.ClsPreprocessor
import org.opencv.core.Core
import org.opencv.core.Mat

/**
 * Text line direction classifier (`ch_ppocr_mobile_v2.0_cls`): two classes, 0 and 180 degrees.
 * A crop read as 180 degrees with a score above [threshold] is turned upright before recognition,
 * as PaddleOCR `TextClassifier` does with `cls_thresh = 0.9`.
 * zh-CN: 文字方向分类器 (`ch_ppocr_mobile_v2.0_cls`): 0 度与 180 度两类. 判为 180 度且分数高于 [threshold] 的裁剪图
 * 在识别前转正, 与 PaddleOCR `TextClassifier` 的 `cls_thresh = 0.9` 一致.
 */
class ClassificationEngine(
    private val ortManager: ORTSessionManager,
    private val threshold: Float = 0.9f,
) {
    /** Replaces upside-down crops in place and releases the originals; returns the elapsed milliseconds. */
    fun rotateUpsideDown(crops: MutableList<Mat>): Long {
        if (crops.isEmpty()) return 0
        val start = System.currentTimeMillis()
        val input = ClsPreprocessor.preprocessBatch(crops)
        val (output, shape) = ortManager.runClassification(input.tensorData, input.shape)
        val classes = shape.getOrNull(1)?.toInt()?.takeIf { it > 1 } ?: return System.currentTimeMillis() - start
        for (i in crops.indices) {
            val upright = output[i * classes]
            val upsideDown = output[i * classes + 1]
            if (upsideDown > upright && upsideDown > threshold) {
                val rotated = Mat()
                Core.rotate(crops[i], rotated, Core.ROTATE_180)
                crops[i].release()
                crops[i] = rotated
            }
        }
        return System.currentTimeMillis() - start
    }
}
