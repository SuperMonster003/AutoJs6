package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsFloatingActionButton
import org.autojs.autojs.runtime.ScriptRuntime

class JsFloatingActionButtonAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : FloatingActionButtonAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsFloatingActionButton

}