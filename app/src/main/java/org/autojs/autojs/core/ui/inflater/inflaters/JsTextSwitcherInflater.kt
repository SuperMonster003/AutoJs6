package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsTextSwitcher
import org.autojs.autojs.core.ui.widget.JsTextView
import org.autojs.autojs6.R

class JsTextSwitcherInflater(resourceParser: ResourceParser) : TextSwitcherInflater<JsTextSwitcher>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsTextSwitcher> = object : ViewCreator<JsTextSwitcher> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsTextSwitcher {
            return JsTextSwitcher(context)
        }
    }

}