package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

/**
 * Created by Stardust on 2017/11/4.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
open class LinearLayoutInflater<V : LinearLayout>(resourceParser: ResourceParser) : ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<LinearLayout> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): LinearLayout {
            return LinearLayout(context)
        }
    }

}