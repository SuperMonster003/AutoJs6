package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsButton

class JsButtonAttributes(resourceParser: ResourceParser, view: View) : ButtonAttributes(resourceParser, view) {

    override val view = super.view as JsButton

}