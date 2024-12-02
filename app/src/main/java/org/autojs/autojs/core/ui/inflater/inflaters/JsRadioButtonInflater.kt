package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.RadioButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsRadioButton
import org.autojs.autojs.runtime.ScriptRuntime

class JsRadioButtonInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : RadioButtonInflater(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in RadioButton> = object : ViewCreator<RadioButton> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsRadioButton {
            return JsRadioButton(context)
        }
    }

}