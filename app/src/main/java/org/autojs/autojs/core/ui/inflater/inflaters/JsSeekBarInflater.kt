package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsSeekBar
import org.autojs.autojs.runtime.ScriptRuntime

class JsSeekBarInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : SeekBarInflater<JsSeekBar>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsSeekBar> = object : ViewCreator<JsSeekBar> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsSeekBar {
            return JsSeekBar(context)
        }
    }

}