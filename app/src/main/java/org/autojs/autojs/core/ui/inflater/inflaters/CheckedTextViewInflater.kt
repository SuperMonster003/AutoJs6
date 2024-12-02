package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.CheckedTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class CheckedTextViewInflater<V : CheckedTextView>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : TextViewInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<CheckedTextView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): CheckedTextView {
            return CheckedTextView(context)
        }
    }

}
