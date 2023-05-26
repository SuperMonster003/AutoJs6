package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsRadioButton

class JsRadioButtonAttributes(resourceParser: ResourceParser, view: View) : RadioButtonAttributes(resourceParser, view) {

    override val view = super.view as JsRadioButton

}