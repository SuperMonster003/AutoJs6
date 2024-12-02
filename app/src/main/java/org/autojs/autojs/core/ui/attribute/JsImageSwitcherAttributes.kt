package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsImageSwitcher
import org.autojs.autojs.runtime.ScriptRuntime

class JsImageSwitcherAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ImageSwitcherAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsImageSwitcher

}