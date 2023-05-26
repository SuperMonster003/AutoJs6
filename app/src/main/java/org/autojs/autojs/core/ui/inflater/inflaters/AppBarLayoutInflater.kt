package org.autojs.autojs.core.ui.inflater.inflaters

import com.google.android.material.appbar.AppBarLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class AppBarLayoutInflater<V : AppBarLayout>(resourceParser: ResourceParser) : LinearLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> AppBarLayout(context) }

}