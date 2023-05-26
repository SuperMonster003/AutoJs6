package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsRadioGroup

class JsRadioGroupInflater(resourceParser: ResourceParser) : RadioGroupInflater<JsRadioGroup>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsRadioGroup> = ViewCreator { context, _ -> JsRadioGroup(context) }

}