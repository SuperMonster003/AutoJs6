package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsSearchView
import org.autojs.autojs.runtime.ScriptRuntime

class JsSearchViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : SearchViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsSearchView

}