package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.ViewSwitcher
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class ViewSwitcherAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewAnimatorAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as ViewSwitcher

}
