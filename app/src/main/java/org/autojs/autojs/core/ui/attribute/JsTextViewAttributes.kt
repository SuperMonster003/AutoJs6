package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsTextView

class JsTextViewAttributes(resourceParser: ResourceParser, view: View) : AppCompatTextViewAttributes(resourceParser, view) {

    override val view = super.view as JsTextView

}