package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsRelativeLayout

class JsRelativeLayoutAttributes(resourceParser: ResourceParser, view: View) : RelativeLayoutAttributes(resourceParser, view) {

    override val view = super.view as JsRelativeLayout

}