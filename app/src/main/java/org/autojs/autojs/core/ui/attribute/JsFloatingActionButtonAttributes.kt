package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsFloatingActionButton

class JsFloatingActionButtonAttributes(resourceParser: ResourceParser, view: View) : FloatingActionButtonAttributes(resourceParser, view) {

    override val view = super.view as JsFloatingActionButton

}