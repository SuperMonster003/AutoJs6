package org.autojs.autojs.core.ui.attribute

import android.graphics.PorterDuff
import android.view.View
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import androidx.core.widget.ImageViewCompat
import org.autojs.autojs.core.ui.BiMaps
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.ColorUtils

open class ImageViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as ImageView

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("adjustViewBounds") { view.adjustViewBounds = it.toBoolean() }
        registerIntPixelAttr("baseline") { view.baseline = it }
        registerAttr("baselineAlignBottom") { view.baselineAlignBottom = it.toBoolean() }
        registerAttr("cropToPadding") { view.cropToPadding = it.toBoolean() }
        registerIntPixelAttr("maxHeight") { view.maxHeight = it }
        registerIntPixelAttr("maxWidth") { view.maxWidth = it }
        registerAttr("path") { drawables.setupWithImage(view, scriptRuntime.getPath(it, ::wrapAsPath)) }
        registerAttr("scaleType") { view.scaleType = SCALE_TYPES[it] }
        registerAttr("src") { drawables.setupWithImage(view, scriptRuntime.getPath(it)) }
        registerAttr("tint") {
            // FIXME by Stardust on Oct 13, 2018.
            //  ! Method setImageTintList not working.
            //  ! zh-CN (translated by SuperMonster003 on Jul 29, 2024):
            //  ! 方法 setImageTintList 无效果.
            val mode = ImageViewCompat.getImageTintMode(view)
            view.setColorFilter(ColorUtils.parse(view, it), mode ?: PorterDuff.Mode.SRC_ATOP)
        }
        registerAttr("tintMode") { ImageViewCompat.setImageTintMode(view, TINT_MODES[it]) }
        registerAttr("url") { drawables.setupWithImage(view, wrapAsUrl(it)) }
    }

    private fun wrapAsPath(value: String) = when {
        value.startsWith("file://") -> value
        else -> "file://$value"
    }

    @Suppress("HttpUrlsUsage")
    private fun wrapAsUrl(value: String) = when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        else -> "http://$value"
    }

    private fun ScriptRuntime.getPath(s: String, def: String = s): String {
        return if (this.files.exists(s)) this.files.path(s) else def
    }

    private fun ScriptRuntime.getPath(s: String, def: (String) -> String): String {
        return if (this.files.exists(s)) this.files.path(s) else def(s)
    }

    companion object {

        private val SCALE_TYPES = BiMaps.newBuilder<String, ScaleType>()
            .put("center", ScaleType.CENTER)
            .put("centerCrop", ScaleType.CENTER_CROP)
            .put("centerInside", ScaleType.CENTER_INSIDE)
            .put("fitCenter", ScaleType.FIT_CENTER)
            .put("fitEnd", ScaleType.FIT_END)
            .put("fitStart", ScaleType.FIT_START)
            .put("fitXY", ScaleType.FIT_XY)
            .put("matrix", ScaleType.MATRIX)
            .build()

    }
}