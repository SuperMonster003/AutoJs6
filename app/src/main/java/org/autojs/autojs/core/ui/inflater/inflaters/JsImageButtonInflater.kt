package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsImageButton

/**
 * Created by SuperMonster003 on May 23, 2023.
 */
class JsImageButtonInflater(resourceParser: ResourceParser) : ImageButtonInflater<JsImageButton>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsImageButton> = object : ViewCreator<JsImageButton> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsImageButton {
            return JsImageButton(context)
        }
    }

}