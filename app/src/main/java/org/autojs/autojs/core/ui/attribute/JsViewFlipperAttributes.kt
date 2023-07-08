package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsViewFlipper

class JsViewFlipperAttributes(resourceParser: ResourceParser, view: View) : ViewFlipperAttributes(resourceParser, view) {

    override val view = super.view as JsViewFlipper

}