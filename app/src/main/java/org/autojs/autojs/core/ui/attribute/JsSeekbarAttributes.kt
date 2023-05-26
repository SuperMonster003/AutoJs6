package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsSeekBar

class JsSeekbarAttributes(resourceParser: ResourceParser, view: View) : SeekBarAttributes<JsSeekBar>(resourceParser, view) {

    override val view = super.view as JsSeekBar

}