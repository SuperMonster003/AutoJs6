package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsTimePicker
import org.autojs.autojs.runtime.ScriptRuntime

class JsTimePickerInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : TimePickerInflater<JsTimePicker>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsTimePicker> = object : ViewCreator<JsTimePicker> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsTimePicker {
            return JsTimePicker(context)
        }
    }

}