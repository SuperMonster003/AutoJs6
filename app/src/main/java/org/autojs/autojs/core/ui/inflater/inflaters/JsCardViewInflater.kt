package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsCardView

class JsCardViewInflater(resourceParser: ResourceParser) : CardViewInflater<JsCardView>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsCardView> = ViewCreator { context, _ -> JsCardView(context) }

}