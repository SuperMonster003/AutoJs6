package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.LinearLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Gravities
import org.autojs.autojs.core.ui.inflater.util.ValueMapper
import org.autojs.autojs.runtime.ScriptRuntime

open class LinearLayoutAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewGroupAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as LinearLayout

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttrs(arrayOf("isBaselineAligned", "baselineAligned")) { view.isBaselineAligned = it.toBoolean() }
        registerAttr("baselineAlignedChildIndex") { view.baselineAlignedChildIndex = it.toInt() }
        registerAttr("divider") { view.dividerDrawable = drawables.parse(view, it) }
        registerAttr("gravity") { view.gravity = Gravities.parse(it) }
        registerAttrs(arrayOf("isMeasureWithLargestChildEnabled", "measureWithLargestChildEnabled", "measureWithLargestChild", "enableMeasureWithLargestChild")) { view.isMeasureWithLargestChildEnabled = it.toBoolean() }
        registerAttr("orientation") { view.orientation = ORIENTATIONS[it] }
        registerAttr("showDividers") { view.showDividers = SHOW_DIVIDERS.split(it) }
        registerAttr("weightSum") { view.weightSum = it.toFloat() }
    }

    companion object {

        @JvmField
        val ORIENTATIONS: ValueMapper<Int> = ValueMapper<Int>("orientation")
            .map("vertical", LinearLayout.VERTICAL)
            .map("horizontal", LinearLayout.HORIZONTAL)

        val SHOW_DIVIDERS: ValueMapper<Int> = ValueMapper<Int>("showDividers")
            .map("beginning", LinearLayout.SHOW_DIVIDER_BEGINNING)
            .map("middle", LinearLayout.SHOW_DIVIDER_MIDDLE)
            .map("end", LinearLayout.SHOW_DIVIDER_END)
            .map("none", LinearLayout.SHOW_DIVIDER_NONE)

    }

}