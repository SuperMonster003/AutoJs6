package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.QuickContactBadge
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class QuickContactBadgeInflater<V : QuickContactBadge>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ImageViewInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<QuickContactBadge> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): QuickContactBadge {
            return QuickContactBadge(context)
        }
    }

}
