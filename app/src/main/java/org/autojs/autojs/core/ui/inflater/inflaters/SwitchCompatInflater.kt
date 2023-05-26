package org.autojs.autojs.core.ui.inflater.inflaters

import androidx.appcompat.widget.SwitchCompat
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class SwitchCompatInflater<V : SwitchCompat>(resourceParser: ResourceParser) : CompoundButtonInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> SwitchCompat(context) }

}
