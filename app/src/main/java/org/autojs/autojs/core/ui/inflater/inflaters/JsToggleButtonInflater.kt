package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsToggleButton

class JsToggleButtonInflater(resourceParser: ResourceParser) : ToggleButtonInflater<JsToggleButton>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsToggleButton> = ViewCreator { context, _ -> JsToggleButton(context) }

}