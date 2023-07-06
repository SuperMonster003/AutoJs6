package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsTextView

class JsTextViewInflater(resourceParser: ResourceParser) : AppCompatTextViewInflater<JsTextView>(resourceParser) {

    override fun getCreator(): ViewCreator<in AppCompatTextView> = object : ViewCreator<AppCompatTextView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsTextView {
            return JsTextView(context)
        }
    }

}