package org.autojs.autojs.core.image

import android.graphics.Color
import android.graphics.Color.RGBToHSV
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Created by Stardust on May 20, 2017.
 * Modified by SuperMonster003 as of Feb 15, 2023.
 */
interface ColorDetector {

    fun detectColor(r: Int, g: Int, b: Int): Boolean

    abstract class AbstractColorDetector(color: Int) : ColorDetector {
        protected open val colorR: Int = Color.red(color)
        protected open val colorG: Int = Color.green(color)
        protected open val colorB: Int = Color.blue(color)
    }

    class EqualityDetector(color: Int) : AbstractColorDetector(color) {
        override fun detectColor(r: Int, g: Int, b: Int): Boolean {
            return colorR == r && colorG == g && colorB == b
        }
    }

    class DifferenceDetector(color: Int, private val threshold: Int) : AbstractColorDetector(color) {
        override fun detectColor(r: Int, g: Int, b: Int): Boolean {
            return (abs(r - colorR) + abs(g - colorG) + abs(b - colorB)) / 3.0 <= threshold
        }
    }

    class RGBDistanceDetector(color: Int, private val threshold: Int) : AbstractColorDetector(color) {
        override fun detectColor(r: Int, g: Int, b: Int): Boolean {
            val dR = (r - colorR).toDouble()
            val dG = (g - colorG).toDouble()
            val dB = (b - colorB).toDouble()
            return sqrt((dR.pow(2) + dG.pow(2) + dB.pow(2))) / 3.0 <= threshold
        }
    }

    class WeightedRGBDistanceDetector(color: Int, private val threshold: Int) : AbstractColorDetector(color) {
        override val colorR = color and 0xff0000 shr 16
        override val colorG = color and 0x00ff00 shr 8
        override val colorB = color and 0xff

        override fun detectColor(r: Int, g: Int, b: Int): Boolean {
            val dR = (r - colorR).toDouble()
            val dG = (g - colorG).toDouble()
            val dB = (b - colorB).toDouble()

            val meanR = (colorR + r) / 2.0

            val wR = 2 + meanR / 256
            val wG = 4.0
            val wB = 2 + (255 - meanR) / 256

            // @Hint by SuperMonster003 on Feb 17, 2023.
            //  ! Code snippet in Auto.js 4.1.1 alpha2:
            //  !
            //  ! mThreshold = threshold * threshold * 8;
            //  !
            //  ! I guess that it should be 9 instead of 8.
            return sqrt((wR * dR.pow(2) + wG * dG.pow(2) + wB * dB.pow(2))) / 3.0 <= threshold
        }
    }

    class HDistanceDetector(color: Int, private val threshold: Int) : AbstractColorDetector(color) {
        override fun detectColor(r: Int, g: Int, b: Int): Boolean {
            // @Hint by SuperMonster003 on Feb 17, 2023.
            //  ! Code snippet in Auto.js 4.1.1 alpha2:
            //  !
            //  ! return Math.abs(mH - getH(R, G, B)) <= mThreshold;
            //  !
            //  ! I guess that abs(a, b) is not the exact distance when a and b are in a circle path.

            val colorH = getHueFromRGB(colorR, colorG, colorB)
            val h = getHueFromRGB(r, g, b)

            @FloatRange(0.0, 180.0)
            val dH = min(abs(colorH - h), 360 - abs(colorH - h))

            return dH / 180.0 * 255 <= threshold
        }
    }

    class HSDistanceDetector(color: Int, private val threshold: Int) : AbstractColorDetector(color) {
        private val hs = getHnS(colorR, colorG, colorB)
        private val h = hs and 0xffffffffL
        private val s = hs shr 32 and 0xffffffffL

        constructor(color: Int, similarity: Float) : this(color, ((1.0f - similarity) * 255).roundToInt())

        override fun detectColor(r: Int, g: Int, b: Int): Boolean {
            val hs = getHnS(r, g, b)
            val dH = (hs and 0xffffffffL) - h
            val dS = (hs shr 32 and 0xffffffffL) - s
            return (dH * dH + dS * dS) * 255 / 3729600.0 <= threshold

            // dh = min(abs(h1-h0), 360-abs(h1-h0)) / 180.0
            // ds = abs(s1-s0)
            // dv = abs(v1-v0) / 255.0
            //
            // Each of these values will be in the range [0,1]. You can compute the length of this tuple:
            //
            // distance = sqrt(dh*dh+ds*ds+dv*dv)
        }

        companion object {
            private fun getHnS(r: Int, g: Int, b: Int): Long {
                val min = if (r > g) min(g, b) else min(r, b)
                val max = if (r > g) max(r, b) else max(g, b)
                val h = getHueFromRGB(r, g, b)
                val s = ((max - min) * 100 / max).toLong()
                return h.toLong() and (s shl 32)
            }
        }
    }

    companion object {
        private fun getHueFromRGB(r: Int, g: Int, b: Int): Double {
            // val min = if (r > g) min(g, b) else min(r, b)
            // val max = if (r > g) max(r, b) else max(g, b)
            // val h = when {
            //     r == max -> (g - b) / (max - min).toDouble() * 60
            //     g == max -> 120 + (b - r) / (max - min).toDouble() * 60
            //     else -> 240 + (r - g) / (max - min).toDouble() * 60
            // }
            // return if (h < 0) h + 360 else h

            return floatArrayOf(0f, 0f, 0f).also { RGBToHSV(r, g, b, it) }[0].toDouble()
        }

        @JvmStatic
        fun get(@ColorInt color: Int, algorithm: String, threshold: Int): ColorDetector {
            return when (algorithm.trim().lowercase()) {
                "diff" -> DifferenceDetector(color, threshold)
                "rgb+" -> WeightedRGBDistanceDetector(color, threshold)
                "rgb" -> RGBDistanceDetector(color, threshold)
                "hs" -> HSDistanceDetector(color, threshold)
                "h" -> HDistanceDetector(color, threshold)
                "equal" -> EqualityDetector(color)
                else -> throw Exception("Failed to get a color detector")
            }
        }
    }
}