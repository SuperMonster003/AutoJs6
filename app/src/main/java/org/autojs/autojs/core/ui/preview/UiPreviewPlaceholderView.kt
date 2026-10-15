package org.autojs.autojs.core.ui.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.View

/**
 * Stands in for a view that needs a window (WebView, SurfaceView, TextureView, VideoView) in the snapshot
 * mode (D48): a flat rectangle with a border and the class name, so the designer still shows where the
 * element sits and how large it is.
 *
 * zh-CN: 在快照模式 (D48) 中代替需要窗口的控件 (WebView, SurfaceView, TextureView, VideoView): 带边框与类名的矩形,
 * 设计器仍能看到元素的位置与大小.
 */
class UiPreviewPlaceholderView(context: Context, val label: String) : View(context) {

    private val fill = Paint().apply { color = Color.argb(255, 236, 239, 241) }
    private val border = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.argb(255, 144, 164, 174)
        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, context.resources.displayMetrics)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 84, 110, 122)
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, fill)
        val inset = border.strokeWidth / 2
        canvas.drawRect(inset, inset, w - inset, h - inset, border)
        if (w > text.textSize * 3 && h > text.textSize * 2) {
            canvas.drawText(label, w / 2, h / 2 - (text.descent() + text.ascent()) / 2, text)
        }
    }
}
