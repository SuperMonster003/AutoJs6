package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.widget.JsCanvasView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by Stardust on Mar 16, 2018.
 */
class JsCanvasViewInflater(private val scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : TextureViewInflater<JsCanvasView>(scriptRuntime, resourceParser) {

    override fun getCreator() = object : ViewCreator<JsCanvasView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsCanvasView {
            return JsCanvasView(context).apply { initWithScriptRuntime(scriptRuntime) }
        }
    }

}