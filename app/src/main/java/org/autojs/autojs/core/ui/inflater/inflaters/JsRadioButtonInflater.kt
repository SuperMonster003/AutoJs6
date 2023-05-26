package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.RadioButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsRadioButton

class JsRadioButtonInflater(resourceParser: ResourceParser) : RadioButtonInflater(resourceParser) {

    override fun getCreator(): ViewCreator<in RadioButton> = ViewCreator { context, _ -> JsRadioButton(context) }

}