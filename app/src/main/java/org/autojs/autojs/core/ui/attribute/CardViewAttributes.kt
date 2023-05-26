package org.autojs.autojs.core.ui.attribute

import android.view.View
import androidx.cardview.widget.CardView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.util.ColorUtils

open class CardViewAttributes(resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(resourceParser, view) {

    override val view = super.view as CardView

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttrs(arrayOf("cardBackgroundColor", "cardBgColor", "cardBg")) { view.setCardBackgroundColor(ColorUtils.parse(view, it)) }
        registerAttrs(arrayOf("radius", "cardCornerRadius", "cornerRadius")) { view.radius = parseDimensionToPixel(it) }
        registerPixelAttr("cardElevation") { view.cardElevation = it }
        registerAttrs(arrayOf("maxCardElevation", "cardMaxElevation")) { view.maxCardElevation = parseDimensionToPixel(it) }
        registerAttrs(arrayOf("preventCornerOverlap", "cardPreventCornerOverlap")) { view.preventCornerOverlap = it.toBoolean() }
        registerAttrs(arrayOf("useCompatPadding", "cardUseCompatPadding")) { view.useCompatPadding = it.toBoolean() }
        registerAttr("contentPadding") { setContentPadding(it) }
        registerIntPixelAttr("contentPaddingBottom") { setContentPaddingBottom(it) }
        registerIntPixelAttr("contentPaddingLeft") { setContentPaddingLeft(it) }
        registerIntPixelAttr("contentPaddingTop") { setContentPaddingTop(it) }
        registerIntPixelAttr("contentPaddingRight") { setContentPaddingRight(it) }
    }

    private fun setContentPadding(value: String) {
        Dimensions.parseToIntPixelArray(view, value).let { (left, top, right, bottom) ->
            view.setContentPadding(left, top, right, bottom)
        }
    }

    private fun setContentPaddingBottom(value: Int) {
        view.apply { setContentPadding(contentPaddingLeft, contentPaddingTop, contentPaddingRight, value) }
    }

    private fun setContentPaddingLeft(value: Int) {
        view.apply { setContentPadding(value, contentPaddingTop, contentPaddingRight, contentPaddingBottom) }
    }

    private fun setContentPaddingTop(value: Int) {
        view.apply { setContentPadding(contentPaddingLeft, value, contentPaddingRight, contentPaddingBottom) }
    }

    private fun setContentPaddingRight(value: Int) {
        view.apply { setContentPadding(contentPaddingLeft, contentPaddingTop, value, contentPaddingBottom) }
    }

}