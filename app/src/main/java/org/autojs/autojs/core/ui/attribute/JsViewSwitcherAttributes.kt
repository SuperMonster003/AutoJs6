package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsViewSwitcher

class JsViewSwitcherAttributes(resourceParser: ResourceParser, view: View) : ViewSwitcherAttributes(resourceParser, view) {

    override val view = super.view as JsViewSwitcher

}