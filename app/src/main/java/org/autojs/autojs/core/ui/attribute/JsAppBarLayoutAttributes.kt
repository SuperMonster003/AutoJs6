package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsAppBarLayout

class JsAppBarLayoutAttributes(resourceParser: ResourceParser, view: View) : AppBarLayoutAttributes(resourceParser, view) {

    override val view = super.view as JsAppBarLayout

}