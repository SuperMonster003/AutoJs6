package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import org.apache.log4j.Logger
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.console.ConsoleView
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.util.ViewUtils
import java.util.Locale

class JsConsoleView : ConsoleView {

    private val TAG = JsConsoleView::class.java.simpleName
    private val LOGGER = Logger.getLogger(JsConsoleView::class.java)

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // init {
    //     setConsole(object : GlobalConsole(UiHandler(context)) {
    //         override fun println(level: Int, charSequence: CharSequence): String {
    //             val log = String.format(Locale.getDefault(), "%s", charSequence)
    //             LOGGER.log(toLog4jLevel(level), log)
    //             Log.d(TAG, log)
    //             super.println(level, log)
    //             return log
    //         }
    //     })
    // }
    //
    // override fun onNewLog(logEntry: ConsoleImpl.LogEntry?) {
    //     if (logEntry != null) {
    //         ViewUtils.showToast(context, logEntry.content.toString())
    //     }
    // }

}