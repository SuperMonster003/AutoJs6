package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.EditText
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class EditTextInflater<V : EditText>(resourceParser: ResourceParser) : TextViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> EditText(context) }

}
