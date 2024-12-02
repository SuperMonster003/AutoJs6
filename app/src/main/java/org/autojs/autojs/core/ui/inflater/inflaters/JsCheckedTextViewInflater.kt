package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.widget.JsCheckedTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

class JsCheckedTextViewInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : CheckedTextViewInflater<JsCheckedTextView>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsCheckedTextView> = object : ViewCreator<JsCheckedTextView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsCheckedTextView {
            return JsCheckedTextView(context)
        }
    }

}