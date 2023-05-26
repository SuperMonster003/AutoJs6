package org.autojs.autojs.core.ui.attribute

import android.view.View
import androidx.viewpager.widget.ViewPager
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Dimensions

open class ViewPagerAttributes(resourceParser: ResourceParser, view: View) : ViewGroupAttributes(resourceParser, view) {

    override val view = super.view as ViewPager

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("pageMargin") { view.pageMargin = Dimensions.parseToIntPixel(it, view) }
        registerAttr("pageMarginDrawable") { view.setPageMarginDrawable(drawables.parse(view, it)) }
    }

}
