package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsTextViewLegacy

class JsTextViewLegacyAttributes(resourceParser: ResourceParser, view: View) : AppCompatTextViewLegacyAttributes(resourceParser, view) {

    override val view = super.view as JsTextViewLegacy

}