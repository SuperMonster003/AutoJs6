package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsChronometer
import org.autojs.autojs.runtime.ScriptRuntime

class JsChronometerAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ChronometerAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsChronometer

}