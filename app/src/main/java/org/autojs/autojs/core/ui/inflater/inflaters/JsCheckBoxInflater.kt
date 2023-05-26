package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsCheckBox

class JsCheckBoxInflater(resourceParser: ResourceParser) : AppCompatCheckBoxInflater<JsCheckBox>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsCheckBox> = ViewCreator { context, _ -> JsCheckBox(context) }

}