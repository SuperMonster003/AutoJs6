package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsSeekBar

class JsSeekBarInflater(resourceParser: ResourceParser) : SeekBarInflater<JsSeekBar>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsSeekBar> = object : ViewCreator<JsSeekBar> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsSeekBar {
            return JsSeekBar(context)
        }
    }

}