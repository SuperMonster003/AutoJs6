package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ActionMenuView
import org.autojs.autojs.core.ui.inflater.EmptyView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

// TODO by SuperMonster003 on Jul 7, 2023.
class JsMenuInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : BaseViewInflater<View>(scriptRuntime, resourceParser) {

    override fun getCreator() = object : ViewCreator<View> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): View {
            when (parent) {
                is ActionMenuView -> {
                    // no-op
                }
                is MenuItem -> {

                }
            }
            return EmptyView(context)
        }
    }

}