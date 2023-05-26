package org.autojs.autojs.core.ui.attribute

import android.os.Build
import android.view.View
import android.widget.HorizontalScrollView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.util.ColorUtils

open class HorizontalScrollViewAttributes(resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(resourceParser, view) {

    override val view = super.view as HorizontalScrollView

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttrs(arrayOf("isFillViewport", "fillViewport")) { view.isFillViewport = it.toBoolean() }
        registerAttrs(arrayOf("smoothScrollingEnabled", "enableSmoothScrolling", "isSmoothScrollingEnabled", "isSmoothScrolling")) { view.isSmoothScrollingEnabled = it.toBoolean() }

        registerAttr("leftEdgeEffectColor") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.leftEdgeEffectColor = ColorUtils.parse(view, it)
            }
        }
        registerAttr("rightEdgeEffectColor") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.rightEdgeEffectColor = ColorUtils.parse(view, it)
            }
        }
        registerAttr("setEdgeEffectColor") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.setEdgeEffectColor(ColorUtils.parse(view, it))
            }
        }
    }

}
