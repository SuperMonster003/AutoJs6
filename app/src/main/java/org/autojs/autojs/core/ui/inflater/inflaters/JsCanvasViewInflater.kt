package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.graphics.JsCanvasView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

/**
 * Created by Stardust on 2018/3/16.
 */
class JsCanvasViewInflater(resourceParser: ResourceParser) : TextureViewInflater<JsCanvasView>(resourceParser) {

    override fun getCreator() = ViewCreator { context, _ -> JsCanvasView(context) }

}