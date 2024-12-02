package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsDatePicker
import org.autojs.autojs.runtime.ScriptRuntime

class JsDatePickerAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : DatePickerAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsDatePicker

}