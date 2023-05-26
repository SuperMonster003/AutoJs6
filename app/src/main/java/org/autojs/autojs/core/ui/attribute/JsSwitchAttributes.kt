package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsSwitch

class JsSwitchAttributes(resourceParser: ResourceParser, view: View) : SwitchCompatAttributes(resourceParser, view) {

    override val view = super.view as JsSwitch

}