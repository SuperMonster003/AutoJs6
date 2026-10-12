package org.autojs.autojs.core.image

import androidx.annotation.ColorInt
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.autojs.autojs.theme.color.OkColor

/**
 * Created by Stardust on May 20, 2017.
 * Modified by SuperMonster003 as of Feb 15, 2023.
 *
 * A color detector compares a probe color with a reference color.
 * Every detector exposes the numeric [distance] it compares against its threshold,
 * so that `detectColor(r, g, b)` is always equivalent to `distance(r, g, b) <= threshold`.
 * Distances are expressed on a 0..255 scale (the same scale as thresholds).
 *
 * zh-CN:
 *
 * 颜色检测器用于比较探测颜色与参考颜色.
 * 每个检测器都会暴露用于与阈值比较的数值 [distance],
 * 因此 `detectColor(r, g, b)` 恒等价于 `distance(r, g, b) <= threshold`.
 * 距离统一使用 0..255 量纲 (与阈值一致).
 */
interface ColorDetector {

    fun detectColor(r: Int, g: Int, b: Int): Boolean

    /**
     * The Auto.js 4 name of [detectColor]; kept because `com.stardust.autojs.core.image.ColorDetector` resolves to
     * this interface through `StardustClassAliases` (roadmap P7.3).
     * zh-CN: [detectColor] 的 Auto.js 4 名称; 因 `com.stardust.autojs.core.image.ColorDetector` 经 `StardustClassAliases`
     * 解析到本接口而保留 (roadmap P7.3).
     */
    fun detectsColor(r: Int, g: Int, b: Int): Boolean = detectColor(r, g, b)

    /**
     * Distance between the reference color and (r, g, b) on the 0..255 scale used by thresholds.
     *
     * zh-CN: 参考颜色与 (r, g, b) 的距离, 量纲与阈值一致 (0..255).
     */
    fun distance(r: Int, g: Int, b: Int): Double

    abstract class AbstractColorDetector(color: Int) : ColorDetector {
        protected val colorR: Int = color shr 16 and 0xFF
        protected val colorG: Int = color shr 8 and 0xFF
        protected val colorB: Int = color and 0xFF

        override fun detectsColor(r: Int, g: Int, b: Int): Boolean = detectColor(r, g, b)
    }

    abstract class ThresholdColorDetector(color: Int, protected val threshold: Int) : AbstractColorDetector(color) {
        override fun detectColor(r: Int, g: Int, b: Int) = distance(r, g, b) <= threshold
    }

    class EqualityDetector(color: Int) : AbstractColorDetector(color) {
        override fun detectColor(r: Int, g: Int, b: Int): Boolean {
            return colorR == r && colorG == g && colorB == b
        }

        /** Chebyshev distance, so that equality means a distance of 0. (zh-CN: 切比雪夫距离, 相等即距离为 0.) */
        override fun distance(r: Int, g: Int, b: Int): Double {
            return max(abs(r - colorR), max(abs(g - colorG), abs(b - colorB))).toDouble()
        }
    }

    class DifferenceDetector(color: Int, threshold: Int) : ThresholdColorDetector(color, threshold) {
        override fun distance(r: Int, g: Int, b: Int): Double {
            return (abs(r - colorR) + abs(g - colorG) + abs(b - colorB)) / 3.0
        }
    }

    /**
     * Red channel distance only; the Auto.js 4 detector of `com.stardust.autojs.core.image.ColorDetector`, kept because
     * that interface resolves to this one through `StardustClassAliases` (roadmap P7.3).
     * zh-CN: 只比较红色通道的距离; Auto.js 4 `com.stardust.autojs.core.image.ColorDetector` 的检测器, 因该接口经
     * `StardustClassAliases` 解析到本接口而保留 (roadmap P7.3).
     */
    class RDistanceDetector(color: Int, threshold: Int) : ThresholdColorDetector(color, threshold) {
        override fun distance(r: Int, g: Int, b: Int): Double = abs(r - colorR).toDouble()
    }

