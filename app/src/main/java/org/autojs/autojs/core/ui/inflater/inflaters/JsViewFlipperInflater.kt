package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsViewFlipper
import org.autojs.autojs.runtime.ScriptRuntime

class JsViewFlipperInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ViewFlipperInflater<JsViewFlipper>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsViewFlipper> = object : ViewCreator<JsViewFlipper> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsViewFlipper {
            return JsViewFlipper(context)
        }
    }

}