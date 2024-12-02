package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsAppBarLayout
import org.autojs.autojs.runtime.ScriptRuntime

class JsAppBarLayoutAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : AppBarLayoutAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsAppBarLayout

}