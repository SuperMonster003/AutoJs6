package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.CalendarView
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class CalendarViewAttributes(resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(resourceParser, view) {

    override val view = super.view as CalendarView

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("date") { view.date = it.toLong() }
        registerAttr("firstDayOfWeek") { view.firstDayOfWeek = parseDayOfWeek(it) }
        registerAttr("minDate") { setMinDate(view, it) }
        registerAttr("maxDate") { setMaxDate(view, it) }
    }

}
