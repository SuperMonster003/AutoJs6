package org.autojs.autojs.core.ui.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import org.autojs.plugin.ui.preview.UiPreviewContract
import kotlin.math.abs

/**
 * The root of a preview session: carries the script theme's window background (an embedded hierarchy has no
 * window to paint one), consumes pointer input in the edit pointer mode and reports taps, and draws the
 * highlight frame the client asked for. The embedded surface sits above the client's window (P10.0: touches
 * only reach the hierarchy that way), so these edit-mode affordances cannot be drawn by the client.
 *
 * Since preview contract v4 the content lives in [contentFrame] (`ui.root`), between the strips that stand in for the
 * status and navigation bars once a run colors them, and the [dialogLayer] of a run's dialogs lies above it all.
 *
 * zh-CN: 预览会话的根容器: 带上脚本主题的窗口背景 (嵌入树没有窗口来绘制它), 在编辑指针模式下消费指针并报告点击,
 * 并绘制客户端要求的高亮框. 嵌入表面位于客户端窗口之上 (P10.0: 只有这样触摸才能到达嵌入树), 因此这些编辑态的标记不能由客户端绘制.
 * 自预览契约 v4 起内容位于 [contentFrame] (`ui.root`), 夹在运行为其着色后代替状态栏与导航栏的色条之间, 运行的对话框层 [dialogLayer] 位于最上层.
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

    private val highlightStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, context.resources.displayMetrics)
    private val highlightPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.rgb(229, 57, 53)
        strokeWidth = highlightStrokeWidth
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private val windowBackground: Drawable?

    /** The parent of the content and the value of `ui.root`; inset by the bar strips (contract v4). */
    val contentFrame = FrameLayout(context)
    private val statusStrip = View(context).apply { visibility = GONE }
    private val navigationStrip = View(context).apply { visibility = GONE }

    /** The dialogs of a run, above the content and the strips (contract v4). */
    val dialogLayer = UiPreviewDialogLayer(context)

    init {
        setWillNotDraw(false)
        val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        try {
            windowBackground = attrs.getDrawable(0)
        } finally {
            attrs.recycle()
        }
        background = windowBackground
        addView(contentFrame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(statusStrip, LayoutParams(LayoutParams.MATCH_PARENT, dp(UiPreviewContract.STATUS_BAR_STRIP_DP), Gravity.TOP))
        addView(navigationStrip, LayoutParams(LayoutParams.MATCH_PARENT, dp(UiPreviewContract.NAVIGATION_BAR_STRIP_DP), Gravity.BOTTOM))
        addView(dialogLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** `ui.statusBarColor` of a run: a strip of [color] at the top, the content below it. */
    fun showStatusBarColor(color: Int) = showStrip(statusStrip, color)

    /** `ui.navigationBarColor` of a run: a strip of [color] at the bottom, the content above it. */
    fun showNavigationBarColor(color: Int) = showStrip(navigationStrip, color)

    /** `ui.backgroundColor` of a run: the root's background, in place of the theme's window background. */
    fun showBackgroundColor(color: Int) {
        background = ColorDrawable(color)
    }

    /** Back to the plain root of a layout: no strips, the theme's window background. */
    fun resetWindowColors() {
        statusStrip.visibility = GONE
        navigationStrip.visibility = GONE
        background = windowBackground
        updateContentInsets()
    }

    private fun showStrip(strip: View, color: Int) {
        strip.background = ColorDrawable(color)
        strip.visibility = VISIBLE
        updateContentInsets()
    }

    private fun updateContentInsets() {
        val params = contentFrame.layoutParams as LayoutParams
        val top = if (statusStrip.visibility == VISIBLE) statusStrip.layoutParams.height else 0
        val bottom = if (navigationStrip.visibility == VISIBLE) navigationStrip.layoutParams.height else 0
        if (params.topMargin != top || params.bottomMargin != bottom) {
            params.topMargin = top
            params.bottomMargin = bottom
            contentFrame.layoutParams = params
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

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
            // A root scaled down into a panel (the editor's preview, D74) keeps the frame as wide on screen.
            // zh-CN: 缩小放进面板的根容器 (编辑器预览, D74) 在屏幕上保持同样的框线宽度.
            highlightPaint.strokeWidth = highlightStrokeWidth / scaleX.coerceIn(MIN_STROKE_SCALE, 1f)
            val inset = highlightPaint.strokeWidth / 2
            canvas.drawRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, highlightPaint)
        }
    }

    private companion object {
        /** Below this scale the frame grows no wider on the layout, so that it does not cover small views. */
        const val MIN_STROKE_SCALE = 0.25f
    }
}
