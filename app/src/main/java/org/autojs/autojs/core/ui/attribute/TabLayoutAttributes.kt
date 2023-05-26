package org.autojs.autojs.core.ui.attribute

import android.graphics.Color
import android.view.View
import com.google.android.material.tabs.TabLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.core.ui.inflater.util.Gravities
import org.autojs.autojs.core.ui.inflater.util.ValueMapper
import org.autojs.autojs.util.ColorUtils

open class TabLayoutAttributes(resourceParser: ResourceParser, view: View) : HorizontalScrollViewAttributes(resourceParser, view) {

    override val view = super.view as TabLayout

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("tabGravity") { view.tabGravity = Gravities.parse(it) }
        registerAttrs(arrayOf("selectedTabIndicatorColor", "tabIndicatorColor")) { view.setSelectedTabIndicatorColor(ColorUtils.parse(view, it)) }
        registerAttr("tabMode") { view.tabMode = TAB_MODES[it] }
        registerAttr("tabRippleColor") { view.tabRippleColor = ColorUtils.toColorStateList(view, it) }
        registerAttr("tabIconTint") { view.tabIconTint = ColorUtils.toColorStateList(view, it) }
        registerAttr("tabGravity") { view.tabGravity = Gravities.parse(it) }
        registerAttrs(arrayOf("selectedTabIndicatorGravity", "tabIndicatorGravity")) { view.setSelectedTabIndicatorGravity(Gravities.parse(it)) }
        registerAttr("selectedTabIndicator") { view.setSelectedTabIndicator(drawables.parse(view, it)) }
        registerAttr("tabSelectedTextColor") { view.setTabTextColors(view.tabTextColors?.defaultColor ?: Color.WHITE, ColorUtils.parse(view, it)) }
        registerAttr("tabTextColor") { view.setTabTextColors(ColorUtils.parse(view, it), view.tabTextColors?.defaultColor ?: Color.WHITE) }
        registerAttrs(arrayOf("isTabIndicatorFullWidth", "tabIndicatorFullWidth")) { view.isTabIndicatorFullWidth = it.toBoolean() }
        registerAttrs(arrayOf("isInlineLabel", "inlineLabel")) { view.isInlineLabel = it.toBoolean() }

        @Suppress("DEPRECATION")
        registerAttr("tabIndicatorHeight") { view.setSelectedTabIndicatorHeight(Dimensions.parseToIntPixel(it, view)) }
    }

    companion object {
        private val TAB_MODES = ValueMapper<Int>("tabMode")
            .map("fixed", TabLayout.MODE_FIXED)
            .map("scrollable", TabLayout.MODE_SCROLLABLE)
    }

}
