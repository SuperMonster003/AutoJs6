package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsRadioGroup

class JsRadioGroupInflater(resourceParser: ResourceParser) : RadioGroupInflater<JsRadioGroup>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsRadioGroup> = object : ViewCreator<JsRadioGroup> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsRadioGroup {
            return JsRadioGroup(context)
        }
    }

}