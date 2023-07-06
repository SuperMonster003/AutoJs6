package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsViewFlipper

class JsViewFlipperInflater(resourceParser: ResourceParser) : ViewFlipperInflater<JsViewFlipper>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsViewFlipper> = object : ViewCreator<JsViewFlipper> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsViewFlipper {
            return JsViewFlipper(context)
        }
    }

}