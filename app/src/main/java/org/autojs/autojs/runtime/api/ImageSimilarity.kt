package org.autojs.autojs.runtime.api

import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Created by SuperMonster003 on Dec 22, 2023.
 * Transformed by SuperMonster003 on Dec 23, 2023.
 */
@Suppress("unused")
object ImageSimilarity {

    /**
     * PSNR (Peak Signal-to-Noise Ratio) is the ratio of the maximum value
     * of the pixel to the noise (MSE) that affects the quality of the pixels.
     *
     * zh-CN: 峰值信噪比.
     */
    @JvmStatic
    fun psnr(imagesPair: Pair<Mat, Mat>) = psnr(imagesPair.first, imagesPair.second)

    /**
     * PSNR (Peak Signal-to-Noise Ratio) is the ratio of the maximum value
     * of the pixel to the noise (MSE) that affects the quality of the pixels.
     *
     * zh-CN: 峰值信噪比.
     *
     * PSNR 值越高, 表示图像的质量越好, 相比于原始图像受噪声影响越小.
     * 典型 PSNR 值范围:
     * - 大于 40: 高质量, 几乎无损
     * - 30 到 40: 高质量
     * - 20 到 30: 可接受的质量
     * - 小于 20: 低质量
     */
    @JvmStatic
    fun psnr(imgA: Mat, imgB: Mat): Double {
        ensureSameSize(imgA, imgB)
        return Core.PSNR(imgA, imgB)
    }

    /**
     * A legacy implementation of psnr.
     */
    @JvmStatic
    fun psnrLegacy(imgA: Mat, imgB: Mat): Double {

        ensureSameSize(imgA, imgB)

        // Calculate absolute difference |I1 - I2|
        val s1 = Mat()
        Core.absdiff(imgA, imgB, s1)

        // Convert to float (CV_32F) to avoid overflow during squaring
        val s1Float = Mat()
        s1.convertTo(s1Float, CvType.CV_32F)

        // Calculate square |I1 - I2|^2
        val s1Square = Mat()
        Core.multiply(s1Float, s1Float, s1Square)

        // Sum elements per channel
        val s = Core.sumElems(s1Square)

        // Clean up Mat resources
        s1.release()
        s1Float.release()
        s1Square.release()

        // Sum channels
        val sse = sumChannels(s)

        val mse = sse / (imgA.channels() * imgA.total())

        // @Reference by SuperMonster003 on Feb 27, 2024.
        //  ! to https://github.com/opencv/opencv/blob/4.2.0/modules/core/src/norm.cpp#L1262-L1271
        //  ! to https://note.nkmk.me/python-opencv-skimage-numpy-psnr
        return if (mse <= 0) 361.20199909921956 else 20 * log10(255 / sqrt(mse))

        // val diff = Mat()
        // Core.absdiff(imgA, imgB, diff)
        // val squaredDiff = Mat()
        // Core.multiply(diff, diff, squaredDiff)
        // val mseScalar = Core.mean(squaredDiff)
        // val mse = mseScalar.`val`[0]
        // return 10.0 * log10(255.0 * 255.0 / mse)
    }

    /**
     * SSIM: Structural Similarity.
     *
     * zh-CN: 结构相似性指数.
     */
    @JvmStatic
    fun ssim(imagesPair: Pair<Mat, Mat>) = ssim(imagesPair.first, imagesPair.second)

