package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsRatingBar
import org.autojs.autojs.runtime.ScriptRuntime

class JsRatingBarAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : RatingBarAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsRatingBar

}