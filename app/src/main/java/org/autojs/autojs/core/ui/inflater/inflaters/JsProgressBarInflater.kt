package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsProgressBar

class JsProgressBarInflater(resourceParser: ResourceParser) : ProgressBarInflater<JsProgressBar>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsProgressBar> = object : ViewCreator<JsProgressBar> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsProgressBar {
            return JsProgressBar(context)
        }
    }

}