package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsProgressBar

class JsProgressBarInflater(resourceParser: ResourceParser) : ProgressBarInflater<JsProgressBar>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsProgressBar> = ViewCreator { context, _ -> JsProgressBar(context) }

}