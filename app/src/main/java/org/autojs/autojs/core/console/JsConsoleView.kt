package org.autojs.autojs.core.console

import android.content.Context
import android.util.AttributeSet
import org.autojs.autojs.AutoJs

class JsConsoleView : ConsoleView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        setConsole(AutoJs.instance.globalConsole)
    }

}