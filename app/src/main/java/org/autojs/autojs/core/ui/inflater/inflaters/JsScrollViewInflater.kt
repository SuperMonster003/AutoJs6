package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsScrollView

class JsScrollViewInflater(resourceParser: ResourceParser) : ScrollViewInflater<JsScrollView>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsScrollView> = object : ViewCreator<JsScrollView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsScrollView {
            return JsScrollView(context)
        }
    }

}