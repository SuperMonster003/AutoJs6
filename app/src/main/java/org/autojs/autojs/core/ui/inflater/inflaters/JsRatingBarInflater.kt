package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsRatingBar

class JsRatingBarInflater(resourceParser: ResourceParser) : RatingBarInflater<JsRatingBar>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsRatingBar> = ViewCreator { context, _ -> JsRatingBar(context) }

}