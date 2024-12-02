package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsTextView
import org.autojs.autojs.runtime.ScriptRuntime

class JsTextViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : AppCompatTextViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsTextView

}