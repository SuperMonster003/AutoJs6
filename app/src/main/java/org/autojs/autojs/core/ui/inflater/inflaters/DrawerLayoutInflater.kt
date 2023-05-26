package org.autojs.autojs.core.ui.inflater.inflaters

import androidx.drawerlayout.widget.DrawerLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class DrawerLayoutInflater<V : DrawerLayout>(resourceParser: ResourceParser) : ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> DrawerLayout(context) }

}
