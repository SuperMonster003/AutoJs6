package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.ViewAnimator
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class ViewAnimatorAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as ViewAnimator

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("animateFirstView") { view.animateFirstView = it.toBoolean() }
        registerAttr("displayedChild") { view.displayedChild = it.toInt() }
    }

}
