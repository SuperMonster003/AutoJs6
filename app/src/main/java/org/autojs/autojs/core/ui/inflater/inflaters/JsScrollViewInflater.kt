package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsScrollView

class JsScrollViewInflater(resourceParser: ResourceParser) : ScrollViewInflater<JsScrollView>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsScrollView> = ViewCreator { context, _ -> JsScrollView(context) }

}