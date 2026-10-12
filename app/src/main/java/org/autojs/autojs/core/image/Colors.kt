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

    /**
     * The color constants of Auto.js 4's `com.stardust.autojs.core.image.Colors` (values of `android.graphics.Color`);
     * that class name resolves to this class through `StardustClassAliases` (roadmap P7.3), so scripts can keep
     * reading them as static fields.
     * zh-CN: Auto.js 4 `com.stardust.autojs.core.image.Colors` 的颜色常量 (与 `android.graphics.Color` 相同); 该类名经
     * `StardustClassAliases` 解析到本类 (roadmap P7.3), 脚本仍可按静态字段读取.
     */
    companion object {
        const val BLACK = 0xFF000000.toInt()
        const val DKGRAY = 0xFF444444.toInt()
        const val GRAY = 0xFF888888.toInt()
        const val LTGRAY = 0xFFCCCCCC.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00FF00.toInt()
        const val BLUE = 0xFF0000FF.toInt()
        const val YELLOW = 0xFFFFFF00.toInt()
        const val CYAN = 0xFF00FFFF.toInt()
        const val MAGENTA = 0xFFFF00FF.toInt()
        const val TRANSPARENT = 0
    }

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