    class RGBDistanceDetector(color: Int, threshold: Int) : ThresholdColorDetector(color, threshold) {
        override fun distance(r: Int, g: Int, b: Int): Double {
            val dR = (r - colorR).toDouble()
            val dG = (g - colorG).toDouble()
            val dB = (b - colorB).toDouble()
            return sqrt(dR * dR + dG * dG + dB * dB) / 3.0
        }
    }

    class WeightedRGBDistanceDetector(color: Int, threshold: Int) : ThresholdColorDetector(color, threshold) {
        override fun distance(r: Int, g: Int, b: Int): Double {
            val dR = (r - colorR).toDouble()
            val dG = (g - colorG).toDouble()
            val dB = (b - colorB).toDouble()

            val meanR = (colorR + r) / 2.0

            val wR = 2 + meanR / 256
            val wG = 4.0
            val wB = 2 + (255 - meanR) / 256

            // @Hint by SuperMonster003 on Feb 17, 2023.
            //  ! Code snippet in Auto.js 4.1.1 Alpha2:
            //  # mThreshold = threshold * threshold * 8;
            //  ! I guess that it should be 9 instead of 8.
            //  ! zh-CN:
            //  ! Auto.js 4.1.1 Alpha2 中的代码片段:
            //  # mThreshold = threshold * threshold * 8;
            //  ! 我认为应当是 9 而非 8.
            return sqrt(wR * dR * dR + wG * dG * dG + wB * dB * dB) / 3.0
        }
    }

    class HDistanceDetector(color: Int, threshold: Int) : ThresholdColorDetector(color, threshold) {

        private val colorH = getHueFromRGB(colorR, colorG, colorB)

        override fun distance(r: Int, g: Int, b: Int): Double {

            // @Hint by SuperMonster003 on Feb 17, 2023.
            //  ! Code snippet in Auto.js 4.1.1 Alpha2:
            //  # return Math.abs(mH - getH(R, G, B)) <= mThreshold;
            //  ! I guess that Math.abs(a, b) is not the exact distance when a and b are in a circle path.
            //  ! zh-CN:
            //  ! Auto.js 4.1.1 Alpha2 中的代码片段:
            //  # return Math.abs(mH - getH(R, G, B)) <= mThreshold;
            //  ! 我认为当 a 和 b 在一个圆形路径中时, Math.abs(a, b) 不能代表它们之间的精确距离.

            val h = getHueFromRGB(r, g, b)

            /* Hue distance on the color circle, in range 0..180. (zh-CN: 色环上的色相距离, 范围 0..180.) */
            val dH = min(abs(colorH - h), 360 - abs(colorH - h))

            return dH / 180.0 * 255
        }
    }

    class HSDistanceDetector(color: Int, threshold: Int) : ThresholdColorDetector(color, threshold) {

        private val h = getHueFromRGB(colorR, colorG, colorB)
        private val s = getSaturationPercent(colorR, colorG, colorB)

        constructor(color: Int, similarity: Float) : this(color, ((1.0f - similarity) * 255).roundToInt())

        override fun distance(r: Int, g: Int, b: Int): Double {
            // @Hint by SuperMonster003 on Sep 14, 2026.
            //  ! The original implementation packed hue and saturation into one Long with
            //  ! `h and (s shl 32)`, which is always 0, so every color was reported as similar.
            //  ! The distance is now the normalized Euclidean length of (dh, ds), where
            //  ! dh is the circular hue distance in range 0..1 and ds the saturation difference in range 0..1.
            //  ! zh-CN:
            //  ! 原实现用 `h and (s shl 32)` 打包色相与饱和度, 结果恒为 0, 导致任意颜色都被判定为相似.
            //  ! 现改为 (dh, ds) 的归一化欧氏距离, 其中 dh 为色环距离 (0..1), ds 为饱和度差 (0..1).
            val hueDiff = abs(getHueFromRGB(r, g, b) - h)
            val dH = min(hueDiff, 360 - hueDiff) / 180.0
            val dS = abs(getSaturationPercent(r, g, b) - s) / 100.0
            return sqrt((dH * dH + dS * dS) / 2.0) * 255
        }

