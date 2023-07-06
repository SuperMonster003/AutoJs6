package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsImageView

/**
 * Created by Stardust on 2017/11/30.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsImageViewInflater(resourceParser: ResourceParser) : RoundedImageViewInflater<JsImageView>(resourceParser) {

    override fun getCreator(): ViewCreator<JsImageView> = object : ViewCreator<JsImageView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsImageView {
            return JsImageView(context).also { it.drawables = drawables }
        }
    }

}