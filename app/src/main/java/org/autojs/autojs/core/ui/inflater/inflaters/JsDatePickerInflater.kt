package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsDatePicker

class JsDatePickerInflater(resourceParser: ResourceParser) : DatePickerInflater<JsDatePicker>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsDatePicker> = ViewCreator { context, _ -> JsDatePicker(context) }

}