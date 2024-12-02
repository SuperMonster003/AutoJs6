package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsSwitch
import org.autojs.autojs.runtime.ScriptRuntime

class JsSwitchAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : SwitchCompatAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsSwitch

}