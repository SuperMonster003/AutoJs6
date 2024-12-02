package org.autojs.autojs.core.ui.attribute

import android.graphics.Rect
import android.view.SurfaceView
import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class SurfaceViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as SurfaceView

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("clipBounds") { value ->
            val (left, top, right, bottom) = parseAttrValue(value).map { it.toInt() }
            view.clipBounds = Rect(left, top, right, bottom)
        }
        registerAttr("visibility") { view.visibility = VISIBILITY[it] }
        registerAttrs(arrayOf("secure", "isSecure")) { view.setSecure(it.toBoolean()) }
        registerAttrs(arrayOf("zOrderOnTop", "isZOrderOnTop")) { view.setZOrderOnTop(it.toBoolean()) }
        registerAttrs(arrayOf("zOrderMediaOverlay", "isZOrderMediaOverlay")) { view.setZOrderMediaOverlay(it.toBoolean()) }
    }

}
