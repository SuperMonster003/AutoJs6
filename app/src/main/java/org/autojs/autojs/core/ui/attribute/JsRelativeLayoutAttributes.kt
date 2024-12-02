package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsRelativeLayout
import org.autojs.autojs.runtime.ScriptRuntime

class JsRelativeLayoutAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : RelativeLayoutAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsRelativeLayout

}