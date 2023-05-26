package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.ToggleButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ToggleButtonInflater<V : ToggleButton>(resourceParser: ResourceParser) : CompoundButtonInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> ToggleButton(context) }

}
