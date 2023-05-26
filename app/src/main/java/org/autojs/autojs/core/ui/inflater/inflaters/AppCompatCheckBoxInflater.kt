package org.autojs.autojs.core.ui.inflater.inflaters

import androidx.appcompat.widget.AppCompatCheckBox
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class AppCompatCheckBoxInflater<V : AppCompatCheckBox>(resourceParser: ResourceParser) : CheckBoxInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> AppCompatCheckBox(context) }

}
