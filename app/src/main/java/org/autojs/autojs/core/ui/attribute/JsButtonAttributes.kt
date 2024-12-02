package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsButton
import org.autojs.autojs.runtime.ScriptRuntime

class JsButtonAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ButtonAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsButton

}