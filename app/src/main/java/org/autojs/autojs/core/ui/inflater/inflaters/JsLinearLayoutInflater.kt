package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsLinearLayout

class JsLinearLayoutInflater(resourceParser: ResourceParser) : LinearLayoutInflater<JsLinearLayout>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsLinearLayout> = object : ViewCreator<JsLinearLayout> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsLinearLayout {
            return JsLinearLayout(context)
        }
    }

}