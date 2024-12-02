package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.TextClock
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Strings
import org.autojs.autojs.runtime.ScriptRuntime

open class TextClockAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : TextViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as TextClock

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("format12Hour") { view.format12Hour = Strings.parse(view, it) }
        registerAttr("format24Hour") { view.format24Hour = Strings.parse(view, it) }
        registerAttr("timeZone") { view.timeZone = Strings.parse(view, it) }
    }

}
