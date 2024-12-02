package org.autojs.autojs.core.ui.attribute

import android.view.View
import androidx.viewpager.widget.ViewPager
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.runtime.ScriptRuntime

open class ViewPagerAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewGroupAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as ViewPager

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("pageMargin") { view.pageMargin = Dimensions.parseToIntPixel(it, view) }
        registerAttr("pageMarginDrawable") { view.setPageMarginDrawable(drawables.parse(view, it)) }
    }

}
