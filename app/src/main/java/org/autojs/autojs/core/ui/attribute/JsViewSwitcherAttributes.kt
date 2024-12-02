package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsViewSwitcher
import org.autojs.autojs.runtime.ScriptRuntime

class JsViewSwitcherAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewSwitcherAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsViewSwitcher

}