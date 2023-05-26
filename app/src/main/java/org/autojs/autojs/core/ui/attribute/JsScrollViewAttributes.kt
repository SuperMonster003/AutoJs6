package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsScrollView

class JsScrollViewAttributes(resourceParser: ResourceParser, view: View) : ScrollViewAttributes(resourceParser, view) {

    override val view = super.view as JsScrollView

}