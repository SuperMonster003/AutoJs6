package org.autojs.autojs.core.ui.inflater.inflaters

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsTabLayout
import org.autojs.autojs6.R

/**
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsTabLayoutInflater(resourceParser: ResourceParser) : TabLayoutInflater<JsTabLayout>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsTabLayout> = ViewCreator { context, _ -> View.inflate(context, R.layout.js_tablayout, null) as JsTabLayout }

}