    /**
     * SSIM: Structural Similarity.
     *
     * zh-CN: 结构相似性指数.
     *
     * SSIM 值的范围是 0 到 1:
     * - 1 表示图像完全相同.
     * - 接近 1 表示图像非常相似.
     * - 0.7 到 0.9 表示图像相似但有细微差异.
     * - 0.4 到 0.7 表示图像有明显差异但有一定相似性.
     * - 0.1 到 0.4 表示图像有较大差异, 有少量相似区域.
     * - 0 表示图像完全不同.
     */
    @Suppress("LocalVariableName")
    @JvmOverloads
    @JvmStatic
    fun ssim(imgA: Mat, imgB: Mat, tolerance: Double = 1e-6): Double {
        ensureSameSize(imgA, imgB)

        // Convert images to CV_32F
        val imgAConverted = Mat()
        val imgBConverted = Mat()
        imgA.convertTo(imgAConverted, CvType.CV_32F)
        imgB.convertTo(imgBConverted, CvType.CV_32F)

        // SSIM constants
        val C1 = 6.5025
        val C2 = 58.5225

        val mu1 = Mat()
        val mu2 = Mat()
        val sigma1_2 = Mat()
        val sigma2_2 = Mat()
        val sigma12 = Mat()

        val mu1Square = Mat()
        val mu2Square = Mat()
        val mu1Mu2 = Mat()

        // Gaussian blur
        Imgproc.GaussianBlur(imgAConverted, mu1, Size(11.0, 11.0), 1.5)
        Imgproc.GaussianBlur(imgBConverted, mu2, Size(11.0, 11.0), 1.5)

        Core.multiply(mu1, mu1, mu1Square)
        Core.multiply(mu2, mu2, mu2Square)
        Core.multiply(mu1, mu2, mu1Mu2)

        val sigma1Mat = Mat()
        Core.multiply(imgAConverted, imgAConverted, sigma1Mat)
        Imgproc.GaussianBlur(sigma1Mat, sigma1_2, Size(11.0, 11.0), 1.5)
        Core.subtract(sigma1_2, mu1Square, sigma1_2)

        val sigma2Mat = Mat()
        Core.multiply(imgBConverted, imgBConverted, sigma2Mat)
        Imgproc.GaussianBlur(sigma2Mat, sigma2_2, Size(11.0, 11.0), 1.5)
        Core.subtract(sigma2_2, mu2Square, sigma2_2)

        val sigma12Mat = Mat()
        Core.multiply(imgAConverted, imgBConverted, sigma12Mat)
        Imgproc.GaussianBlur(sigma12Mat, sigma12, Size(11.0, 11.0), 1.5)
        Core.subtract(sigma12, mu1Mu2, sigma12)

        val t1 = Mat()
        val t2 = Mat()
        val t3 = Mat()

        // Solve the error by using Core.multiply
        val mu1Mu2Multiplied = Mat()
        val sigma12Multiplied = Mat()

        Core.multiply(mu1Mu2, Scalar(2.0), mu1Mu2Multiplied)
        Core.multiply(sigma12, Scalar(2.0), sigma12Multiplied)

        Core.add(mu1Mu2Multiplied, Scalar(C1), t1)
        Core.add(sigma12Multiplied, Scalar(C2), t2)
        Core.multiply(t1, t2, t3)

        val t1_2 = Mat()
        Core.add(mu1Square, mu2Square, t1)
        Core.add(t1, Scalar(C1), t1_2)

        val t2_2 = Mat()
        Core.add(sigma1_2, sigma2_2, t2)
        Core.add(t2, Scalar(C2), t2_2)

        val t3_2 = Mat()
        Core.multiply(t1_2, t2_2, t3_2)

        val ssim_map = Mat()
        Core.divide(t3, t3_2, ssim_map)

        var ssimIndex = Core.mean(ssim_map).`val`[0]

        // Adjust result if it's within tolerance range to 1
        if (abs(ssimIndex - 1.0) < tolerance) {
            ssimIndex = 1.0
        }

        // Release all Mats from memory
        listOf(
            imgAConverted, imgBConverted, mu1, mu2, sigma1_2, sigma2_2, sigma12,
            mu1Square, mu2Square, mu1Mu2, sigma1Mat, sigma2Mat, sigma12Mat,
            t1, t2, t3, t1_2, t2_2, t3_2, ssim_map, mu1Mu2Multiplied, sigma12Multiplied
        ).forEach { it.release() }

        return ssimIndex
    }

    /**
     * MSSIM: Mean Structural Similarity.
     *
     * zh-CN: 平均结构相似性指数.
     */
    @JvmStatic
    fun mssim(imagesPair: Pair<Mat, Mat>) = mssim(imagesPair.first, imagesPair.second)

    /**
     * MSSIM: Mean Structural Similarity.
     *
     * zh-CN: 平均结构相似性指数.
     *
     * MSSIM 值的范围是 0 到 1:
     * - 1 表示图像完全相同.
     * - 接近 1 表示图像非常相似.
     * - 0.7 到 0.9 表示图像相似但有细微差异.
     * - 0.4 到 0.7 表示图像有明显差异但有一定相似性.
     * - 0.1 到 0.4 表示图像有较大差异, 有少量相似区域.
     * - 0 表示图像完全不同.
     */
    @JvmStatic
    @JvmOverloads
    fun mssim(imgA: Mat, imgB: Mat, tolerance: Double = 1e-6): Double = ssim(imgA, imgB, tolerance)

