package org.autojs.autojs.core.ui.attribute

import android.os.Build
import android.view.View
import android.widget.ScrollView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class ScrollViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as ScrollView

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttrs(arrayOf("fillViewport", "isFillViewport")) { view.isFillViewport = it.toBoolean() }
        registerAttrs(arrayOf("isSmoothScrollingEnabled", "isSmoothScrolling", "smoothScrollingEnabled", "enableSmoothScrolling")) { view.isSmoothScrollingEnabled = it.toBoolean() }

        registerAttr("topEdgeEffectColor") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.topEdgeEffectColor = parseColor(it)
            }
        }
        registerAttr("edgeEffectColor") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.setEdgeEffectColor(parseColor(it))
            }
        }
        registerAttr("bottomEdgeEffectColor") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.bottomEdgeEffectColor = parseColor(it)
            }
        }
    }

}
