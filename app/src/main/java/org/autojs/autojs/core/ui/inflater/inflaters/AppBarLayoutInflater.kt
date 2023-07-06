package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import com.google.android.material.appbar.AppBarLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class AppBarLayoutInflater<V : AppBarLayout>(resourceParser: ResourceParser) : LinearLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<AppBarLayout> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): AppBarLayout {
            return AppBarLayout(context)
        }
    }

}