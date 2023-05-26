package org.autojs.autojs.core.ui.inflater.inflaters

import androidx.appcompat.widget.AppCompatTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class AppCompatTextViewInflater<V : AppCompatTextView>(resourceParser: ResourceParser) : TextViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in AppCompatTextView> = ViewCreator { context, _ -> AppCompatTextView(context) }

}
