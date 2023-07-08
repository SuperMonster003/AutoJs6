package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.widget.JsCheckedTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

class JsCheckedTextViewInflater(resourceParser: ResourceParser) : CheckedTextViewInflater<JsCheckedTextView>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsCheckedTextView> = object : ViewCreator<JsCheckedTextView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsCheckedTextView {
            return JsCheckedTextView(context)
        }
    }

}