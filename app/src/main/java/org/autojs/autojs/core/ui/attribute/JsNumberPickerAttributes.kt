package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsNumberPicker
import org.autojs.autojs.runtime.ScriptRuntime

open class JsNumberPickerAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : NumberPickerAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsNumberPicker

}