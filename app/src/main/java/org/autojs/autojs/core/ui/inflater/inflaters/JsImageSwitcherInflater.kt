package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsImageSwitcher
import org.autojs.autojs.runtime.ScriptRuntime

class JsImageSwitcherInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ImageSwitcherInflater<JsImageSwitcher>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<JsImageSwitcher> = object : ViewCreator<JsImageSwitcher> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsImageSwitcher {
            return JsImageSwitcher(context)
        }
    }

}