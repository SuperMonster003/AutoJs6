package org.autojs.autojs.core.ui.attribute

import android.view.View
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.ColorUtils

/**
 * Modified by SuperMonster003 as of Jan 21, 2023.
 * Transformed by SuperMonster003 on May 19, 2023.
 */
open class FloatingActionButtonAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ImageViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as FloatingActionButton

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttrs(arrayOf("customSize", "fabCustomSize")) { view.customSize = Dimensions.parseToIntPixel(it, view) }
        registerAttrs(arrayOf("size", "fabSize")) { view.size = Dimensions.parseToIntPixel(it, view) }
        registerPixelAttr("elevation") { view.compatElevation = it }
        registerAttr("useCompatPadding") { view.useCompatPadding = it.toBoolean() }
        registerAttr("rippleColor") { view.rippleColor = ColorUtils.parse(view, it) }
        registerAttrs(arrayOf("backgroundTint", "bgTint")) { view.backgroundTintList = ColorUtils.toColorStateList(view, it) }
        registerAttr("ensureMinTouchTargetSize") { view.setEnsureMinTouchTargetSize(it.toBoolean()) }
        registerAttr("maxImageSize") { view.setMaxImageSize(it.toInt()) }
    }

}