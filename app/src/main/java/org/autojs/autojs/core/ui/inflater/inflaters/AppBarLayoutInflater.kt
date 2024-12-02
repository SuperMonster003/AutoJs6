package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import com.google.android.material.appbar.AppBarLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class AppBarLayoutInflater<V : AppBarLayout>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : LinearLayoutInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<AppBarLayout> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): AppBarLayout {
            return AppBarLayout(context)
        }
    }

}