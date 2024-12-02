package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsTabLayout
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by SuperMonster003 on May 20, 2023.
 */
class JsTabLayoutAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : TabLayoutAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsTabLayout

}