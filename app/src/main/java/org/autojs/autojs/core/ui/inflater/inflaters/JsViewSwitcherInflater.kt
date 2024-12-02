package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsViewSwitcher
import org.autojs.autojs.runtime.ScriptRuntime

class JsViewSwitcherInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ViewSwitcherInflater<JsViewSwitcher>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsViewSwitcher> = object : ViewCreator<JsViewSwitcher> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsViewSwitcher {
            return JsViewSwitcher(context)
        }
    }

}