package org.autojs.autojs.theme.app

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * A circle split into equal sectors, one per color. Two colors give a left half and a right half,
 * which is how day / night groups are shown.
 * zh-CN: 等分为若干扇区的圆形, 每个扇区一种颜色. 两种颜色时分为左右两半, 用于显示日 / 夜组.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
class SegmentedCircleDrawable(private val colors: List<Int>) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    override fun draw(canvas: Canvas) {
        if (colors.isEmpty()) return
        oval.set(bounds)
        if (colors.size == 1) {
            paint.color = colors.first()
            canvas.drawOval(oval, paint)
            return
        }
        val sweep = 360f / colors.size
        colors.forEachIndexed { index, color ->
            paint.color = color
            // Start at the bottom so the first color fills the left side. zh-CN: 从底部开始, 使第一种颜色位于左侧.
            canvas.drawArc(oval, 90f + index * sweep, sweep, true, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

}
