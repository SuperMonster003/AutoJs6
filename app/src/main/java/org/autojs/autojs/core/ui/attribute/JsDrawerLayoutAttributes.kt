package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsDrawerLayout

class JsDrawerLayoutAttributes(resourceParser: ResourceParser, view: View) : DrawerLayoutAttributes(resourceParser, view) {

    override val view = super.view as JsDrawerLayout

}