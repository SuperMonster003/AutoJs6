package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.RelativeLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class RelativeLayoutInflater<V : RelativeLayout>(resourceParser: ResourceParser) : ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> RelativeLayout(context) }

}
