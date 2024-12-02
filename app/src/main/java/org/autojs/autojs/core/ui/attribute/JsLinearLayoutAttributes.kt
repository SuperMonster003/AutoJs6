package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsLinearLayout
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by SuperMonster003 on May 20, 2023.
 */
class JsLinearLayoutAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : LinearLayoutAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsLinearLayout

}