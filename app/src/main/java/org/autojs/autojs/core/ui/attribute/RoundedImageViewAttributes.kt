package org.autojs.autojs.core.ui.attribute

import android.graphics.Shader
import android.view.View
import android.widget.ImageView
import com.makeramen.roundedimageview.Corner
import com.makeramen.roundedimageview.RoundedImageView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.core.ui.inflater.util.ValueMapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.ColorUtils

open class RoundedImageViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ImageViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as RoundedImageView

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttrs(arrayOf("cornerRadius", "radius")) { view.cornerRadius = Dimensions.parseToPixel(it, view) }
        registerAttrs(arrayOf("cornerRadiusTopLeft", "radiusTopLeft")) { view.setCornerRadius(Dimensions.parseToPixel(it, view), view.getCornerRadius(Corner.TOP_RIGHT), view.getCornerRadius(Corner.BOTTOM_LEFT), view.getCornerRadius(Corner.BOTTOM_RIGHT)) }
        registerAttrs(arrayOf("cornerRadiusTopRight", "radiusTopRight")) { view.setCornerRadius(view.getCornerRadius(Corner.TOP_LEFT), Dimensions.parseToPixel(it, view), view.getCornerRadius(Corner.BOTTOM_LEFT), view.getCornerRadius(Corner.BOTTOM_RIGHT)) }
        registerAttrs(arrayOf("cornerRadiusBottomLeft", "radiusBottomLeft")) { view.setCornerRadius(view.getCornerRadius(Corner.TOP_LEFT), view.getCornerRadius(Corner.TOP_RIGHT), Dimensions.parseToPixel(it, view), view.getCornerRadius(Corner.BOTTOM_RIGHT)) }
        registerAttrs(arrayOf("cornerRadiusBottomRight", "radiusBottomRight")) { view.setCornerRadius(view.getCornerRadius(Corner.TOP_LEFT), view.getCornerRadius(Corner.TOP_RIGHT), view.getCornerRadius(Corner.BOTTOM_LEFT), Dimensions.parseToPixel(it, view)) }
        registerAttrs(arrayOf("isOval", "oval")) { view.isOval = it.toBoolean() }
        registerAttrs(arrayOf("tileX", "tileModeX")) { view.tileModeX = TILE_MODES[it] }
        registerAttrs(arrayOf("tileY", "tileModeY")) { view.tileModeY = TILE_MODES[it] }
        registerAttr("borderWidth") { view.borderWidth = Dimensions.parseToPixel(it, view) }
        registerAttr("borderColor") { view.borderColor = ColorUtils.parse(view, it) }
        registerAttr("scaleType") { view.scaleType = SCALE_TYPES[it] }
    }

    companion object {

        private val SCALE_TYPES: ValueMapper<ImageView.ScaleType> = ValueMapper<ImageView.ScaleType>("scaleType")
            .map("center", ImageView.ScaleType.CENTER)
            .map("centerCrop", ImageView.ScaleType.CENTER_CROP)
            .map("centerInside", ImageView.ScaleType.CENTER_INSIDE)
            .map("fitCenter", ImageView.ScaleType.FIT_CENTER)
            .map("fitEnd", ImageView.ScaleType.FIT_END)
            .map("fitStart", ImageView.ScaleType.FIT_START)
            .map("fitXY", ImageView.ScaleType.FIT_XY)
            .map("matrix", ImageView.ScaleType.MATRIX)

        private val TILE_MODES: ValueMapper<Shader.TileMode> = ValueMapper<Shader.TileMode>("tileMode")
            .map("clamp", Shader.TileMode.CLAMP)
            .map("mirror", Shader.TileMode.MIRROR)
            .map("repeat", Shader.TileMode.REPEAT)

    }

}
