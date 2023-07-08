package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsTimePicker

class JsTimePickerInflater(resourceParser: ResourceParser) : TimePickerInflater<JsTimePicker>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsTimePicker> = object : ViewCreator<JsTimePicker> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsTimePicker {
            return JsTimePicker(context)
        }
    }

}