package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsCheckBox
import org.autojs.autojs.runtime.ScriptRuntime

class JsCheckBoxAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : AppCompatCheckBoxAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsCheckBox

}