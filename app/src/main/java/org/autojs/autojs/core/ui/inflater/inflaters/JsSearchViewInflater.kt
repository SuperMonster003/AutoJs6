package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsSearchView
import org.autojs.autojs.runtime.ScriptRuntime

class JsSearchViewInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : SearchViewInflater<JsSearchView>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsSearchView> = object : ViewCreator<JsSearchView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsSearchView {
            return JsSearchView(context)
        }
    }

}