        companion object {
            /** Saturation in percent (0..100), 0 for black. (zh-CN: 百分比饱和度 (0..100), 黑色为 0.) */
            private fun getSaturationPercent(r: Int, g: Int, b: Int): Double {
                val max = max(r, max(g, b))
                if (max == 0) return 0.0
                val min = min(r, min(g, b))
                return (max - min) * 100.0 / max
            }
        }
    }

    /**
     * Perceptual distance in OKLab (roadmap P6.1, D18): the Euclidean dE_ok, scaled so that black to white is 255.
     * Allocation free, with a lookup table for the sRGB transfer function, since it runs per pixel when finding colors.
     *
     * zh-CN: OKLab 中的感知距离 (Roadmap P6.1, D18): 欧氏距离 dE_ok, 缩放使黑与白的距离为 255.
     * 由于找色时逐像素调用, 实现不分配内存, 并以查找表计算 sRGB 传递函数.
     */
    class OkLabDistanceDetector(color: Int, threshold: Int) : ThresholdColorDetector(color, threshold) {

        private val reference = OkColor.toOkLab(0xFF shl 24 or (colorR shl 16) or (colorG shl 8) or colorB)

        // Same matrices as OkColor.toOkLab, kept in locals so that one detector may serve several threads.
        // zh-CN: 与 OkColor.toOkLab 相同的矩阵, 只使用局部变量, 使一个检测器可供多个线程使用.
        override fun distance(r: Int, g: Int, b: Int): Double {
            val lr = LINEAR[r and 0xFF]
            val lg = LINEAR[g and 0xFF]
            val lb = LINEAR[b and 0xFF]
            val l = cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
            val m = cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
            val s = cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
            val dL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s - reference.l
            val dA = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s - reference.a
            val dB = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s - reference.b
            return sqrt(dL * dL + dA * dA + dB * dB) * 255
        }

        companion object {
            private val LINEAR = DoubleArray(256) { OkColor.srgbToLinear(it / 255.0) }
        }

    }

    companion object {

        /**
         * Hue in degrees (0..360), following the same formula as android.graphics.Color.RGBToHSV
         * but without any allocation.
         *
         * zh-CN: 色相 (0..360 度), 与 android.graphics.Color.RGBToHSV 的公式一致, 且无内存分配.
         */
        @JvmStatic
        fun getHueFromRGB(r: Int, g: Int, b: Int): Double {
            val max = max(r, max(g, b))
            val min = min(r, min(g, b))
            val delta = (max - min).toDouble()
            if (delta == 0.0) return 0.0
            val h = when (max) {
                r -> (g - b) / delta
                g -> 2 + (b - r) / delta
                else -> 4 + (r - g) / delta
            } * 60
            return if (h < 0) h + 360 else h
        }

        @JvmStatic
        fun get(@ColorInt color: Int, algorithm: String, threshold: Int): ColorDetector {
            return when (val name = algorithm.trim().lowercase()) {
                "diff" -> DifferenceDetector(color, threshold)
                "rgb+" -> WeightedRGBDistanceDetector(color, threshold)
                "rgb" -> RGBDistanceDetector(color, threshold)
                "hs" -> HSDistanceDetector(color, threshold)
                "h" -> HDistanceDetector(color, threshold)
                "equal" -> EqualityDetector(color)
                "oklab" -> OkLabDistanceDetector(color, threshold)
                else -> throw IllegalArgumentException("Unknown color detection algorithm \"$name\", expected one of: diff, rgb, rgb+, hs, h, equal, oklab")
            }
        }
    }
}
