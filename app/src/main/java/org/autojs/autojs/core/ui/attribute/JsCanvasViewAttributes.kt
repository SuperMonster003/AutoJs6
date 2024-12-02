package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.widget.JsCanvasView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

class JsCanvasViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : TextureViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsCanvasView

}