package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsCalendarView

class JsCalendarViewAttributes(resourceParser: ResourceParser, view: View) : CalendarViewAttributes(resourceParser, view) {

    override val view = super.view as JsCalendarView

}