package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsRatingBar

class JsRatingBarAttributes(resourceParser: ResourceParser, view: View) : RatingBarAttributes(resourceParser, view) {

    override val view = super.view as JsRatingBar

}