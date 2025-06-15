package org.autojs.autojs.core.image

import android.graphics.Paint
import org.autojs.autojs.runtime.api.augment.colors.Colors

class RhinoPaint(paint: Paint) : Paint(paint) {

    override fun setColor(color: Long) = setColor(Colors.toIntRhino(color))

    fun setColor(color: Any?) = setColor(Colors.toIntRhino(color))

}
