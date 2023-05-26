package org.autojs.autojs.core.ui.attribute

import android.os.Build
import android.view.View
import android.widget.ScrollView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.util.ColorUtils

open class ScrollViewAttributes(resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(resourceParser, view) {

    override val view = super.view as ScrollView

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("isFillViewport") { view.isFillViewport = it.toBoolean() }
        registerAttrs(arrayOf("isSmoothScrollingEnabled", "isSmoothScrolling", "smoothScrollingEnabled", "enableSmoothScrolling")) { view.isSmoothScrollingEnabled = it.toBoolean() }

        registerAttr("topEdgeEffectColor") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.topEdgeEffectColor = ColorUtils.parse(view, it)
            }
        }
        registerAttr("edgeEffectColor") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.setEdgeEffectColor(ColorUtils.parse(view, it))
            }
        }
        registerAttr("bottomEdgeEffectColor") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.bottomEdgeEffectColor = ColorUtils.parse(view, it)
            }
        }
    }

}
