package org.autojs.autojs.core.ui.inflater.inflaters

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsToolbar
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/11/5.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsToolbarInflater(resourceParser: ResourceParser) : ToolbarInflater<JsToolbar>(resourceParser) {

    override fun getCreator() = ViewCreator { context, _ ->
        View.inflate(context, R.layout.js_toolbar, null) as JsToolbar
    }

}