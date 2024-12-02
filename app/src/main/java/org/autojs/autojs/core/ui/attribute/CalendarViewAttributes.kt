package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.CalendarView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class CalendarViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as CalendarView

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("date") { view.date = it.toLong() }
        registerAttr("firstDayOfWeek") { view.firstDayOfWeek = parseDayOfWeek(it) }
        registerAttr("minDate") { setMinDate(view, it) }
        registerAttr("maxDate") { setMaxDate(view, it) }
    }

}
