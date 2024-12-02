package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsCheckBox
import org.autojs.autojs.runtime.ScriptRuntime

class JsCheckBoxInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : AppCompatCheckBoxInflater<JsCheckBox>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsCheckBox> = object : ViewCreator<JsCheckBox> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsCheckBox {
            return JsCheckBox(context)
        }
    }

}