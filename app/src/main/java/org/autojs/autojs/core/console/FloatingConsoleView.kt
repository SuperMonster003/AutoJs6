package org.autojs.autojs.core.console

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import org.autojs.autojs.tool.MapBuilder
import org.autojs.autojs6.R

class FloatingConsoleView : ConsoleView {

    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun getLogLevelMap(): MutableMap<Int, Int> = MapBuilder<Int, Int>()
        .put(R.color.floating_console_view_verbose, Log.VERBOSE)
        .put(R.color.floating_console_view_debug, Log.DEBUG)
        .put(R.color.floating_console_view_info, Log.INFO)
        .put(R.color.floating_console_view_warn, Log.WARN)
        .put(R.color.floating_console_view_error, Log.ERROR)
        .put(R.color.floating_console_view_assert, Log.ASSERT)
        .build()

}