package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsNumberPicker

open class JsNumberPickerAttributes(resourceParser: ResourceParser, view: View) : NumberPickerAttributes(resourceParser, view) {

    override val view = super.view as JsNumberPicker

}