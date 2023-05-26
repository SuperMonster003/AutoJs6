package org.autojs.autojs.core.ui.inflater.inflaters

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsAppBarLayout
import org.autojs.autojs6.R

class JsAppBarLayoutInflater(resourceParser: ResourceParser) : AppBarLayoutInflater<JsAppBarLayout>(resourceParser) {

    override fun getCreator() = ViewCreator { context, _ ->
        View.inflate(context, R.layout.js_appbar, null) as JsAppBarLayout
    }

}