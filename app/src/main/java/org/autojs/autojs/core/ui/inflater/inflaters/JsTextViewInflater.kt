package org.autojs.autojs.core.ui.inflater.inflaters

import androidx.appcompat.widget.AppCompatTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsTextView

class JsTextViewInflater(resourceParser: ResourceParser) : AppCompatTextViewInflater<JsTextView>(resourceParser) {

    override fun getCreator(): ViewCreator<in AppCompatTextView> = ViewCreator { context, _ -> JsTextView(context) }

}