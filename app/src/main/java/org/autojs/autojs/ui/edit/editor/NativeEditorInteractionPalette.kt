package org.autojs.autojs.ui.edit.editor

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import org.xmlpull.v1.XmlPullParser
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Process-local interaction colors for the native editor.
 *
 * Cursor and selection-handle drawables are inflated by the framework before the editor theme is
 * known. They therefore resolve the latest palette every time they draw. Registered drawables are
 * invalidated when the editor theme changes so already-visible selection handles update as well.
 */
object NativeEditorInteractionPalette {

    data class Colors(
        val background: Int,
        val controlColor: Int,
        val selectionColor: Int,
    )

    @Volatile
    private var colors = resolve(DEFAULT_BACKGROUND, DEFAULT_FOREGROUND)

    private val drawables = CopyOnWriteArrayList<WeakReference<Drawable>>()

    fun update(
        @ColorInt background: Int,
        @ColorInt foreground: Int,
    ): Colors {
        return resolve(background, foreground).also {
            colors = it
            invalidateDrawables()
        }
    }

    fun current(): Colors = colors

    internal fun resolve(
        @ColorInt background: Int,
        @ColorInt foreground: Int,
    ): Colors {
        val opaqueBackground = opaque(background)
        val opaqueForeground = opaque(foreground)
        val controlColor = when {
            contrastRatio(opaqueBackground, opaqueForeground) >= MINIMUM_CONTROL_CONTRAST ->
                opaqueForeground
            contrastRatio(opaqueBackground, OPAQUE_WHITE) >=
                contrastRatio(opaqueBackground, OPAQUE_BLACK) ->
                OPAQUE_WHITE
            else -> OPAQUE_BLACK
        }
        return Colors(
            background = opaqueBackground,
            controlColor = controlColor,
            selectionColor = withAlpha(controlColor, SELECTION_ALPHA),
        )
    }

    internal fun contrastRatio(
        @ColorInt first: Int,
        @ColorInt second: Int,
    ): Double {
        val lighter = max(relativeLuminance(opaque(first)), relativeLuminance(opaque(second)))
        val darker = min(relativeLuminance(opaque(first)), relativeLuminance(opaque(second)))
        return (lighter + 0.05) / (darker + 0.05)
    }

    internal fun register(drawable: Drawable) {
        drawables += WeakReference(drawable)
    }

    private fun invalidateDrawables() {
        drawables.removeAll { reference ->
            reference.get()?.let {
                it.invalidateSelf()
                false
            } ?: true
        }
    }

    private fun relativeLuminance(@ColorInt color: Int): Double {
        fun linearize(channel: Int): Double {
            val component = channel / 255.0
            return if (component <= 0.04045) {
                component / 12.92
            } else {
                Math.pow((component + 0.055) / 1.055, 2.4)
            }
        }

        val red = linearize(color ushr 16 and 0xFF)
        val green = linearize(color ushr 8 and 0xFF)
        val blue = linearize(color and 0xFF)
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    @ColorInt
    private fun opaque(@ColorInt color: Int): Int = color or OPAQUE_ALPHA_MASK

    @ColorInt
    private fun withAlpha(@ColorInt color: Int, alpha: Double): Int {
        val alphaChannel = (alpha * 255.0).roundToInt().coerceIn(0, 0xFF)
        return color and RGB_MASK or (alphaChannel shl 24)
    }

    internal const val MINIMUM_CONTROL_CONTRAST = 3.0
    internal const val SELECTION_ALPHA = 0.30

    private const val DEFAULT_BACKGROUND = -0x1
    private const val DEFAULT_FOREGROUND = -0x1000000
    private const val OPAQUE_WHITE = -0x1
    private const val OPAQUE_BLACK = -0x1000000
    private const val OPAQUE_ALPHA_MASK = -0x1000000
    private const val RGB_MASK = 0x00FFFFFF
}

abstract class NativeEditorInteractionDrawable : Drawable() {

    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    protected var density = Resources.getSystem().displayMetrics.density

    private var drawableAlpha = 0xFF

    init {
        NativeEditorInteractionPalette.register(this)
    }

    override fun inflate(
        resources: Resources,
        parser: XmlPullParser,
        attrs: AttributeSet,
        theme: Resources.Theme?,
    ) {
        super.inflate(resources, parser, attrs, theme)
        density = resources.displayMetrics.density
    }

    protected fun preparePaint() {
        val color = NativeEditorInteractionPalette.current().controlColor
        val sourceAlpha = color ushr 24 and 0xFF
        val resolvedAlpha = sourceAlpha * drawableAlpha / 0xFF
        paint.color = color and 0x00FFFFFF or (resolvedAlpha shl 24)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 0xFF)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Editor controls intentionally ignore host-theme tint filters.
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class NativeEditorCursorDrawable : NativeEditorInteractionDrawable() {

    override fun draw(canvas: Canvas) {
        preparePaint()
        canvas.drawRect(bounds, paint)
    }

    override fun getIntrinsicWidth(): Int = (CURSOR_WIDTH_DP * density).roundToInt().coerceAtLeast(1)

    private companion object {
        const val CURSOR_WIDTH_DP = 2f
    }
}

class NativeEditorInsertionHandleDrawable : NativeEditorInteractionDrawable() {

    private val pointer = Path()

    override fun draw(canvas: Canvas) {
        preparePaint()
        val area = RectF(bounds)
        if (area.isEmpty) {
            return
        }

        val centerX = area.centerX()
        val radius = min(area.width() / 2f, area.height() * 0.42f)
        val centerY = area.bottom - radius

        pointer.reset()
        pointer.moveTo(centerX, area.top)
        pointer.lineTo(centerX - radius * 0.72f, centerY - radius * 0.55f)
        pointer.lineTo(centerX + radius * 0.72f, centerY - radius * 0.55f)
        pointer.close()

        canvas.drawPath(pointer, paint)
        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    override fun getIntrinsicWidth(): Int = (HANDLE_WIDTH_DP * density).roundToInt()

    override fun getIntrinsicHeight(): Int = (HANDLE_HEIGHT_DP * density).roundToInt()

    private companion object {
        const val HANDLE_WIDTH_DP = 20f
        const val HANDLE_HEIGHT_DP = 24f
    }
}

abstract class NativeEditorSelectionHandleDrawable(
    private val contactOnRight: Boolean,
) : NativeEditorInteractionDrawable() {

    override fun draw(canvas: Canvas) {
        preparePaint()
        val area = RectF(bounds)
        if (area.isEmpty) {
            return
        }

        val radius = min(area.height() / 2f, area.width() / 4f)
        val centerX = area.centerX()
        val centerY = area.centerY()
        canvas.drawCircle(centerX, centerY, radius, paint)
        if (contactOnRight) {
            canvas.drawRect(centerX, centerY - radius, centerX + radius, centerY + radius, paint)
        } else {
            canvas.drawRect(centerX - radius, centerY - radius, centerX, centerY + radius, paint)
        }
    }

    override fun getIntrinsicWidth(): Int = (HANDLE_WIDTH_DP * density).roundToInt()

    override fun getIntrinsicHeight(): Int = (HANDLE_HEIGHT_DP * density).roundToInt()

    private companion object {
        const val HANDLE_WIDTH_DP = 44f
        const val HANDLE_HEIGHT_DP = 22f
    }
}

class NativeEditorSelectionHandleLeftDrawable :
    NativeEditorSelectionHandleDrawable(contactOnRight = true)

class NativeEditorSelectionHandleRightDrawable :
    NativeEditorSelectionHandleDrawable(contactOnRight = false)
