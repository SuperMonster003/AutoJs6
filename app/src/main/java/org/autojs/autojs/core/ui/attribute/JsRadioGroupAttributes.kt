package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsRadioGroup
import org.autojs.autojs.runtime.ScriptRuntime

class JsRadioGroupAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : RadioGroupAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsRadioGroup

}