package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsFloatingActionButton

class JsFloatingActionButtonInflater(resourceParser: ResourceParser) : FloatingActionButtonInflater<JsFloatingActionButton>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsFloatingActionButton> = ViewCreator { context, _ -> JsFloatingActionButton(context) }

}