package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsFloatingActionButton
import org.autojs.autojs.runtime.ScriptRuntime

class JsFloatingActionButtonInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : FloatingActionButtonInflater<JsFloatingActionButton>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsFloatingActionButton> = object : ViewCreator<JsFloatingActionButton> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsFloatingActionButton {
            return JsFloatingActionButton(context)
        }
    }

}