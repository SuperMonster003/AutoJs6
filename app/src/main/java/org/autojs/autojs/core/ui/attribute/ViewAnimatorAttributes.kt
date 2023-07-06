package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.ViewAnimator
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class ViewAnimatorAttributes(resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(resourceParser, view) {

    override val view = super.view as ViewAnimator

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("animateFirstView") { view.animateFirstView = it.toBoolean() }
        registerAttr("displayedChild") { view.displayedChild = it.toInt() }
    }

}
