package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.RelativeLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Gravities
import org.autojs.autojs.runtime.ScriptRuntime

open class RelativeLayoutAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewGroupAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as RelativeLayout

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("gravity") { view.gravity = Gravities.parse(it) }
        registerAttr("horizontalGravity") { view.setHorizontalGravity(Gravities.parse(it)) }
        registerAttr("verticalGravity") { view.setVerticalGravity(Gravities.parse(it)) }
    }

}
