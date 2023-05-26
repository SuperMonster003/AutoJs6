package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsRadioGroup

class JsRadioGroupAttributes(resourceParser: ResourceParser, view: View) : RadioGroupAttributes(resourceParser, view) {

    override val view = super.view as JsRadioGroup

}