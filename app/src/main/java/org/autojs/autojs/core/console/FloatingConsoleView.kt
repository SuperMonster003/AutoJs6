package org.autojs.autojs.core.console

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import org.autojs.autojs6.R
import kotlin.math.roundToInt

/**
 * The console view inside the floating window: light text palette on a dark translucent
 * background, rounded corners, pinch-to-zoom and a visible title bar.
 *
 * zh-CN: 浮动窗口内的控制台视图: 深色半透明背景上的浅色文本配色, 圆角, 双指缩放与可见的标题栏.
 */
class FloatingConsoleView : ConsoleView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        setPinchToZoomEnabled(true)
        isTitleBarVisible = true
        setBackgroundDrawables(
            ResourcesCompat.getDrawable(resources, R.drawable.floating_console_title_bar_bg, context.theme),
            ResourcesCompat.getDrawable(resources, R.drawable.floating_console_content_bg, context.theme),
        )
        val padding = (8 * resources.displayMetrics.density).roundToInt()
        setContentPadding(padding, padding, padding, padding)
    }

    override fun getLogLevelMap(): Map<Int, Int> = mapOf(
        Log.VERBOSE to R.color.floating_console_view_verbose,
        Log.DEBUG to R.color.floating_console_view_debug,
        Log.INFO to R.color.floating_console_view_info,
        Log.WARN to R.color.floating_console_view_warn,
        Log.ERROR to R.color.floating_console_view_error,
        Log.ASSERT to R.color.floating_console_view_assert,
    )

}