    // /**
    //  * Linearizes MSSIM value to a range of [0, 1].
    //  */
    // fun mssimNormalized(imgA: Mat, imgB: Mat, tolerance: Double = 1e-6): Double {
    //     val mssimValue = mssim(imgA, imgB, tolerance)
    //     // 对 MSSIM 值应用线性化处理
    //     return ln(1 + mssimValue).coerceIn(0.0, 1.0)
    // }

    /**
     * Histogram.
     *
     * zh-CN: 直方图.
     */
    @JvmStatic
    fun hist(imagesPair: Pair<Mat, Mat>) = hist(imagesPair.first, imagesPair.second)

    /**
     * Computes the similarity between two images based on their histograms.
     *
     * zh-CN: 计算两幅图像基于直方图的相似度.
     *
     * This function ensures both images have the same size, calculates their histograms,
     * and compares the histograms using correlation.
     *
     * @param imgA First image.
     * @param imgB Second image.
     * @return A value representing the similarity between the two images, where 1 indicates
     * perfect similarity and -1 indicates perfect dissimilarity.
     */
    @JvmStatic
    fun hist(imgA: Mat, imgB: Mat): Double {
        ensureSameSize(imgA, imgB)
        val hist1 = calcHist(imgA)
        val hist2 = calcHist(imgB)
        try {
            return Imgproc.compareHist(hist1, hist2, Imgproc.CV_COMP_CORREL)
        } finally {
            hist1.release()
            hist2.release()
        }
    }

    // /**
    //  * Normalized similarity based on histogram comparison.
    //  *
    //  * zh-CN: 基于直方图比较的规范化相似度.
    //  *
    //  * This function calculates the similarity between two images based on their histograms,
    //  * scaled to the range [0, 1].
    //  * A value of 1 indicates the images are completely identical, while a value of 0 indicates
    //  * they are completely dissimilar. Any negative or zero histogram values will be treated as 0 similarity.
    //  *
    //  * Similarity is computed as:
    //  * \[
    //  * \text{similarity} = \max(0, \text{compareHist\_value})
    //  * \]
    //  *
    //  * @param imgA First image.
    //  * @param imgB Second image.
    //  * @return A value between 0 and 1 representing the normalized similarity between the two images.
    //  * @throws IllegalArgumentException if the images do not have the same size.
    //  */
    // @JvmStatic
    // fun histNormalized(imgA: Mat, imgB: Mat): Double {
    //     // 计算原始的直方图相似度
    //     val histValue = hist(imgA, imgB)
    //
    //     // 将直方图相似度标准化到 0 和 1 之间, 如果 histValue < 0, 则设为 0
    //     val similarity = histValue.coerceAtLeast(0.0)
    //
    //     return similarity
    // }

    /**
     * MSE: Mean Squared Error similarity.
     *
     * zh-CN: 均方差 (相似度).
     */
    @JvmStatic
    fun mse(imagesPair: Pair<Mat, Mat>) = mse(imagesPair.first, imagesPair.second)

