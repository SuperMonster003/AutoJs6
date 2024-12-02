package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsCalendarView
import org.autojs.autojs.runtime.ScriptRuntime

class JsCalendarViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : CalendarViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsCalendarView

}