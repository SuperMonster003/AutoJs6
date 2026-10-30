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

data class RecPreprocessResult(
    val tensorData: FloatArray,
    val shape: LongArray,
)

/**
 * PP-OCRv3 text-line preprocessing (PaddleOCR 2.7): crops stay BGR, are resized to height 48
 * with their aspect ratio kept, normalized to (x / 255 - 0.5) / 0.5 and right-padded with zeros.
 */
object RecPreprocessor {

    /** Recognition batch: the tensor width is [TextLineResize.recBatchWidth] of the crops. */
    fun preprocessBatch(crops: List<Mat>): RecPreprocessResult {
        val targetWidth = TextLineResize.recBatchWidth(crops.map { it.cols() to it.rows() })
        return preprocess(crops, targetWidth)
    }

    /** Orientation batch: every crop goes into a fixed [TextLineResize.CLS_WIDTH] wide tensor. */
    fun preprocessClsBatch(crops: List<Mat>): RecPreprocessResult {
        return preprocess(crops, TextLineResize.CLS_WIDTH)
    }

    private fun preprocess(crops: List<Mat>, targetWidth: Int): RecPreprocessResult {
        val height = TextLineResize.HEIGHT
        val plane = height * targetWidth
        val tensorData = FloatArray(crops.size * 3 * plane)
        crops.forEachIndexed { index, crop ->
            val resizedWidth = TextLineResize.resizedWidth(crop.cols(), crop.rows(), targetWidth)
            writeNormalized(crop, resizedWidth, targetWidth, tensorData, index * 3 * plane)
        }
        return RecPreprocessResult(
            tensorData = tensorData,
            shape = longArrayOf(crops.size.toLong(), 3, height.toLong(), targetWidth.toLong()),
        )
    }

    private fun writeNormalized(crop: Mat, resizedWidth: Int, targetWidth: Int, dest: FloatArray, offset: Int) {
        val height = TextLineResize.HEIGHT
        val resized = Mat()
        val floatMat = Mat()
        try {
            Imgproc.resize(crop, resized, Size(resizedWidth.toDouble(), height.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
            // (x / 255 - 0.5) / 0.5 == x / 127.5 - 1
            resized.convertTo(floatMat, CvType.CV_32FC3, 1.0 / 127.5, -1.0)
            val hwc = FloatArray(height * resizedWidth * 3)
            floatMat.get(0, 0, hwc)
            val plane = height * targetWidth
            for (y in 0 until height) {
                val srcRow = y * resizedWidth * 3
                val dstRow = offset + y * targetWidth
                for (x in 0 until resizedWidth) {
                    val src = srcRow + x * 3
                    dest[dstRow + x] = hwc[src]
                    dest[dstRow + plane + x] = hwc[src + 1]
                    dest[dstRow + 2 * plane + x] = hwc[src + 2]
                }
            }
        } finally {
            floatMat.release()
            resized.release()
        }
    }
}
