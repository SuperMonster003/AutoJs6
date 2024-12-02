package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class FrameLayoutInflater<V : FrameLayout>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ViewGroupInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<FrameLayout> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): FrameLayout {
            return FrameLayout(context)
        }
    }

}
