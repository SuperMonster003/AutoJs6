package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsQuickContactBadge

class JsQuickContactBadgeAttributes(resourceParser: ResourceParser, view: View) : QuickContactBadgeAttributes(resourceParser, view) {

    override val view = super.view as JsQuickContactBadge

}