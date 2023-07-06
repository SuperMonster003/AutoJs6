package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.QuickContactBadge
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class QuickContactBadgeInflater<V : QuickContactBadge>(resourceParser: ResourceParser) : ImageViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<QuickContactBadge> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): QuickContactBadge {
            return QuickContactBadge(context)
        }
    }

}
