package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.widget.JsCanvasView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

/**
 * Created by Stardust on 2018/3/16.
 */
class JsCanvasViewInflater(resourceParser: ResourceParser) : TextureViewInflater<JsCanvasView>(resourceParser) {

    override fun getCreator() = object : ViewCreator<JsCanvasView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsCanvasView {
            return JsCanvasView(context)
        }
    }

}