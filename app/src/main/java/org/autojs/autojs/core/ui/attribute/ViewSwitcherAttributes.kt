package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.ViewSwitcher
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class ViewSwitcherAttributes(resourceParser: ResourceParser, view: View): ViewAnimatorAttributes(resourceParser, view) {

    override val view = super.view as ViewSwitcher

}
