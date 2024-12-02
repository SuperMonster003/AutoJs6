package org.autojs.autojs.core.ui.attribute

import android.view.View
import androidx.appcompat.widget.Toolbar
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.core.ui.inflater.util.Strings
import org.autojs.autojs.core.ui.inflater.util.ValueMapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.ColorUtils

open class ToolbarAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewGroupAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as Toolbar

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("collapseContentDescription") { view.collapseContentDescription = Strings.parse(view, it) }
        registerAttr("collapseIcon") { view.collapseIcon = drawables.parse(view, it) }
        registerAttr("logo") { view.logo = drawables.parse(view, it) }
        registerAttr("logoDescription") { view.logoDescription = Strings.parse(view, it) }
        registerAttr("navigationContentDescription") { view.navigationContentDescription = Strings.parse(view, it) }
        registerAttr("navigationIcon") { view.navigationIcon = drawables.parse(view, it) }
        registerAttr("overflowIcon") { view.overflowIcon = drawables.parse(view, it) }
        registerAttr("popupTheme") { view.popupTheme = POP_UP_THEMES[it] }
        registerAttr("subtitle") { view.subtitle = Strings.parse(view, it) }
        registerAttr("subtitleTextColor") { view.setSubtitleTextColor(ColorUtils.parse(view, it)) }
        registerAttr("title") { view.title = Strings.parse(view, it) }
        registerAttr("titleMargin") { value -> Dimensions.parseToIntPixel(value, view).let { view.setTitleMargin(it, it, it, it) } }
        registerAttr("titleMarginBottom") { view.titleMarginBottom = Dimensions.parseToIntPixel(it, view) }
        registerAttr("titleMarginTop") { view.titleMarginTop = Dimensions.parseToIntPixel(it, view) }
        registerAttr("titleMarginStart") { view.titleMarginStart = Dimensions.parseToIntPixel(it, view) }
        registerAttr("titleMarginEnd") { view.titleMarginEnd = Dimensions.parseToIntPixel(it, view) }
        registerAttr("titleTextColor") { view.setTitleTextColor(ColorUtils.parse(view, it)) }
    }

    companion object {

        private val POP_UP_THEMES = ValueMapper<Int>("popupTheme")
            .map("dark", androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dark_ActionBar)
            .map("light", androidx.appcompat.R.style.ThemeOverlay_AppCompat_ActionBar)

    }

}
