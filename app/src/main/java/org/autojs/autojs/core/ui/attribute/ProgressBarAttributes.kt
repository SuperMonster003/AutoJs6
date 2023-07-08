package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.ProgressBar
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.util.ColorUtils

/**
 * Created by SuperMonster003 on May 20, 2023.
 */
open class ProgressBarAttributes(resourceParser: ResourceParser, view: View) : ViewAttributes(resourceParser, view) {

    override val view = super.view as ProgressBar

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttrs(arrayOf("isIndeterminate", "indeterminate")) { view.isIndeterminate = it.toBoolean() }
        registerAttr("indeterminateDrawable") { view.indeterminateDrawable = drawables.parse(view, it) }
        registerAttr("indeterminateTint") { view.indeterminateTintList = ColorUtils.toColorStateList(view, it) }
        registerAttr("indeterminateTintMode") { view.indeterminateTintMode = TINT_MODES[it] }
        registerAttr("max") { view.max = it.toInt() }
        registerAttr("minHeight") { view.minimumHeight = Dimensions.parseToIntPixel(it, view) }
        registerAttr("minWidth") { view.minimumWidth = Dimensions.parseToIntPixel(it, view) }
        registerAttr("progress") { view.progress = it.toInt() }
        registerAttr("progressBackgroundTint") { view.progressBackgroundTintList = ColorUtils.toColorStateList(view, it) }
        registerAttr("progressBackgroundTintMode") { view.progressBackgroundTintMode = TINT_MODES[it] }
        registerAttr("progressDrawable") { view.progressDrawable = drawables.parse(view, it) }
        registerAttr("progressTint") { view.progressTintList = ColorUtils.toColorStateList(view, it) }
        registerAttr("progressTintMode") { view.progressTintMode = TINT_MODES[it] }
        registerAttr("secondaryProgress") { view.secondaryProgress = it.toInt() }
        registerAttr("secondaryProgressTint") { view.secondaryProgressTintList = ColorUtils.toColorStateList(view, it) }
        registerAttr("secondaryProgressTintMode") { view.secondaryProgressTintMode = TINT_MODES[it] }
        registerAttr("tint", ::setTintList)

        registerAttrUnsupported(
            arrayOf(
                "animationResolution",
                "mirrorForRtl",
                "min",
                "maxWidth",
                "maxHeight",
                "interpolator",
                "indeterminateOnly",
                "indeterminateDuration",
                "indeterminateBehavior",
            )
        )
    }

    private fun setTintList(value: String) {
        val split = parseAttrValue(value)
        when (split.size) {
            1 -> {
                val color = ColorUtils.toColorStateList(view, split[0])
                view.progressTintList = color
                view.progressBackgroundTintList = color
                view.secondaryProgressTintList = color
                view.indeterminateTintList = color
            }
            2 -> {
                val colorA = ColorUtils.toColorStateList(view, split[0])
                val colorB = ColorUtils.toColorStateList(view, split[1])
                view.progressTintList = colorA
                view.progressBackgroundTintList = colorB
                view.secondaryProgressTintList = colorA
                view.indeterminateTintList = colorA
            }
            3 -> {
                val colorA = ColorUtils.toColorStateList(view, split[0])
                val colorB = ColorUtils.toColorStateList(view, split[1])
                val colorC = ColorUtils.toColorStateList(view, split[2])
                view.progressTintList = colorA
                view.progressBackgroundTintList = colorB
                view.secondaryProgressTintList = colorC
                view.indeterminateTintList = colorA
            }
        }
    }

}