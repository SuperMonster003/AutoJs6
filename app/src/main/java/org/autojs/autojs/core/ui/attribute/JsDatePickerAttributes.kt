package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsDatePicker

class JsDatePickerAttributes(resourceParser: ResourceParser, view: View) : DatePickerAttributes(resourceParser, view) {

    override val view = super.view as JsDatePicker

}