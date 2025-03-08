package org.autojs.autojs.core.image

import androidx.annotation.ColorInt
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.util.ColorUtils

/**
 * Created by Stardust on Dec 31, 2017.
 * Transformed by SuperMonster003 on Mar 8, 2025.
 */
@Suppress("FunctionName")
class Colors {

    @ScriptInterface
    fun rgb(red: Int, green: Int, blue: Int) = ColorUtils.rgb(red, green, blue)

    @ScriptInterface
    fun argb(alpha: Int, red: Int, green: Int, blue: Int) = ColorUtils.argb(alpha, red, green, blue)

    /**
     * @see ColorUtils.luminance
     */
    @ScriptInterface
    fun luminance(@ColorInt color: Int) = ColorUtils.luminance(color)

    @ScriptInterface
    fun parseColor(colorString: String?) = ColorUtils.parseColor(colorString)

    @ScriptInterface
    fun toString(color: Int) = ColorUtils.toString(color)

    @ScriptInterface
    fun RGBToHSV(red: Int, green: Int, blue: Int, hsv: FloatArray?) = ColorUtils.RGBToHSV(red, green, blue, hsv)

    @ScriptInterface
    fun colorToHSV(color: Int, hsv: FloatArray?) = ColorUtils.colorToHSV(color, hsv)

    @ScriptInterface
    fun HSVToColor(hsv: FloatArray?) = ColorUtils.HSVToColor(hsv)

    @ScriptInterface
    fun HSVToColor(alpha: Int, hsv: FloatArray?) = ColorUtils.HSVToColor(alpha, hsv)

    @ScriptInterface
    fun equals(c1: Int, c2: Int) = ColorUtils.equals(c1, c2)

    @ScriptInterface
    fun equals(c1: Int, c2: String?) = ColorUtils.equals(c1, c2)

    @ScriptInterface
    fun equals(c1: String?, c2: Int) = ColorUtils.equals(c1, c2)

    @ScriptInterface
    fun equals(c1: String?, c2: String?) = ColorUtils.equals(c1, c2)

}
