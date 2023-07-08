package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import androidx.appcompatlegacy.widget.AppCompatTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsTextViewLegacy

class JsTextViewLegacyInflater(resourceParser: ResourceParser) : TextViewLegacyInflater<JsTextViewLegacy>(resourceParser) {

    override fun getCreator(): ViewCreator<in AppCompatTextView> = object : ViewCreator<AppCompatTextView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsTextViewLegacy {
            return JsTextViewLegacy(context)
        }
    }

}