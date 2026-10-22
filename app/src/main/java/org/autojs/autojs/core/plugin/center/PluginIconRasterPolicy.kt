package org.autojs.autojs.core.plugin.center

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Size limits for installed plugin icons kept by the plugin center.
 *
 * Density-less artwork (for example a 432 px PNG in res/mipmap) is scaled by Resources to the
 * screen density, so one icon becomes a 1512 px (9 MB) bitmap at 560 dpi. Every plugin keeps a
 * day and a night icon, and before Android 8 bitmap pixels count against the Java heap.
 *
 * zh-CN: 插件中心保留的已安装插件图标的尺寸上限.
 * 未区分密度的图片 (例如 res/mipmap 中 432 px 的 PNG) 会被 Resources 按屏幕密度放大,
 * 在 560 dpi 上单个图标即为 1512 px (9 MB) 的位图. 每个插件保留日间与夜间两个图标,
 * 而 Android 8 之前位图像素计入 Java 堆.
 */
internal object PluginIconRasterPolicy {

    /** Resources inflate ".xml" files and decode every other file resource as a bitmap. */
    fun isBitmapFile(path: CharSequence?): Boolean {
        if (path.isNullOrEmpty()) return false
        val name = path.toString()
        return !name.endsWith(".xml", ignoreCase = true) && !name.endsWith(".9.png", ignoreCase = true)
    }

    /** Whether a bitmap can be kept as is: its shorter side is not larger than [target]. */
    fun fits(width: Int, height: Int, target: Int): Boolean = target <= 0 || min(width, height) <= target

    /** The largest power-of-two sample size that keeps the shorter side at least [target]. */
    fun sampleSize(width: Int, height: Int, target: Int): Int {
        if (target <= 0) return 1
        val shorter = min(width, height)
        var sample = 1
        while (shorter / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }

    /**
     * Scales the shorter side down to [target] and keeps the aspect ratio, as the icon view
     * crops to the center. Sizes that already fit are returned unchanged.
     *
     * zh-CN: 将较短边缩小到 [target] 并保持宽高比 (图标视图居中裁剪). 已符合上限的尺寸原样返回.
     */
    fun scaledSize(width: Int, height: Int, target: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || fits(width, height, target)) return width to height
        val scale = target.toDouble() / min(width, height)
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }
}
