package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsCardView

class JsCardViewAttributes(resourceParser: ResourceParser, view: View) : CardViewAttributes(resourceParser, view) {

    override val view = super.view as JsCardView

}