    /**
     * MSE: Mean Squared Error similarity.
     *
     * zh-CN: 均方差 (相似度).
     *
     * This function calculates the Mean Squared Error (MSE) between two images.
     *
     * The MSE is a measure of the average squared difference between corresponding pixels
     * in the two images:
     *
     * \[
     * \text{MSE} = \frac{1}{mn} \sum_{i=1}^{m} \sum_{j=1}^{n} \left( I_1(i,j) - I_2(i,j) \right)^2
     * \]
     *
     * where \( I_1(i,j) \) and \( I_2(i,j) \) are the pixel values at position \((i, j)\) in
     * the two images, and \(m\) and \(n\) are the width and height of the images.
     *
     * The result range of MSE depends on the type and range of the image data:
     * - For 8-bit images (pixel values range from 0 to 255):
     *   - Minimum possible value: 0 (when the two images are identical)
     *   - Maximum possible value: 65,025 (255^2, when the pixel values differ as much as possible at each point)
     * - For higher precision images (e.g., 16-bit or 32-bit):
     *   - Minimum possible value: 0 (when the two images are identical)
     *   - Maximum possible value: (max possible pixel value difference)^2
     *
     * The smaller the MSE, the more similar the two images are. Conversely, a larger MSE indicates greater difference.
     *
     * zh-CN:
     *
     * 这个函数计算两个图像之间的均方误差 (MSE).
     *
     * 均方误差是衡量两个图像对应像素间平均平方差的方法:
     *
     * \[
     * \text{MSE} = \frac{1}{mn} \sum_{i=1}^{m} \sum_{j=1}^{n} \left( I_1(i,j) - I_2(i,j) \right)^2
     * \]
     *
     * 其中, \( I_1(i,j) \) 和 \( I_2(i,j) \) 分别表示两个图像在像素 \((i, j)\) 处的值, \(m\) 和 \(n\) 分别是图像的宽度和高度.
     *
     * MSE 的结果范围取决于图像数据的类型和范围:
     * - 对于 8-bit 图像 (像素值范围为 0 到 255):
     *   - 最小可能值: 0 (当两个图像完全相同时)
     *   - 最大可能值: 65,025 (255^2, 当所有像素点的差值最大时)
     * - 对于更高精度的图像 (例如 16-bit 或 32-bit):
     *   - 最小可能值: 0 (当两个图像完全相同时)
     *   - 最大可能值: 图像的最大可能像素值差值的平方
     *
     * MSE 值越小, 两个图像越相似; MSE 值越大, 则差异越大.
     * @param imgA First image.
     * @param imgB Second image.
     * @return The mean squared error between the two images.
     * @throws IllegalArgumentException if the images do not have the same size and type.
     */
    @JvmStatic
    fun mse(imgA: Mat, imgB: Mat): Double {
        // 检查图像尺寸和类型是否相同
        ensureSameSizeAndType(imgA, imgB)

        // 计算两个图像的差值并存储在 diff 中
        val diff = Mat()
        val diffFloat = Mat()
        val squaredDiff = Mat()
        try {
            Core.absdiff(imgA, imgB, diff)  // 计算两个图像之间的绝对差异

            // Square in floating point: squaring 8-bit differences in place saturates at 255.
            // zh-CN: 以浮点计算平方, 8 位差值直接平方会在 255 处饱和截断.
            diff.convertTo(diffFloat, CvType.CV_32F)
            Core.multiply(diffFloat, diffFloat, squaredDiff)

            // 计算平方差的和
            val s = Core.sumElems(squaredDiff)

            // 计算均方误差
            return s.`val`.sum() / (imgA.total() * imgA.channels())
        } finally {
            diff.release()
            diffFloat.release()
            squaredDiff.release()
        }
    }

    // /**
    //  * Normalized Similarity based on MSE.
    //  *
    //  * zh-CN: 基于均方误差 (MSE) 的规范化相似度.
    //  *
    //  * This function calculates the similarity between two images, scaled to the range 0..1.
    //  * A value of 0 indicates completely dissimilar images, while a value of 1 indicates identical images.
    //  * Intermediate values reflect the degree of similarity.
    //  *
    //  * Similarity is computed as:
    //  * \[
    //  * \text{similarity} = e^{-\alpha \cdot \text{MSE}}
    //  * \]
    //  * where \(\alpha\) is a parameter controlling the sensitivity of the similarity measure.
    //  *
    //  * @param imgA First image.
    //  * @param imgB Second image.
    //  * @param alpha Sensitivity parameter (default = 0.013).
    //  * @return A value between 0 and 1 representing the similarity between the two images.
    //  * @throws IllegalArgumentException if the images do not have the same size and type.
    //  */
    // @JvmStatic
    // @JvmOverloads
    // fun mseNormalized(imgA: Mat, imgB: Mat, alpha: Double = 0.013): Double {
    //     // 计算 MSE
    //     val mseValue = mse(imgA, imgB)
    //
    //     // 将 MSE 规范化到 0 到 1 之间
    //     val similarity = exp(-alpha * mseValue)
    //
    //     return similarity
    // }

    /**
     * NCC: Normalized Cross-Correlation.
     *
     * zh-CN: 归一化交叉相关.
     */
    @JvmStatic
    fun ncc(imagesPair: Pair<Mat, Mat>) = ncc(imagesPair.first, imagesPair.second)

