package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsQuickContactBadge
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs6.R

class JsQuickContactBadgeInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : QuickContactBadgeInflater<JsQuickContactBadge>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in JsQuickContactBadge> = object : ViewCreator<JsQuickContactBadge> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsQuickContactBadge {
            return (View.inflate(context, R.layout.js_quickcontactbadge, null) as JsQuickContactBadge).apply {
                setImageToDefault()
            }
        }
    }

}