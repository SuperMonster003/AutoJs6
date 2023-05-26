package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsTimePicker

class JsTimePickerInflater(resourceParser: ResourceParser) : TimePickerInflater<JsTimePicker>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsTimePicker> = ViewCreator { context, _ -> JsTimePicker(context) }

}