package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.widget.JsCanvasView
import org.autojs.autojs.core.ui.inflater.ResourceParser

class JsCanvasViewAttributes(resourceParser: ResourceParser, view: View) : TextureViewAttributes(resourceParser, view) {

    override val view = super.view as JsCanvasView

}