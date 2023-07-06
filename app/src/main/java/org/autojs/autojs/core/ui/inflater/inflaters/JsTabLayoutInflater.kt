package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsTabLayout
import org.autojs.autojs6.R

/**
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsTabLayoutInflater(resourceParser: ResourceParser) : TabLayoutInflater<JsTabLayout>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsTabLayout> = object : ViewCreator<JsTabLayout> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsTabLayout {
            return View.inflate(context, R.layout.js_tablayout, null) as JsTabLayout
        }
    }

}