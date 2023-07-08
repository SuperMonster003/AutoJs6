package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsCheckedTextView

class JsCheckedTextViewAttributes(resourceParser: ResourceParser, view: View) : CheckedTextViewAttributes(resourceParser, view) {

    override val view = super.view as JsCheckedTextView

}