package org.autojs.autojs.core.ui.inflater.inflaters

import androidx.appcompatlegacy.widget.AppCompatTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class TextViewLegacyInflater<V : AppCompatTextView>(resourceParser: ResourceParser) : BaseViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in AppCompatTextView> = ViewCreator { context, _ -> AppCompatTextView(context) }

}
