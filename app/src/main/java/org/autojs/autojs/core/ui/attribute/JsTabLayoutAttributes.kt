package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsTabLayout

/**
 * Created by SuperMonster003 on May 20, 2023.
 */
class JsTabLayoutAttributes(resourceParser: ResourceParser, view: View) : TabLayoutAttributes(resourceParser, view) {

    override val view = super.view as JsTabLayout

}