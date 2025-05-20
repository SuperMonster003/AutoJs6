package org.autojs.autojs.util

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object ImageUtils {

    @JvmStatic
    fun Mat.to8UC3(): Mat {
        val src = this

        // Convert bit depth to 8 bit (zh-CN: 位深转换为 8 位)
        var tmp8 = Mat()
        // e.g. CV_16U, CV_32F, etc (zh-CN: 例如 CV_16U, CV_32F 等)
        val depth = src.depth()

        if (depth != CvType.CV_8U) {
            // Automatically calculate alpha/beta to map results to 0-255.
            // zh-CN: 自动计算 alpha/beta, 使结果映射到 0-255.
            val mm = Core.minMaxLoc(src)
            val minV = mm.minVal
            val maxV = mm.maxVal
            // Avoid division by zero (zh-CN: 避免除以 0)
            val alpha = if ((maxV - minV) < 1e-5) 1.0 else 255.0 / (maxV - minV)
            val beta = -minV * alpha

            // tmp8 is now 8-bit (zh-CN: 此时的 tmp8 为 8 位)
            src.convertTo(tmp8, CvType.CV_8U, alpha, beta)
        } else {
            // Already 8-bit, continue using (zh-CN: 已经是 8 位, 继续使用)
            tmp8 = src
        }

        // Convert channel count to 3 (zh-CN: 通道数转换为 3)
        val ch = tmp8.channels()
        var dst = Mat()

        when (ch) {
            1 -> Imgproc.cvtColor(tmp8, dst, Imgproc.COLOR_GRAY2BGR)
            3 -> dst = tmp8.clone()
            4 -> Imgproc.cvtColor(tmp8, dst, Imgproc.COLOR_BGRA2BGR)
            else -> throw IllegalArgumentException("Unsupported channel count: $ch")
        }
        return dst
    }

}