package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.FrameLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class FrameLayoutInflater<V : FrameLayout>(resourceParser: ResourceParser) : ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> FrameLayout(context) }

}
