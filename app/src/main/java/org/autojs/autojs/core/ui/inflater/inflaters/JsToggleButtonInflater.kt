package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsToggleButton
import org.autojs.autojs.runtime.ScriptRuntime

class JsToggleButtonInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ToggleButtonInflater<JsToggleButton>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsToggleButton> = object : ViewCreator<JsToggleButton> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsToggleButton {
            return JsToggleButton(context)
        }
    }

}