package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsSeekBar

class JsSeekBarInflater(resourceParser: ResourceParser) : SeekbarInflater<JsSeekBar>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsSeekBar> = ViewCreator { context, _ -> JsSeekBar(context) }

}