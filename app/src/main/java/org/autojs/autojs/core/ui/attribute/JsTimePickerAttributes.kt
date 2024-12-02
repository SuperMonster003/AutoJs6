package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsTimePicker
import org.autojs.autojs.runtime.ScriptRuntime

class JsTimePickerAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : TimePickerAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsTimePicker

}