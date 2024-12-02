package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsRatingBar
import org.autojs.autojs.runtime.ScriptRuntime

class JsRatingBarInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : RatingBarInflater<JsRatingBar>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsRatingBar> = object : ViewCreator<JsRatingBar> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsRatingBar {
            return JsRatingBar(context)
        }
    }

}