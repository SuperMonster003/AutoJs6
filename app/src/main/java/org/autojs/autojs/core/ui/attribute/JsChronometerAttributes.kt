package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsChronometer

class JsChronometerAttributes(resourceParser: ResourceParser, view: View) : ChronometerAttributes(resourceParser, view) {

    override val view = super.view as JsChronometer

}