    /**
     * NCC: Normalized Cross-Correlation.
     *
     * zh-CN: 归一化交叉相关.
     *
     * This function calculates the Normalized Cross-Correlation (NCC) between two images.
     *
     * The NCC is a measure of how similar two images are, taking values in the range [-1, 1]:
     *
     * \[
     * \text{NCC} = \frac{\sum_{i=1}^{m} \sum_{j=1}^{n} (I_1(i,j) - \mu_1) (I_2(i,j) - \mu_2)}
     * {\sqrt{\sum_{i=1}^{m} \sum_{j=1}^{n} (I_1(i,j) - \mu_1)^2 \sum_{k=1}^{m} \sum_{l=1}^{n} (I_2(k,l) - \mu_2)^2}}
     * \]
     *
     * where \( \mu_1 \) and \( \mu_2 \) are the means of the pixel values in images \( I_1 \) and \( I_2 \), respectively.
     *
     * The closer the NCC value is to 1, the more similar the two images are. A value of -1 indicates perfect negative
     * correlation, and a value of 0 indicates no correlation.
     *
     * zh-CN:
     *
     * 这个函数计算两个图像之间的归一化交叉相关 (NCC).
     *
     * NCC 是衡量两个图像相似度的方法, 取值范围为 [-1, 1]:
     *
     * \[
     * \text{NCC} = \frac{\sum_{i=1}^{m} \sum_{j=1}^{n} (I_1(i,j) - \mu_1) (I_2(i,j) - \mu_2)}
     * {\sqrt{\sum_{i=1}^{m} \sum_{j=1}^{n} (I_1(i,j) - \mu_1)^2 \sum_{k=1}^{m} \sum_{l=1}^{n} (I_2(k,l) - \mu_2)^2}}
     * \]
     *
     * 其中 \( \mu_1 \) 和 \( \mu_2 \) 分别是图像 \( I_1 \) 和 \( I_2 \) 中像素值的均值.
     *
     * NCC 值越接近 1, 两个图像越相似. 值为 -1 表示完全负相关, 值为 0 表示无相关性.
     *
     * @param imgA First image.
     * @param imgB Second image.
     * @return The normalized cross-correlation between the two images.
     * @throws IllegalArgumentException if the images do not have the same size.
     */
    @JvmStatic
    fun ncc(imgA: Mat, imgB: Mat): Double {

        ensureSameSize(imgA, imgB)

        // Step 1: Convert images to single-channel floating point
        // zh-CN: 转换为单通道浮点图 (均值相减后会出现负值, 8 位无符号会截断)
        val imgAGray = toGrayFloat(imgA)
        val imgBGray = toGrayFloat(imgB)
        val imgAZeroMean = Mat()
        val imgBZeroMean = Mat()
        val numeratorMat = Mat()
        val denominatorLeftMat = Mat()
        val denominatorRightMat = Mat()
        try {
            // Step 2: Compute mean of the images
            val meanA = Core.mean(imgAGray).`val`[0]
            val meanB = Core.mean(imgBGray).`val`[0]

            // Step 3: Subtract the mean from all elements
            Core.subtract(imgAGray, Scalar(meanA), imgAZeroMean)
            Core.subtract(imgBGray, Scalar(meanB), imgBZeroMean)

            // Step 4: Compute numerator and denominators for NCC
            Core.multiply(imgAZeroMean, imgBZeroMean, numeratorMat)
            val numerator = Core.sumElems(numeratorMat).`val`[0]

            Core.multiply(imgAZeroMean, imgAZeroMean, denominatorLeftMat)
            val denominatorLeft = Core.sumElems(denominatorLeftMat).`val`[0]

            Core.multiply(imgBZeroMean, imgBZeroMean, denominatorRightMat)
            val denominatorRight = Core.sumElems(denominatorRightMat).`val`[0]

            // Step 5: Calculate NCC
            val denominator = sqrt(denominatorLeft * denominatorRight)
            if (denominator == 0.0) {
                // A flat image has no variance; it correlates perfectly only with another flat image of the same mean.
                // zh-CN: 纯色图像无方差, 仅与均值相同的纯色图像视为完全相关.
                return if (denominatorLeft == 0.0 && denominatorRight == 0.0 && meanA == meanB) 1.0 else 0.0
            }
            val ncc = numerator / denominator

            // Ensure the value is within valid range [-1, 1]
            return ncc.coerceIn(-1.0, 1.0)
        } finally {
            listOf(imgAGray, imgBGray, imgAZeroMean, imgBZeroMean, numeratorMat, denominatorLeftMat, denominatorRightMat).forEach { it.release() }
        }
    }

