package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.ActionMenuView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class ActionMenuViewInflater<V : ActionMenuView>(resourceParser: ResourceParser) : LinearLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ActionMenuView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ActionMenuView {
            return ActionMenuView(context)
        }
    }

}
