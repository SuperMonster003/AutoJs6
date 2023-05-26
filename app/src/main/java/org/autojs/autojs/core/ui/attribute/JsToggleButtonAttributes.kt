package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsToggleButton

class JsToggleButtonAttributes(resourceParser: ResourceParser, view: View) : ToggleButtonAttributes(resourceParser, view) {

    override val view = super.view as JsToggleButton

}