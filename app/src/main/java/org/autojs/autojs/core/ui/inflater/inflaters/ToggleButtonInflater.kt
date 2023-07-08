package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.ToggleButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ToggleButtonInflater<V : ToggleButton>(resourceParser: ResourceParser) : CompoundButtonInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ToggleButton> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ToggleButton {
            return ToggleButton(context)
        }
    }

}
