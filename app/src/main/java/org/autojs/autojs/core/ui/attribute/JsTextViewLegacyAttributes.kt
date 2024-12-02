package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsTextViewLegacy
import org.autojs.autojs.runtime.ScriptRuntime

class JsTextViewLegacyAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : AppCompatTextViewLegacyAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsTextViewLegacy

}