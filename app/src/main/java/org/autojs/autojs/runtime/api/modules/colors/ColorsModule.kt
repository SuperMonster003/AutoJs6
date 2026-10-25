package org.autojs.autojs.runtime.api.modules.colors

import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Build
import org.autojs.autojs.core.image.ColorDetector
import org.autojs.autojs.core.image.ColorTable
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.images.Images
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.ScriptToString
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.Alpha
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.OMNI_COLOR
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.alpha as alphaOf
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.blue as blueOf
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.green as greenOf
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.number
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.quote
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.rangeError
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.red as redOf
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.typeError
import org.autojs.autojs.runtime.api.modules.jsox.JsoxValues
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.color.ColorHarmony
import org.autojs.autojs.theme.color.ColorQuantizer
import org.autojs.autojs.theme.color.ColorVisionSimulation
import org.autojs.autojs.theme.color.OkColor
import org.autojs.autojs.theme.color.OkLab
import org.autojs.autojs.theme.color.OkLch
import org.autojs.autojs.theme.color.ThemeColorDerivation
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.dtoa.DecimalFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.autojs.autojs.core.image.Colors as CoreColors
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `colors` module on the new script module layer (roadmap P2.3), with its sub-module `colors.theme`
 * ([ColorsThemeModule]); `Color` is [ColorModule].
 *
 * Members, their order, the color tables (`colors.android` / `css` / `web` / `material`), the getters (`all`, hidden, and
 * `themeColor`) and the prototype chain (the four color tables, then the runtime's `org.autojs.autojs.core.image.Colors`
 * with `parseColor` / `equals`) are those of the current module. Color values come from [ColorValues] (no Android), so
 * the module runs on the plain JVM except `toColorStateList`, `setPaintColor`, `extract`, `themeColor` and `build`
 * (Color instances), which need Android classes.
 *
 * Behavior follows the documentation (`api/color.md`, `api/colorType.md`) and the d.ts (ROADMAP appendix E, "P2.3 new
 * deviations"): `toHex` takes the alpha modes `'keep'` / `'none'` / `'auto'` and shortens any color to `#RGB`; channels that
 * are kept (the other channels of `setRed`, the RGB of `hsla`, the color of `toHsla` / `toHsvString` / `toHslString`) stay
 * integers instead of going through the component rules again (where 1 meant 255); the relative setters scale the channel
 * as an integer; the `*Double` functions and the alpha of `toHsva` / `toHsla` divide by 255; alpha components accept text
 * such as `'25%'`; `rgba('#RRGGBB')` is opaque; `toHsv(color, result)` and the other result forms accept a Java `float[]`;
 * `setPaintColor` accepts the script's `Paint` (a wrapped Java object); `summary` is `hex(...), rgba(r,g,b/a), int(...)`.
 * Arguments follow the real signatures (T4; extra arguments are a TypeError), errors are TypeError / RangeError (D10).
 *
 * zh-CN: 新脚本模块层上的 colors 模块 (P2.3), 子模块 colors.theme 见 [ColorsThemeModule], Color 见 [ColorModule]. 成员及其顺序, 颜色表, getter (隐藏的
 * all 与 themeColor) 与原型链 (四个颜色表, 然后是运行时的 core.image.Colors) 与现有模块相同. 颜色取值来自 [ColorValues] (不依赖 Android), 因此除
 * toColorStateList, setPaintColor, extract, themeColor 与 build (Color 实例) 外都可在纯 JVM 上运行. 行为以文档与 d.ts 为准 (附录 E "P2.3 新发现的偏差"),
 * 参数按真实签名 (T4, 多余参数为 TypeError), 错误为 TypeError / RangeError (D10).
 */
@ScriptModule(name = "colors")
class ColorsModule(private val runtime: ScriptRuntime?) {

    @ScriptProperty
    val android: Any = ColorTable.Android

    @ScriptProperty
    val css: Any = ColorTable.Css

    @ScriptProperty
    val web: Any = ColorTable.Web

    @ScriptProperty
    val material: Any = ColorTable.Material

    /** The color tables, then the runtime's `Colors` object (as the current module, five levels). */
    @ScriptPrototype
    fun prototypes(): List<Any> = listOf(ColorTable.Android, ColorTable.Css, ColorTable.Web, ColorTable.Material, (runtime?.colors as CoreColors?) ?: CoreColors())

    /** `colors.toString(color, alphaOrLength?)` as `toHex`; without a color, the description of the module object. */
    @ScriptFunction(name = "toString", toStringRole = ScriptToString.FUNCTIONAL)
    fun toStringOf(color: Any? = Undefined.instance, alphaOrLength: Any? = null): String =
        if (Undefined.isUndefined(color)) "[object Object]" else hex("colors.toString", color, alphaOrLength)

    @ScriptFunction
    fun toInt(color: Any?): Int = int(color, "colors.toInt")

    /** [alphaOrLength]: `'auto'` (default), `'keep'` / true, `'none'` / false, or a length of 8, 6 or 3. */
    @ScriptFunction
    fun toHex(color: Any?, alphaOrLength: Any? = null): String = hex("colors.toHex", color, alphaOrLength)

    @ScriptFunction
    fun toFullHex(color: Any?): String = ColorValues.hexOf(int(color, "colors.toFullHex"), Alpha.KEEP)

    @ScriptFunction
    fun alpha(color: Any?, options: Any? = null): Double = channel("colors.alpha", alphaOf(int(color, "colors.alpha")), options)

    @ScriptFunction
    fun getAlpha(color: Any?, options: Any? = null): Double = channel("colors.getAlpha", alphaOf(int(color, "colors.getAlpha")), options)

    @ScriptFunction
    fun alphaDouble(color: Any?, options: Any? = null): Double = fraction("colors.alphaDouble", alphaOf(int(color, "colors.alphaDouble")), options)

    @ScriptFunction
    fun getAlphaDouble(color: Any?, options: Any? = null): Double = fraction("colors.getAlphaDouble", alphaOf(int(color, "colors.getAlphaDouble")), options)

    @ScriptFunction
    fun setAlpha(color: Any?, alpha: Any?): Int = withChannel(int(color, "colors.setAlpha"), 24, ColorValues.alphaChannel(alpha, "colors.setAlpha", "alpha"))

    @ScriptFunction
    fun setAlphaRelative(color: Any?, percentage: Any?): Int = relative("colors.setAlphaRelative", color, percentage, 24)

    @ScriptFunction
    fun removeAlpha(color: Any?): Int = withChannel(int(color, "colors.removeAlpha"), 24, 0)

    @ScriptFunction
    fun red(color: Any?, options: Any? = null): Double = channel("colors.red", redOf(int(color, "colors.red")), options)

    @ScriptFunction
    fun getRed(color: Any?, options: Any? = null): Double = channel("colors.getRed", redOf(int(color, "colors.getRed")), options)

    @ScriptFunction
    fun redDouble(color: Any?, options: Any? = null): Double = fraction("colors.redDouble", redOf(int(color, "colors.redDouble")), options)

    @ScriptFunction
    fun getRedDouble(color: Any?, options: Any? = null): Double = fraction("colors.getRedDouble", redOf(int(color, "colors.getRedDouble")), options)

    @ScriptFunction
    fun setRed(color: Any?, red: Any?): Int = withChannel(int(color, "colors.setRed"), 16, component("colors.setRed", "red", red))

    @ScriptFunction
    fun setRedRelative(color: Any?, percentage: Any?): Int = relative("colors.setRedRelative", color, percentage, 16)

    @ScriptFunction
    fun removeRed(color: Any?): Int = withChannel(int(color, "colors.removeRed"), 16, 0)

    @ScriptFunction
    fun green(color: Any?, options: Any? = null): Double = channel("colors.green", greenOf(int(color, "colors.green")), options)

    @ScriptFunction
    fun getGreen(color: Any?, options: Any? = null): Double = channel("colors.getGreen", greenOf(int(color, "colors.getGreen")), options)

    @ScriptFunction
    fun greenDouble(color: Any?, options: Any? = null): Double = fraction("colors.greenDouble", greenOf(int(color, "colors.greenDouble")), options)

    @ScriptFunction
    fun getGreenDouble(color: Any?, options: Any? = null): Double = fraction("colors.getGreenDouble", greenOf(int(color, "colors.getGreenDouble")), options)

    @ScriptFunction
    fun setGreen(color: Any?, green: Any?): Int = withChannel(int(color, "colors.setGreen"), 8, component("colors.setGreen", "green", green))

    @ScriptFunction
    fun setGreenRelative(color: Any?, percentage: Any?): Int = relative("colors.setGreenRelative", color, percentage, 8)

    @ScriptFunction
    fun removeGreen(color: Any?): Int = withChannel(int(color, "colors.removeGreen"), 8, 0)

    @ScriptFunction
    fun blue(color: Any?, options: Any? = null): Double = channel("colors.blue", blueOf(int(color, "colors.blue")), options)

    @ScriptFunction
    fun getBlue(color: Any?, options: Any? = null): Double = channel("colors.getBlue", blueOf(int(color, "colors.getBlue")), options)

    @ScriptFunction
    fun blueDouble(color: Any?, options: Any? = null): Double = fraction("colors.blueDouble", blueOf(int(color, "colors.blueDouble")), options)

    @ScriptFunction
    fun getBlueDouble(color: Any?, options: Any? = null): Double = fraction("colors.getBlueDouble", blueOf(int(color, "colors.getBlueDouble")), options)

    @ScriptFunction
    fun setBlue(color: Any?, blue: Any?): Int = withChannel(int(color, "colors.setBlue"), 0, component("colors.setBlue", "blue", blue))

    @ScriptFunction
    fun setBlueRelative(color: Any?, percentage: Any?): Int = relative("colors.setBlueRelative", color, percentage, 0)

    @ScriptFunction
    fun removeBlue(color: Any?): Int = withChannel(int(color, "colors.removeBlue"), 0, 0)

    /** `rgb(color)` (any alpha dropped) or `rgb([red, green, blue])`. */
    @ScriptFunction
    fun rgb(color: Any?): Int = rgbOf("colors.rgb", color)

    @ScriptFunction
    fun rgb(red: Any?, green: Any?, blue: Any?): Int = rgbOf("colors.rgb", red, green, blue)

    /** `argb(color)` (a `#AARRGGBB` code, alpha kept) or `argb([alpha, red, green, blue])`. */
    @ScriptFunction
    fun argb(color: Any?): Int = when (color) {
        is NativeArray -> componentsOf("colors.argb", color, 4).let { (a, r, g, b) -> argbOf("colors.argb", a, r, g, b) }
        else -> int(color, "colors.argb")
    }

    @ScriptFunction
    fun argb(alpha: Any?, red: Any?, green: Any?, blue: Any?): Int = argbOf("colors.argb", alpha, red, green, blue)

    /** `rgba('#RRGGBBAA')` (`#RRGGBB` and `#RGB` are opaque), `rgba(color)` for other colors, or `rgba([red, green, blue, alpha])`. */
    @ScriptFunction
    fun rgba(color: Any?): Int = rgbaOf("colors.rgba", color)

    @ScriptFunction
    fun rgba(components: Any?, alpha: Any?): Int = componentsOf("colors.rgba", components, 3, "rgb").let { (r, g, b) -> argbOf("colors.rgba", alpha, r, g, b) }

    @ScriptFunction
    fun rgba(red: Any?, green: Any?, blue: Any?, alpha: Any?): Int = argbOf("colors.rgba", alpha, red, green, blue)

    @ScriptFunction
    fun hsv(components: Any?): Int = componentsOf("colors.hsv", components, 3).let { (h, s, v) -> hsvOf("colors.hsv", 255, h, s, v) }

    @ScriptFunction
    fun hsv(hue: Any?, saturation: Any?, value: Any?): Int = hsvOf("colors.hsv", 255, hue, saturation, value)

    @ScriptFunction
    fun hsva(components: Any?): Int = componentsOf("colors.hsva", components, 4).let { (h, s, v, a) -> hsvaOf("colors.hsva", h, s, v, a) }

    @ScriptFunction
    fun hsva(components: Any?, alpha: Any?): Int = componentsOf("colors.hsva", components, 3, "hsv").let { (h, s, v) -> hsvaOf("colors.hsva", h, s, v, alpha) }

    @ScriptFunction
    fun hsva(hue: Any?, saturation: Any?, value: Any?, alpha: Any?): Int = hsvaOf("colors.hsva", hue, saturation, value, alpha)

    @ScriptFunction
    fun hsl(components: Any?): Int = componentsOf("colors.hsl", components, 3).let { (h, s, l) -> hslOf("colors.hsl", h, s, l) }

    @ScriptFunction
    fun hsl(hue: Any?, saturation: Any?, lightness: Any?): Int = hslOf("colors.hsl", hue, saturation, lightness)

    @ScriptFunction
    fun hsla(components: Any?): Int = componentsOf("colors.hsla", components, 4).let { (h, s, l, a) -> hslaOf("colors.hsla", h, s, l, a) }

    @ScriptFunction
    fun hsla(components: Any?, alpha: Any?): Int = componentsOf("colors.hsla", components, 3, "hsl").let { (h, s, l) -> hslaOf("colors.hsla", h, s, l, alpha) }

    @ScriptFunction
    fun hsla(hue: Any?, saturation: Any?, lightness: Any?, alpha: Any?): Int = hslaOf("colors.hsla", hue, saturation, lightness, alpha)

    @ScriptFunction
    fun toRgb(color: Any?): List<Int> = int(color, "colors.toRgb").let { listOf(redOf(it), greenOf(it), blueOf(it)) }

    @ScriptFunction
    fun toRgb(red: Any?, green: Any?, blue: Any?): List<Int> = rgbOf("colors.toRgb", red, green, blue).let { listOf(redOf(it), greenOf(it), blueOf(it)) }

    /** `[red, green, blue, alpha]`; `options.maxAlpha` 1 gives the alpha as a fraction. */
    @ScriptFunction
    fun toRgba(color: Any?, options: Any? = null): List<Double> {
        val c = int(color, "colors.toRgba")
        val a = channel("colors.toRgba", alphaOf(c), options, "maxAlpha")
        return listOf(redOf(c).toDouble(), greenOf(c).toDouble(), blueOf(c).toDouble(), a)
    }

    @ScriptFunction
    fun toArgb(color: Any?, options: Any? = null): List<Double> {
        val c = int(color, "colors.toArgb")
        val a = channel("colors.toArgb", alphaOf(c), options, "maxAlpha")
        return listOf(a, redOf(c).toDouble(), greenOf(c).toDouble(), blueOf(c).toDouble())
    }

    /** `[hue, saturation, value]`; a color given as one argument may also be `[red, green, blue]`. */
    @ScriptFunction
    fun toHsv(color: Any?): List<Double> = hsvOf(rgbOf("colors.toHsv", color), FloatArray(3))

    /** Also writes the components into [result], a Java `float[]` of at least 3 elements, and returns all its elements. */
    @ScriptFunction
    fun toHsv(color: Any?, result: Any?): List<Double> = hsvOf(int(color, "colors.toHsv"), floats("colors.toHsv", result, 3))

    @ScriptFunction
    fun toHsv(red: Any?, green: Any?, blue: Any?): List<Double> = hsvOf(rgbOf("colors.toHsv", red, green, blue), FloatArray(3))

    @ScriptFunction
    fun toHsv(red: Any?, green: Any?, blue: Any?, result: Any?): List<Double> = hsvOf(rgbOf("colors.toHsv", red, green, blue), floats("colors.toHsv", result, 3))

    /** `[hue, saturation, value, alpha]`, the alpha as a fraction of 255. */
    @ScriptFunction
    fun toHsva(color: Any?): List<Double> = hsvaOf(int(color, "colors.toHsva"), null)

    /** Also writes the four components into [result], a Java `float[]` of at least 4 elements. */
    @ScriptFunction
    fun toHsva(color: Any?, result: Any?): List<Double> = hsvaOf(int(color, "colors.toHsva"), floats("colors.toHsva", result, 4))

    @ScriptFunction
    fun toHsva(red: Any?, green: Any?, blue: Any?, alpha: Any?): List<Double> = hsvaOf(argbOf("colors.toHsva", alpha, red, green, blue), null)

    @ScriptFunction
    fun toHsva(red: Any?, green: Any?, blue: Any?, alpha: Any?, result: Any?): List<Double> =
        hsvaOf(argbOf("colors.toHsva", alpha, red, green, blue), floats("colors.toHsva", result, 4))

    /** `[hue, saturation, lightness]`; a color given as one argument may also be `[red, green, blue]`. */
    @ScriptFunction
    fun toHsl(color: Any?): List<Double> = hslOf(rgbOf("colors.toHsl", color), FloatArray(3))

    @ScriptFunction
    fun toHsl(color: Any?, result: Any?): List<Double> = hslOf(int(color, "colors.toHsl"), floats("colors.toHsl", result, 3))

    @ScriptFunction
    fun toHsl(red: Any?, green: Any?, blue: Any?): List<Double> = hslOf(rgbOf("colors.toHsl", red, green, blue), FloatArray(3))

    @ScriptFunction
    fun toHsl(red: Any?, green: Any?, blue: Any?, result: Any?): List<Double> = hslOf(rgbOf("colors.toHsl", red, green, blue), floats("colors.toHsl", result, 3))

    /** `[hue, saturation, lightness, alpha]`, the alpha as a fraction of 255. */
    @ScriptFunction
    fun toHsla(color: Any?): List<Double> = int(color, "colors.toHsla").let { c -> hslOf(c, FloatArray(3)) + alphaOf(c) / 255.0 }

    /** [alpha] is a fraction (0..1) or a channel (above 1, up to 255). */
    @ScriptFunction
    fun toHsla(red: Any?, green: Any?, blue: Any?, alpha: Any?): List<Double> =
        hslOf(rgbOf("colors.toHsla", red, green, blue), FloatArray(3)) + ColorValues.fraction(alpha, "colors.toHsla", "alpha")

    @ScriptFunction
    fun toRgbString(color: Any?): String = int(color, "colors.toRgbString").let { c -> "rgb(${redOf(c)}, ${greenOf(c)}, ${blueOf(c)})" }

    @ScriptFunction
    fun toRgbString(red: Any?, green: Any?, blue: Any?): String = rgbOf("colors.toRgbString", red, green, blue).let { c -> "rgb(${redOf(c)}, ${greenOf(c)}, ${blueOf(c)})" }

    /** [options]: `keepTrailingZeroForFullAlpha` (default true, an opaque alpha is written as `1.0`), as a boolean or an object property. */
    @ScriptFunction
    fun toRgbaString(color: Any?, options: Any? = null): String = int(color, "colors.toRgbaString").let { c ->
        "rgba(${redOf(c)}, ${greenOf(c)}, ${blueOf(c)}, ${alphaText(c, options)})"
    }

    @ScriptFunction
    fun toArgbString(color: Any?, options: Any? = null): String = int(color, "colors.toArgbString").let { c ->
        "argb(${alphaText(c, options)}, ${redOf(c)}, ${greenOf(c)}, ${blueOf(c)})"
    }

    @ScriptFunction
    fun toHsvString(color: Any?): String = hsvText(int(color, "colors.toHsvString"))

    @ScriptFunction
    fun toHsvString(red: Any?, green: Any?, blue: Any?): String = hsvText(rgbOf("colors.toHsvString", red, green, blue))

    @ScriptFunction
    fun toHsvaString(color: Any?, options: Any? = null): String = int(color, "colors.toHsvaString").let { c ->
        "hsva(${hsvText(c).removePrefix("hsv(").removeSuffix(")")}, ${alphaText(c, options)})"
    }

    @ScriptFunction
    fun toHslString(color: Any?): String = hslText(int(color, "colors.toHslString"))

    @ScriptFunction
    fun toHslString(red: Any?, green: Any?, blue: Any?): String = hslText(rgbOf("colors.toHslString", red, green, blue))

    @ScriptFunction
    fun toHslaString(color: Any?, options: Any? = null): String = int(color, "colors.toHslaString").let { c ->
        "hsla(${hslText(c).removePrefix("hsl(").removeSuffix(")")}, ${alphaText(c, options)})"
    }

    /**
     * Whether [colorA] and [colorB] are similar by the detection [algorithm] (default `'diff'`) within [thresholdOrOptions]
     * (default 4), or with an options object `{ threshold | similarity, algorithm }`. A threshold that is not a number
     * falls back to 4, as in the current module.
     */
    @ScriptFunction
    fun isSimilar(colorA: Any?, colorB: Any?, thresholdOrOptions: Any? = null, algorithm: Any? = null): Boolean {
        val function = "colors.isSimilar"
        val a = int(colorA, function, "colorA")
        val b = int(colorB, function, "colorB")
        if (thresholdOrOptions is NativeObject && JsoxValues.isNullish(algorithm)) {
            val options = thresholdOrOptions
            val threshold = when {
                ScriptableObject.hasProperty(options, "threshold") -> {
                    if (ScriptableObject.hasProperty(options, "similarity")) {
                        throw typeError(function, ScriptMessages.argumentCannotBe("options", "{ threshold, similarity }"))
                    }
                    ColorValues.component(ScriptableObject.getProperty(options, "threshold"), function, "options.threshold").roundToInt()
                }
                ScriptableObject.hasProperty(options, "similarity") -> {
                    ((1 - ColorValues.component(ScriptableObject.getProperty(options, "similarity"), function, "options.similarity")) * 255).roundToInt()
                }
                else -> ColorValues.DEFAULT_THRESHOLD
            }
            return detector(function, a, property(options, "algorithm"), threshold).detectColor(redOf(b), greenOf(b), blueOf(b))
        }
        val threshold = ColorValues.component(thresholdOrOptions).takeUnless { it.isNaN() }?.roundToInt() ?: ColorValues.DEFAULT_THRESHOLD
        return detector(function, a, algorithm, threshold).detectColor(redOf(b), greenOf(b), blueOf(b))
    }

    /** Equality of the RGB channels, or of the whole color when [alphaMatters]. */
    @ScriptFunction
    fun isEqual(colorA: Any?, colorB: Any?, alphaMatters: Boolean? = null): Boolean = when (alphaMatters) {
        true -> int(colorA, "colors.isEqual", "colorA") == int(colorB, "colors.isEqual", "colorB")
        else -> rgbOf("colors.isEqual", colorA, "colorA") == rgbOf("colors.isEqual", colorB, "colorB")
    }

    /** Distance on the threshold scale (0..255): `isSimilar(a, b, t, algorithm)` is `distance(a, b, algorithm) <= t`. */
    @ScriptFunction
    fun distance(colorA: Any?, colorB: Any?, algorithm: Any? = null): Double {
        val name = if (algorithm is NativeObject) property(algorithm, "algorithm") else algorithm
        val b = int(colorB, "colors.distance", "colorB")
        return detector("colors.distance", int(colorA, "colors.distance", "colorA"), name, 0).distance(redOf(b), greenOf(b), blueOf(b))
    }

    /** The RGB channels inverted, the alpha kept. */
    @ScriptFunction
    fun invert(color: Any?): Int = int(color, "colors.invert") xor 0x00FFFFFF

    /** [ratio] (default 0.5) is the weight of [colorB], a number of 0..1 or text such as `'25%'`. */
    @ScriptFunction
    fun blend(colorA: Any?, colorB: Any?, ratio: Any? = null): Int {
        val weight = when {
            JsoxValues.isNullish(ratio) -> 0.5
            else -> ColorValues.component(ratio, "colors.blend", "ratio").also {
                if (it !in 0.0..1.0) throw rangeError("colors.blend", ScriptMessages.argumentType("ratio", "number in [0, 1]", number(it)))
            }
        }
        return ColorValues.blend(int(colorA, "colors.blend", "colorA"), int(colorB, "colors.blend", "colorB"), weight.toFloat())
    }

    /** WCAG contrast ratio (1..21) of [foreground] on an opaque [background]. */
    @ScriptFunction
    fun contrast(foreground: Any?, background: Any?): Double {
        val bg = opaque("colors.contrast", background)
        return ColorValues.contrast(int(foreground, "colors.contrast", "foreground"), bg)
    }

    @ScriptFunction
    fun toColorStateList(color: Any?, vararg colors: Any?): ColorStateList {
        val first = int(color, "colors.toColorStateList")
        if (colors.isEmpty()) return ColorStateList.valueOf(first)
        val rest = colors.mapIndexed { i, c -> int(c, "colors.toColorStateList", "colors[$i]") }
        return ColorStateList(arrayOf(intArrayOf()), (listOf(first) + rest).toIntArray())
    }

    /** `paint.setARGB(...)` on Android 10 and above (where `setColor(number)` picks the long overload), `setColor` before. */
    @ScriptFunction
    fun setPaintColor(paint: Paint, color: Any?) {
        val c = int(color, "colors.setPaintColor")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) paint.setARGB(alphaOf(c), redOf(c), greenOf(c), blueOf(c)) else paint.setColor(c)
    }

    @ScriptFunction
    fun luminance(color: Any?): Double = ColorValues.luminance(int(color, "colors.luminance"))

    /** A Color instance, as `Color(...)`: no argument (black), a color, `(red, green, blue)` or `(red, green, blue, alpha)`. */
    @ScriptFunction(arity = [0, 4])
    fun build(vararg args: Any?): Any = ColorModule.newColor(this, "colors.build", args)

    /** `hex(#RRGGBB), rgba(r,g,b/a), int(...)`, the alpha as a fraction with one or two decimals. */
    @ScriptFunction
    fun summary(color: Any?): String {
        val c = int(color, "colors.summary")
        val a = DecimalFormatter.toFixed(alphaOf(c) / 255.0, 2).let { if (it.endsWith("0")) it.dropLast(1) else it }
        return "hex(${ColorValues.hexOf(c, Alpha.AUTO)}), rgba(${redOf(c)},${greenOf(c)},${blueOf(c)}/$a), int($c)"
    }

    // Perceptual color functions (theme color roadmap P6.1).

    /** OKLab `[L, a, b]`. */
    @ScriptFunction
    fun toOklab(color: Any?): List<Double> = OkColor.toOkLab(int(color, "colors.toOklab")).let { listOf(it.l, it.a, it.b) }

    @ScriptFunction
    fun oklab(components: Any?): Int = perceptual("colors.oklab", components) { (l, a, b, alpha) -> oklabOf(l, a, b, alpha) }

    /** A color from OKLab components; out of sRGB, the lightness and hue are kept and the chroma reduced. */
    @ScriptFunction
    fun oklab(l: Any?, a: Any?, b: Any?, alpha: Any? = null): Int = oklabOf(l, a, b, alpha)

    /** OKLCH `[L, C, h]`. */
    @ScriptFunction
    fun toOklch(color: Any?): List<Double> = OkColor.toOkLch(int(color, "colors.toOklch")).let { listOf(it.l, it.c, it.h) }

    @ScriptFunction
    fun oklch(components: Any?): Int = perceptual("colors.oklch", components) { (l, c, h, alpha) -> oklchOf(l, c, h, alpha) }

    @ScriptFunction
    fun oklch(l: Any?, c: Any?, h: Any?, alpha: Any? = null): Int = oklchOf(l, c, h, alpha)

    /**
     * The color nearest to [color] in lightness (same hue) with a contrast of at least [ratio] (default 4.5, WCAG AA)
     * against an opaque [background]; the alpha of [color] is kept.
     */
    @ScriptFunction
    fun ensureContrast(color: Any?, background: Any?, ratio: Any? = null): Int {
        val function = "colors.ensureContrast"
        val c = int(color, function)
        val bg = opaque(function, background)
        val target = when {
            JsoxValues.isNullish(ratio) -> DEFAULT_CONTRAST_RATIO
            else -> finite(function, "ratio", ratio).also {
                if (it !in 1.0..21.0) throw rangeError(function, ScriptMessages.argumentType("ratio", "number in [1, 21]", number(it)))
            }
        }
        val result = ThemeColorDerivation.ensureContrast(c or ColorValues.OPAQUE, bg, target)
        return (result and 0xFFFFFF) or (c and ColorValues.OPAQUE)
    }

    /** Harmony colors of [type], the color itself first: complementary, analogous, triadic, splitComplementary, tetradic. */
    @ScriptFunction
    fun harmony(color: Any?, type: Any?): List<Int> {
        val c = int(color, "colors.harmony")
        val harmony = (if (JsoxValues.isNullish(type)) null else ColorHarmony.Type.of(RhinoScriptRuntime.toString(type)))
            ?: throw rangeError("colors.harmony", ScriptMessages.argumentType("type", ColorHarmony.Type.entries.joinToString(" | ") { "'${it.key}'" }, quote(type)))
        return ColorHarmony.harmony(c, harmony)
    }

    /** [count] tones of the color's hue from light to dark (10 by default, 2..20). */
    @ScriptFunction
    fun tones(color: Any?, count: Any? = null): List<Int> {
        val c = int(color, "colors.tones")
        val n = when {
            JsoxValues.isNullish(count) -> DEFAULT_TONE_COUNT
            else -> integer("colors.tones", "count", count, 2, MAX_TONE_COUNT)
        }
        return ColorHarmony.tones(c, n)
    }

    /** `{ primary, accent, dark }` derived from a primary color, as the theme color does with "Derived colors" on "Auto". */
    @ScriptFunction
    fun derive(cx: Context, scope: VarScope, color: Any?): Scriptable {
        val primary = int(color, "colors.derive") or ColorValues.OPAQUE
        val (accent, dark) = ThemeColorDerivation.accentAndDark(primary, null, null, derive = true)
        return cx.newObject(scope).also {
            it.put("primary", it, primary)
            it.put("accent", it, accent)
            it.put("dark", it, dark)
        }
    }

    @ScriptFunction
    fun nightVariant(color: Any?): Int = ThemeColorDerivation.nightVariant(int(color, "colors.nightVariant") or ColorValues.OPAQUE)

    /** How the color looks with a color vision [deficiency]: protanopia, deuteranopia or tritanopia (Machado 2009). */
    @ScriptFunction
    fun simulate(color: Any?, deficiency: Any?): Int {
        val c = int(color, "colors.simulate")
        val key = if (JsoxValues.isNullish(deficiency)) null else RhinoScriptRuntime.toString(deficiency)
        val type = ColorVisionSimulation.Deficiency.entries.firstOrNull { it.key == key }
            ?: throw rangeError("colors.simulate", ScriptMessages.argumentType("deficiency", ColorVisionSimulation.Deficiency.entries.joinToString(" | ") { "'${it.key}'" }, quote(deficiency)))
        return ColorVisionSimulation.simulate(c, type)
    }

    /**
     * Dominant colors of an image, the most frequent first. Options: `count` (1..16, default 5), `region` (as in
     * images.findColor) and `step` (sample every n pixels; by default about 65536 samples). Pixels with alpha below 128
     * are ignored; a one-shot image is recycled afterwards.
     */
    @ScriptFunction
    fun extract(image: Any?, options: Any? = null): List<Int> {
        val function = "colors.extract"
        val img = ColorValues.unwrap(image) as? ImageWrapper ?: throw typeError(function, "image", "ImageWrapper", image)
        if (!JsoxValues.isNullish(options) && options !is NativeObject) throw typeError(function, "options", "Object", options)
        val opt = options as? NativeObject
        val count = property(opt, "count")?.let { integer(function, "options.count", it, 1, ColorQuantizer.MAX_COLORS) } ?: DEFAULT_EXTRACT_COUNT
        try {
            val region = Images.buildRegionInternal(img, property(opt, "region"))
            val step = property(opt, "step")?.let { integer(function, "options.step", it, 1, Int.MAX_VALUE) }
                ?: ceil(sqrt(region.width.toDouble() * region.height / EXTRACT_SAMPLES)).toInt().coerceAtLeast(1)
            val bitmap = img.bitmap
            val row = IntArray(region.width)
            val samples = ArrayList<Int>()
            var y = region.y
            while (y < region.y + region.height) {
                bitmap.getPixels(row, 0, region.width, region.x, y, region.width, 1)
                var x = 0
                while (x < region.width) {
                    samples += row[x]
                    x += step
                }
                y += step
            }
            return ColorQuantizer.quantize(samples.toIntArray(), count).map { it.color }
        } finally {
            img.shoot()
        }
    }

    /** Every constant of the four color tables by name (hidden from enumeration, as in the current module). */
    @ScriptGetter(attributes = ScriptAttributes.DONTENUM)
    val all: Map<String, Int>
        get() = ColorValues.all

    /** The current theme color (the same instance as `autojs.themeColor`). */
    @ScriptGetter
    val themeColor: ThemeColor
        get() = ThemeColorManager.currentThemeColor

    // Conversions shared with Color (ColorModule).

    internal fun int(color: Any?, function: String, param: String = "color"): Int = ColorValues.toInt(color, function, param)

    /** `rgb(color)`: a color with its alpha set to 255, or `[red, green, blue]` components. */
    internal fun rgbOf(function: String, color: Any?, param: String = "color"): Int = when (color) {
        is NativeArray -> componentsOf(function, color, 3, param).let { (r, g, b) -> rgbOf(function, r, g, b) }
        else -> ColorValues.OPAQUE or (int(color, function, param) and 0xFFFFFF)
    }

    internal fun rgbOf(function: String, red: Any?, green: Any?, blue: Any?): Int =
        ColorValues.channels(listOf(red, green, blue), function, RGB).let { (r, g, b) -> ColorValues.rgb(r, g, b) }

    internal fun argbOf(function: String, alpha: Any?, red: Any?, green: Any?, blue: Any?): Int {
        val (r, g, b) = ColorValues.channels(listOf(red, green, blue), function, RGB)
        return ColorValues.argb(ColorValues.alphaChannel(alpha, function, "alpha"), r, g, b)
    }

    internal fun rgbaOf(function: String, color: Any?): Int {
        if (color is NativeArray) return componentsOf(function, color, 4).let { (r, g, b, a) -> argbOf(function, a, r, g, b) }
        val text = (ColorValues.unwrap(color) as? CharSequence)?.toString()
        if (text != null && text.trim().startsWith("#")) {
            val full = ColorValues.hexOf(text, Alpha.KEEP) ?: throw rangeError(function, ScriptMessages.argumentType("color", OMNI_COLOR, quote(text)))
            // "#RRGGBBAA" moves its alpha to the front; "#RRGGBB" and "#RGB" are opaque.
            val hex = if (text.length == 9) "#" + full.substring(7, 9) + full.substring(1, 7) else full
            return int(hex, function)
        }
        return int(color, function)
    }

    internal fun hsvOf(function: String, alpha: Int, hue: Any?, saturation: Any?, value: Any?): Int = ColorValues.hsvToColor(
        alpha,
        floatArrayOf(ColorValues.hue(hue, function, "hue"), ColorValues.percentage(saturation, function, "saturation"), ColorValues.percentage(value, function, "value")),
    )

    internal fun hsvaOf(function: String, hue: Any?, saturation: Any?, value: Any?, alpha: Any?): Int =
        hsvOf(function, ColorValues.alphaChannel(alpha, function, "alpha"), hue, saturation, value)

    internal fun hslOf(function: String, hue: Any?, saturation: Any?, lightness: Any?): Int = ColorValues.hslToColor(
        floatArrayOf(ColorValues.hue(hue, function, "hue"), ColorValues.percentage(saturation, function, "saturation"), ColorValues.percentage(lightness, function, "lightness")),
    )

    internal fun hslaOf(function: String, hue: Any?, saturation: Any?, lightness: Any?, alpha: Any?): Int =
        (hslOf(function, hue, saturation, lightness) and 0xFFFFFF) or (ColorValues.alphaChannel(alpha, function, "alpha") shl 24)

    // Helpers.

    private fun hex(function: String, color: Any?, alphaOrLength: Any?): String {
        val c = int(color, function)
        return when (val mode = ColorValues.unwrap(alphaOrLength)) {
            null, Undefined.instance -> ColorValues.hexOf(c, Alpha.AUTO)
            is Boolean -> ColorValues.hexOf(c, if (mode) Alpha.KEEP else Alpha.NONE)
            is Number -> when (mode.toInt()) {
                8 -> ColorValues.hexOf(c, Alpha.KEEP)
                6 -> ColorValues.hexOf(c, Alpha.NONE)
                3 -> ColorValues.shortHexOf(c) ?: throw rangeError(function, ScriptMessages.argumentType("color", "color of #RGB", ColorValues.hexOf(c, Alpha.NONE)))
                else -> throw rangeError(function, ScriptMessages.argumentType("alphaOrLength", HEX_MODES, number(mode.toDouble())))
            }
            is CharSequence -> when (mode.toString()) {
                "auto" -> ColorValues.hexOf(c, Alpha.AUTO)
                "keep" -> ColorValues.hexOf(c, Alpha.KEEP)
                "none" -> ColorValues.hexOf(c, Alpha.NONE)
                else -> throw rangeError(function, ScriptMessages.argumentType("alphaOrLength", HEX_MODES, quote(mode)))
            }
            else -> throw typeError(function, "alphaOrLength", HEX_MODES, alphaOrLength)
        }
    }

    /** A channel of 0..255, or a fraction of 255 when `options[key]` is 1 (1 or 255, nullish for 255). */
    private fun channel(function: String, channel: Int, options: Any?, key: String = "max"): Double =
        if (maxIsOne(function, options, key)) channel / 255.0 else channel.toDouble()

    /** A fraction of 255 (`options.max` is checked as for [channel] but does not change the result). */
    private fun fraction(function: String, channel: Int, options: Any?): Double {
        maxIsOne(function, options, "max")
        return channel / 255.0
    }

    private fun maxIsOne(function: String, options: Any?, key: String): Boolean {
        val max = property(options as? NativeObject, key) ?: return false
        return when (RhinoScriptRuntime.toNumber(max)) {
            255.0 -> false
            1.0 -> true
            else -> {
                val error = ScriptMessages.argumentType("options.$key", "1 | 255", quote(max))
                throw if (RhinoScriptRuntime.toNumber(max).isNaN()) typeError(function, error) else rangeError(function, error)
            }
        }
    }

    /** [color] with the byte at [shift] (24 alpha, 16 red, 8 green, 0 blue) replaced by [channel]. */
    private fun withChannel(color: Int, shift: Int, channel: Int): Int = (color and (0xFF shl shift).inv()) or (channel shl shift)

    /** A single component for a setter: below 1 a fraction of 255, otherwise the number (`setRed(c, 1)` sets 1). */
    private fun component(function: String, param: String, value: Any?): Int =
        ColorValues.unit8(ColorValues.component(value, function, param), function, param)

    /** The channel at [shift] scaled by [percentage] (0.5, `'50%'`, 1.5, ...), rounded, at most 255. */
    private fun relative(function: String, color: Any?, percentage: Any?, shift: Int): Int {
        val c = int(color, function)
        val p = ColorValues.component(percentage, function, "percentage")
        if (p < 0) throw rangeError(function, ScriptMessages.argumentType("percentage", "number >= 0", number(p)))
        val scaled = ((c ushr shift) and 0xFF) * p
        return withChannel(c, shift, minOf(255.0, scaled).roundToInt())
    }

    /** The elements of a JS array of [length] components, or a TypeError. */
    private fun componentsOf(function: String, value: Any?, length: Int, param: String = "components"): List<Any?> {
        if (value !is NativeArray) throw typeError(function, param, "Array", value)
        if (value.length != length.toLong()) {
            throw typeError(function, ScriptMessages.argumentLength(param, length, value.length.toInt()))
        }
        return List(length) { i -> value.get(i, value).let { if (it === Scriptable.NOT_FOUND) Undefined.instance else it } }
    }

    /** A Java `float[]` of at least [min] elements (a wrapped Java array is unwrapped). */
    private fun floats(function: String, value: Any?, min: Int): FloatArray {
        val array = ColorValues.unwrap(value) as? FloatArray ?: throw typeError(function, "result", "float[]", value)
        if (array.size < min) throw rangeError(function, ScriptMessages.argumentLength("result", min, array.size))
        return array
    }

    private fun hsvOf(color: Int, out: FloatArray): List<Double> {
        ColorValues.rgbToHsv(redOf(color), greenOf(color), blueOf(color), out)
        return out.map { it.toDouble() }
    }

    private fun hsvaOf(color: Int, result: FloatArray?): List<Double> {
        val hsv = FloatArray(3).also { ColorValues.rgbToHsv(redOf(color), greenOf(color), blueOf(color), it) }
        val a = alphaOf(color) / 255.0
        result?.let { r ->
            hsv.forEachIndexed { i, x -> r[i] = x }
            r[3] = a.toFloat()
        }
        return hsv.map { it.toDouble() } + a
    }

    private fun hslOf(color: Int, out: FloatArray): List<Double> {
        ColorValues.rgbToHsl(redOf(color), greenOf(color), blueOf(color), out)
        return out.map { it.toDouble() }
    }

    private fun hsvText(color: Int): String {
        val hsv = FloatArray(3).also { ColorValues.rgbToHsv(redOf(color), greenOf(color), blueOf(color), it) }
        return "hsv(${ColorValues.hueText(hsv[0].toDouble())}, ${ColorValues.percentText(hsv[1].toDouble())}, ${ColorValues.percentText(hsv[2].toDouble())})"
    }

    private fun hslText(color: Int): String {
        val hsl = FloatArray(3).also { ColorValues.rgbToHsl(redOf(color), greenOf(color), blueOf(color), it) }
        return "hsl(${ColorValues.hueText(hsl[0].toDouble())}, ${ColorValues.percentText(hsl[1].toDouble())}, ${ColorValues.percentText(hsl[2].toDouble())})"
    }

    private fun alphaText(color: Int, options: Any?): String {
        val keep = when (val o = ColorValues.unwrap(options)) {
            is NativeObject -> property(o, "keepTrailingZeroForFullAlpha")?.let { RhinoScriptRuntime.toBoolean(it) } != false
            is Boolean -> o
            else -> true
        }
        return ColorValues.alphaText(alphaOf(color) / 255.0, keep)
    }

    private fun detector(function: String, color: Int, algorithm: Any?, threshold: Int): ColorDetector {
        val name = if (JsoxValues.isNullish(algorithm)) ColorValues.DEFAULT_ALGORITHM else RhinoScriptRuntime.toString(algorithm)
        if (name.trim().lowercase() !in ALGORITHMS) {
            throw rangeError(function, ScriptMessages.argumentType("algorithm", ALGORITHMS.joinToString(" | ") { "'$it'" }, quote(algorithm)))
        }
        return ColorDetector.get(color, name, threshold)
    }

    /** An opaque background color, or a RangeError. */
    private fun opaque(function: String, background: Any?): Int = int(background, function, "background").also {
        if (alphaOf(it) != 255) throw rangeError(function, ScriptMessages.argumentType("background", "opaque color", ColorValues.hexOf(it, Alpha.KEEP)))
    }

    /** A finite number (numbers and numeric text); NaN is a TypeError, an infinite number a RangeError. */
    private fun finite(function: String, param: String, value: Any?): Double {
        val x = ColorValues.component(value, function, param)
        if (x.isInfinite()) throw rangeError(function, ScriptMessages.argumentType(param, "finite number", number(x)))
        return x
    }

    private fun integer(function: String, param: String, value: Any?, min: Int, max: Int): Int {
        val x = finite(function, param, value)
        if (x != Math.floor(x) || x < min || x > max) {
            throw rangeError(function, ScriptMessages.argumentType(param, if (max == Int.MAX_VALUE) "integer >= $min" else "integer in [$min, $max]", number(x)))
        }
        return x.toInt()
    }

    /** `[c1, c2, c3]` or `[c1, c2, c3, alpha]` of a JS array. */
    private inline fun perceptual(function: String, components: Any?, build: (List<Any?>) -> Int): Int {
        if (components !is NativeArray) throw typeError(function, "components", "Array", components)
        val n = components.length.toInt()
        if (n !in 3..4) throw typeError(function, ScriptMessages.argumentLength("components", 3, n))
        return build(List(4) { i -> if (i < n) components.get(i, components) else null })
    }

    private fun oklabOf(l: Any?, a: Any?, b: Any?, alpha: Any?): Int {
        val function = "colors.oklab"
        val lab = OkLab(lightness(function, l), finite(function, "a", a), finite(function, "b", b))
        val color = if (OkColor.isInGamut(lab)) OkColor.fromOkLab(lab) else OkColor.fromOkLch(OkColor.toOkLch(lab))
        return withAlpha(function, color, alpha)
    }

    private fun oklchOf(l: Any?, c: Any?, h: Any?, alpha: Any?): Int {
        val function = "colors.oklch"
        val chroma = finite(function, "C", c).also {
            if (it < 0) throw rangeError(function, ScriptMessages.argumentType("C", "number >= 0", number(it)))
        }
        val lch = OkLch(lightness(function, l), chroma, OkColor.normalizeHue(finite(function, "h", h)))
        return withAlpha(function, OkColor.fromOkLch(lch), alpha)
    }

    /** Lightness in 0..1, also as a percentage such as "50%". */
    private fun lightness(function: String, value: Any?): Double = finite(function, "L", value).also {
        if (it !in 0.0..1.0) throw rangeError(function, ScriptMessages.argumentType("L", "number in [0, 1]", number(it)))
    }

    private fun withAlpha(function: String, color: Int, alpha: Any?): Int = when {
        JsoxValues.isNullish(alpha) -> color or ColorValues.OPAQUE
        else -> (color and 0xFFFFFF) or (ColorValues.alphaChannel(alpha, function, "alpha") shl 24)
    }

    /** `options[key]` (prototype chain included), or null when the options or the value are missing or nullish. */
    private fun property(options: Any?, key: String): Any? {
        if (options !is Scriptable) return null
        val value = ScriptableObject.getProperty(options, key)
        return if (value === Scriptable.NOT_FOUND || JsoxValues.isNullish(value)) null else value
    }

    private companion object {
        val RGB = listOf("red", "green", "blue")
        const val HEX_MODES = "'auto' | 'keep' | 'none' | boolean | 8 | 6 | 3"
        val ALGORITHMS = listOf("diff", "rgb", "rgb+", "hs", "h", "equal", "oklab")
        const val DEFAULT_CONTRAST_RATIO = 4.5
        const val DEFAULT_TONE_COUNT = 10
        const val MAX_TONE_COUNT = 20
        const val DEFAULT_EXTRACT_COUNT = 5

        /** Default number of pixels sampled by `colors.extract`. */
        const val EXTRACT_SAMPLES = 65536.0
    }

}
