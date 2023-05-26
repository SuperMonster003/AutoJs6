package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsLinearLayout

class JsLinearLayoutInflater(resourceParser: ResourceParser) : LinearLayoutInflater<JsLinearLayout>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsLinearLayout> = ViewCreator { context, _ -> JsLinearLayout(context) }

}