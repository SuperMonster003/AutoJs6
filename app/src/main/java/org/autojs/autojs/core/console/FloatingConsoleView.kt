package org.autojs.autojs.core.console

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import org.autojs.autojs.tool.MapBuilder
import org.autojs.autojs6.R

class FloatingConsoleView : ConsoleView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        setPinchToZoomEnabled(true)
    }

    override fun getLogLevelMap(): MutableMap<Int, Int> = MapBuilder<Int, Int>()
        .put(Log.VERBOSE, R.color.floating_console_view_verbose)
        .put(Log.DEBUG, R.color.floating_console_view_debug)
        .put(Log.INFO, R.color.floating_console_view_info)
        .put(Log.WARN, R.color.floating_console_view_warn)
        .put(Log.ERROR, R.color.floating_console_view_error)
        .put(Log.ASSERT, R.color.floating_console_view_assert)
        .build()

}