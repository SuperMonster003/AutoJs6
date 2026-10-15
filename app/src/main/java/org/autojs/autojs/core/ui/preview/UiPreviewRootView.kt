package org.autojs.autojs.core.ui.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * The root of a preview session: carries the script theme's window background (an embedded hierarchy has no
 * window to paint one), consumes pointer input in the edit pointer mode and reports taps, and draws the
 * highlight frame the client asked for. The embedded surface sits above the client's window (P10.0: touches
 * only reach the hierarchy that way), so these edit-mode affordances cannot be drawn by the client.
 *
 * zh-CN: 预览会话的根容器: 带上脚本主题的窗口背景 (嵌入树没有窗口来绘制它), 在编辑指针模式下消费指针并报告点击,
 * 并绘制客户端要求的高亮框. 嵌入表面位于客户端窗口之上 (P10.0: 只有这样触摸才能到达嵌入树), 因此这些编辑态的标记不能由客户端绘制.
 */
class UiPreviewRootView(context: Context, private val listener: Listener) : FrameLayout(context) {

    interface Listener {
        fun onRootTap(x: Int, y: Int)
    }

    /** Edit mode: the root swallows every pointer event and reports taps; interactive mode lets them through. */
    var interceptsPointer = true

    var highlightRect: Rect? = null
        set(value) {
            field = value
            invalidate()
        }

    private val highlightPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.rgb(229, 57, 53)
        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, context.resources.displayMetrics)
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    init {
        setWillNotDraw(false)
        val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        try {
            background = attrs.getDrawable(0)
        } finally {
            attrs.recycle()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = interceptsPointer

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interceptsPointer) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
            }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) moved = true
            MotionEvent.ACTION_UP -> if (!moved) listener.onRootTap(event.x.toInt(), event.y.toInt())
        }
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        highlightRect?.let { rect ->
            val inset = highlightPaint.strokeWidth / 2
            canvas.drawRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, highlightPaint)
        }
    }
}
