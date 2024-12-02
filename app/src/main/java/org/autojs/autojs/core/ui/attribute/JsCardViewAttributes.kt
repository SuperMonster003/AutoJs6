package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsCardView
import org.autojs.autojs.runtime.ScriptRuntime

class JsCardViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : CardViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsCardView

}