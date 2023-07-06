package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsImageSwitcher

class JsImageSwitcherAttributes(resourceParser: ResourceParser, view: View) : ImageSwitcherAttributes(resourceParser, view) {

    override val view = super.view as JsImageSwitcher

}