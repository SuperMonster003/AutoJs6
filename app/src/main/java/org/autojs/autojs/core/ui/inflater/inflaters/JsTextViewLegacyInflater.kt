package org.autojs.autojs.core.ui.inflater.inflaters

import androidx.appcompatlegacy.widget.AppCompatTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsTextViewLegacy

class JsTextViewLegacyInflater(resourceParser: ResourceParser) : TextViewLegacyInflater<JsTextViewLegacy>(resourceParser) {

    override fun getCreator(): ViewCreator<in AppCompatTextView> = ViewCreator { context, _ -> JsTextViewLegacy(context) }

}