    // /**
    //  * Normalized Similarity based on NCC.
    //  *
    //  * zh-CN: 基于归一化交叉相关 (NCC) 的规范化相似度.
    //  *
    //  * This function calculates the similarity between two images, scaled to the range 0..1.
    //  * A value of 1 indicates the images are completely identical, while a value of 0 indicates
    //  * they are completely dissimilar. Any negative NCC values will be treated as 0 similarity.
    //  *
    //  * Similarity is computed as:
    //  * \[
    //  * \text{similarity} = \max(0, \text{NCC})
    //  * \]
    //  *
    //  * @param imgA First image.
    //  * @param imgB Second image.
    //  * @return A value between 0 and 1 representing the similarity between the two images.
    //  * @throws IllegalArgumentException if the images do not have the same size.
    //  */
    // @JvmStatic
    // fun nccNormalized(imgA: Mat, imgB: Mat): Double {
    //     // 计算 NCC
    //     val nccValue = ncc(imgA, imgB)
    //
    //     // 将 NCC 规范化到 0 到 1 之间, 如果 NCC < 0, 则设为 0
    //     val similarity = nccValue.coerceAtLeast(0.0)
    //
    //     return similarity
    // }

    @JvmStatic
    fun isEqual(imagesPair: Pair<Mat, Mat>) = isEqual(imagesPair.first, imagesPair.second)

    @JvmStatic
    fun isEqual(imgA: Mat, imgB: Mat): Boolean {
        if (imgA.size() != imgB.size() || imgA.type() != imgB.type()) {
            return false
        }

        val diff = Mat()
        var diffFlat: Mat? = null
        try {
            Core.bitwise_xor(imgA, imgB, diff)

            // View every channel as its own column instead of converting to grayscale, which would
            // weight the channels (and drop alpha) and could round a small difference away to zero.
            // zh-CN: 将各通道展开为独立列而非转灰度, 灰度转换会加权各通道 (并丢弃 alpha), 可能把微小差异舍入为 0.
            diffFlat = if (diff.channels() == 1) diff else diff.reshape(1)
            return Core.countNonZero(diffFlat) == 0
        } finally {
            if (diffFlat != null && diffFlat !== diff) diffFlat.release()
            diff.release()
        }
    }

    private fun ensureSameSize(imgA: Mat, imgB: Mat) {
        if (imgA.size() != imgB.size()) {
            throw Exception(str(R.string.error_images_must_have_the_same_size))
        }
    }

    private fun ensureSameSizeAndType(imgA: Mat, imgB: Mat) {
        if (imgA.size() != imgB.size() || imgA.type() != imgB.type()) {
            throw Exception(str(R.string.error_images_must_have_the_same_size_and_type))
        }
    }

    private fun calcHist(image: Mat): Mat {
        val hist = Mat()
        val histSize = MatOfInt(256) // Number of bins
        val ranges = MatOfFloat(0f, 256f) // Histogram value range
        val channels = MatOfInt(0) // Channel to be measured (0 for grayscale or first color channel)
        val mask = Mat()
        try {
            Imgproc.calcHist(mutableListOf(image), channels, mask, hist, histSize, ranges)
            Core.normalize(hist, hist, 0.0, 1.0, Core.NORM_MINMAX) // Normalize the histogram
        } finally {
            histSize.release()
            ranges.release()
            channels.release()
            mask.release()
        }
        return hist
    }

    /**
     * Converts an image of 1, 3 or 4 channels into a single-channel CV_32F Mat owned by the caller.
     * zh-CN: 将 1, 3 或 4 通道图像转换为由调用方持有的单通道 CV_32F Mat.
     */
    private fun toGrayFloat(image: Mat): Mat {
        val gray = Mat()
        when (image.channels()) {
            1 -> image.convertTo(gray, CvType.CV_32F)
            3, 4 -> Mat().let { tmp ->
                try {
                    Imgproc.cvtColor(image, tmp, if (image.channels() == 3) Imgproc.COLOR_BGR2GRAY else Imgproc.COLOR_BGRA2GRAY)
                    tmp.convertTo(gray, CvType.CV_32F)
                } finally {
                    tmp.release()
                }
            }
            else -> {
                gray.release()
                throw IllegalArgumentException("Unsupported channel count (${image.channels()}) for grayscale conversion")
            }
        }
        return gray
    }

    private fun sumChannels(s: Scalar) = s.`val`.sum()

}
