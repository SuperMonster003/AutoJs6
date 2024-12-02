package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsImageView
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by Stardust on Nov 30, 2017.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsImageViewInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : RoundedImageViewInflater<JsImageView>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<JsImageView> = object : ViewCreator<JsImageView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsImageView {
            return JsImageView(context).also { it.drawables = drawables }
        }
    }

}