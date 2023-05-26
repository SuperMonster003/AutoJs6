package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsRelativeLayout

class JsRelativeLayoutInflater(resourceParser: ResourceParser) : RelativeLayoutInflater<JsRelativeLayout>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsRelativeLayout> = ViewCreator { context, _ -> JsRelativeLayout(context) }

}