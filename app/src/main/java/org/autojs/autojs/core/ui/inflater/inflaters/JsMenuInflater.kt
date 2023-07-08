package org.autojs.autojs.core.ui.inflater.inflaters

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ActionMenuView
import org.autojs.autojs.core.ui.inflater.EmptyView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsActionMenuView
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.JsToolbarBinding

class JsMenuInflater(resourceParser: ResourceParser) : BaseViewInflater<View>(resourceParser) {

    override fun getCreator() = object : ViewCreator<View> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): View {
            when (parent) {
                is ActionMenuView -> {
                    // val activity = context as Activity
                    // activity.menuInflater.inflate(R.menu.menu_js_sample, parent.menu)
                }
                is MenuItem -> {

                }
            }
            return EmptyView(context)
        }
    }

}