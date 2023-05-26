package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.LinearLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

/**
 * Created by Stardust on 2017/11/4.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
open class LinearLayoutInflater<V : LinearLayout>(resourceParser: ResourceParser) : ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> LinearLayout(context) }

}