package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class AutoCompleteTextViewInflater<V : AutoCompleteTextView>(resourceParser: ResourceParser) : EditTextInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<AutoCompleteTextView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): AutoCompleteTextView {
            return AutoCompleteTextView(context)
        }
    }

}
