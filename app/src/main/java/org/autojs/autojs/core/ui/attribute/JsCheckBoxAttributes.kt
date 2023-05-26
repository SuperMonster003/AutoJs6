package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsCheckBox

class JsCheckBoxAttributes(resourceParser: ResourceParser, view: View) : AppCompatCheckBoxAttributes(resourceParser, view) {

    override val view = super.view as JsCheckBox

}