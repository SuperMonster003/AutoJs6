package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsCalendarView
import org.autojs.autojs.runtime.ScriptRuntime

class JsCalendarViewInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : CalendarViewInflater<JsCalendarView>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsCalendarView> = object : ViewCreator<JsCalendarView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsCalendarView {
            return JsCalendarView(context)
        }
    }

}