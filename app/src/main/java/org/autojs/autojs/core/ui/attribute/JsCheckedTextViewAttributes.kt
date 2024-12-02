package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsCheckedTextView
import org.autojs.autojs.runtime.ScriptRuntime

class JsCheckedTextViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : CheckedTextViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsCheckedTextView

}