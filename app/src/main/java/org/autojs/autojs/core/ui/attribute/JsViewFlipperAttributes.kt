package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsViewFlipper
import org.autojs.autojs.runtime.ScriptRuntime

class JsViewFlipperAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewFlipperAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsViewFlipper

}