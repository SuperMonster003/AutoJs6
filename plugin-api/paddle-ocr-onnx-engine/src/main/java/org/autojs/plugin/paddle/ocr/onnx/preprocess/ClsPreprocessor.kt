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
package org.autojs.plugin.paddle.ocr.onnx.preprocess

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil

/**
 * Text line direction classifier input: 3 x 48 x 192, aspect ratio kept and right-padded with zeros,
 * BGR as cropped, normalized to (x / 255 - 0.5) / 0.5 (PaddleOCR `TextClassifier.resize_norm_img`).
 * zh-CN: 文字方向分类器输入: 3 x 48 x 192, 保持宽高比并在右侧补零, 通道保持裁剪时的 BGR,
 * 归一化为 (x / 255 - 0.5) / 0.5 (PaddleOCR `TextClassifier.resize_norm_img`).
 */
object ClsPreprocessor {
    const val HEIGHT = 48
    const val WIDTH = 192

    fun preprocessBatch(crops: List<Mat>): RecPreprocessResult {
        val planeSize = HEIGHT * WIDTH
        val tensorData = FloatArray(crops.size * 3 * planeSize)
        crops.forEachIndexed { b, crop ->
            val ratio = if (crop.rows() > 0) crop.cols().toDouble() / crop.rows() else 1.0
            val resizedW = ceil(HEIGHT * ratio).toInt().coerceIn(1, WIDTH)
            val resized = Mat()
            val floatMat = Mat()
            try {
                Imgproc.resize(crop, resized, Size(resizedW.toDouble(), HEIGHT.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
                resized.convertTo(floatMat, CvType.CV_32FC3)
                val pixels = FloatArray(HEIGHT * resizedW * 3)
                floatMat.get(0, 0, pixels)
                for (y in 0 until HEIGHT) {
                    for (x in 0 until resizedW) {
                        val src = (y * resizedW + x) * 3
                        for (c in 0..2) {
                            tensorData[(b * 3 + c) * planeSize + y * WIDTH + x] = pixels[src + c] / 127.5f - 1f
                        }
                    }
                }
            } finally {
                resized.release()
                floatMat.release()
            }
        }
        return RecPreprocessResult(
            tensorData = tensorData,
            shape = longArrayOf(crops.size.toLong(), 3, HEIGHT.toLong(), WIDTH.toLong()),
        )
    }
}
