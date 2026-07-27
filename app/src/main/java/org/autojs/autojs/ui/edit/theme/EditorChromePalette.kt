package org.autojs.autojs.ui.edit.theme

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.LayoutDirection
import android.view.Menu
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat
import org.autojs.autojs.util.ViewUtils.applyColorFilterWith
import org.xmlpull.v1.XmlPullParser

/**
 * Process-local colors used by the editor's popup drawable resources.
 *
 * The editor toolbar always uses one popup theme resource. AppCompat caches the popup context when
 * it creates its ActionMenuPresenter, so swapping popup theme resource IDs later is not reliable.
 * These drawables instead resolve the latest editor colors every time they draw.
 */
object EditorChromePalette {

    data class Colors(
        val background: Int,
        val foreground: Int,
    )

    @Volatile
    private var colors = Colors(
        background = DEFAULT_BACKGROUND,
        foreground = DEFAULT_FOREGROUND,
    )

    @JvmStatic
    fun update(@ColorInt background: Int, @ColorInt foreground: Int) {
        colors = Colors(background, foreground)
    }

    @JvmStatic
    fun current(): Colors = colors

    @ColorInt
    fun foreground(enabled: Boolean): Int =
        if (enabled) colors.foreground else withAlpha(colors.foreground, DISABLED_ALPHA)

    @ColorInt
    fun pressedOverlay(): Int = withAlpha(colors.foreground, PRESSED_ALPHA)

    fun applyTo(menu: Menu) {
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            val title = item.title?.toString().orEmpty()
            if (title.isNotEmpty()) {
                item.title = SpannableString(title).apply {
                    setSpan(
                        ForegroundColorSpan(foreground(item.isEnabled)),
                        0,
                        length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
            item.icon = item.icon?.mutate()?.applyColorFilterWith(foreground(item.isEnabled))
            item.subMenu?.let(::applyTo)
        }
    }

    @ColorInt
    internal fun withAlpha(@ColorInt color: Int, alpha: Float): Int {
        val sourceAlpha = color ushr 24 and 0xFF
        val adjustedAlpha = (sourceAlpha * alpha).toInt().coerceIn(0, 0xFF)
        return color and 0x00FFFFFF or (adjustedAlpha shl 24)
    }

    private const val DEFAULT_BACKGROUND = -0x1
    private const val DEFAULT_FOREGROUND = -0x1000000
    private const val DISABLED_ALPHA = 0.43f
    private const val PRESSED_ALPHA = 0.14f
}

abstract class EditorPaletteDrawable : Drawable() {

    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected var density = Resources.getSystem().displayMetrics.density

    override fun inflate(
        resources: Resources,
        parser: XmlPullParser,
        attrs: AttributeSet,
        theme: Resources.Theme?,
    ) {
        super.inflate(resources, parser, attrs, theme)
        density = resources.displayMetrics.density
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
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class EditorPopupBackgroundDrawable : EditorPaletteDrawable() {

    override fun draw(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = EditorChromePalette.current().background
        val radius = CORNER_RADIUS_DP * density
        canvas.drawRoundRect(RectF(bounds), radius, radius, paint)
    }

    override fun getPadding(padding: Rect): Boolean {
        padding.set(0, (VERTICAL_PADDING_DP * density).toInt(), 0, (VERTICAL_PADDING_DP * density).toInt())
        return true
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, CORNER_RADIUS_DP * density)
    }

    private companion object {
        const val CORNER_RADIUS_DP = 4f
        const val VERTICAL_PADDING_DP = 4f
    }
}

class EditorPopupItemBackgroundDrawable : EditorPaletteDrawable() {

    private var highlighted = false

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val nextHighlighted = state.any {
            it == android.R.attr.state_pressed ||
                it == android.R.attr.state_focused ||
                it == android.R.attr.state_selected
        }
        if (highlighted == nextHighlighted) {
            return false
        }
        highlighted = nextHighlighted
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        if (!highlighted) {
            return
        }
        paint.style = Paint.Style.FILL
        paint.color = EditorChromePalette.pressedOverlay()
        canvas.drawRect(bounds, paint)
    }
}

class EditorPopupSubmenuArrowDrawable : EditorPaletteDrawable() {

    override fun draw(canvas: Canvas) {
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val halfWidth = 3f * density
        val halfHeight = 5f * density
        val direction = if (
            DrawableCompat.getLayoutDirection(this) == LayoutDirection.RTL
        ) {
            -1f
        } else {
            1f
        }
        val tipX = centerX + direction * halfWidth
        val tailX = centerX - direction * halfWidth

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * density
        paint.strokeCap = Paint.Cap.SQUARE
        paint.strokeJoin = Paint.Join.MITER
        paint.color = EditorChromePalette.current().foreground

        canvas.drawLine(tailX, centerY - halfHeight, tipX, centerY, paint)
        canvas.drawLine(tipX, centerY, tailX, centerY + halfHeight, paint)
    }

    override fun getIntrinsicWidth(): Int = (INTRINSIC_SIZE_DP * density).toInt()

    override fun getIntrinsicHeight(): Int = (INTRINSIC_SIZE_DP * density).toInt()

    private companion object {
        const val INTRINSIC_SIZE_DP = 24f
    }
}

class EditorPopupCheckboxDrawable : EditorPaletteDrawable() {

    private var checked = false
    private var enabled = true

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val nextChecked = state.any { it == android.R.attr.state_checked }
        val nextEnabled = state.any { it == android.R.attr.state_enabled }
        if (checked == nextChecked && enabled == nextEnabled) {
            return false
        }
        checked = nextChecked
        enabled = nextEnabled
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val foreground = EditorChromePalette.foreground(enabled)
        val inset = 4f * density
        val strokeWidth = 1.7f * density
        val box = RectF(bounds).apply { inset(inset, inset) }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = foreground
        canvas.drawRoundRect(box, 2f * density, 2f * density, paint)

        if (!checked) {
            return
        }
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(box, 2f * density, 2f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = EditorChromePalette.current().background
        val left = box.left + box.width() * 0.22f
        val middleX = box.left + box.width() * 0.44f
        val middleY = box.top + box.height() * 0.68f
        val right = box.right - box.width() * 0.18f
        canvas.drawLine(left, box.top + box.height() * 0.52f, middleX, middleY, paint)
        canvas.drawLine(middleX, middleY, right, box.top + box.height() * 0.30f, paint)
    }

    override fun getIntrinsicWidth(): Int = (INTRINSIC_SIZE_DP * density).toInt()

    override fun getIntrinsicHeight(): Int = (INTRINSIC_SIZE_DP * density).toInt()

    private companion object {
        const val INTRINSIC_SIZE_DP = 24f
    }
}

class EditorPopupRadioDrawable : EditorPaletteDrawable() {

    private var checked = false
    private var enabled = true

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val nextChecked = state.any { it == android.R.attr.state_checked }
        val nextEnabled = state.any { it == android.R.attr.state_enabled }
        if (checked == nextChecked && enabled == nextEnabled) {
            return false
        }
        checked = nextChecked
        enabled = nextEnabled
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val foreground = EditorChromePalette.foreground(enabled)
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val radius = 8f * density

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.7f * density
        paint.color = foreground
        canvas.drawCircle(centerX, centerY, radius, paint)

        if (checked) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(centerX, centerY, 4.5f * density, paint)
        }
    }

    override fun getIntrinsicWidth(): Int = (INTRINSIC_SIZE_DP * density).toInt()

    override fun getIntrinsicHeight(): Int = (INTRINSIC_SIZE_DP * density).toInt()

    private companion object {
        const val INTRINSIC_SIZE_DP = 24f
    }
}
