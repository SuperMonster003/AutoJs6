package org.autojs.autojs.runtime.api.modules.colors

import org.autojs.autojs.core.image.ColorTable
import org.autojs.autojs.runtime.api.augment.colors.ColorNativeObject
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.jsox.NumberxModule
import org.autojs.autojs.theme.ThemeColor
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.lang.reflect.Modifier
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Color values of the `colors` module on the new script module layer (roadmap P2.3), without Android: the color int
 * layout and parsing of `android.graphics.Color` and the host's `ColorUtils` (hex codes, color names of the four color
 * tables), the HSV conversions of Skia (`SkRGBToHSV` / `SkHSVToColor`, behind `Color.RGBToHSV` / `Color.HSVToColor`), and
 * the HSL, luminance, contrast and blending of androidx `ColorUtils` (core 1.15.0), all with the same float arithmetic,
 * so the module runs on the plain JVM (unit tests and the differential test against the obsolete JS module).
 *
 * zh-CN: 新脚本模块层 colors 模块的颜色取值 (P2.3), 不依赖 Android: android.graphics.Color 与宿主 ColorUtils 的颜色整数布局与解析 (颜色代码, 四个颜色表的
 * 颜色名称), Skia 的 HSV 转换 (Color.RGBToHSV / HSVToColor 背后的 SkRGBToHSV / SkHSVToColor), 以及 androidx ColorUtils (core 1.15.0) 的 HSL, 亮度, 对比度与
 * 混合, 浮点运算方式相同, 因此模块可在纯 JVM 上运行 (单元测试与对旧 JS 模块的差分测试).
 */
internal object ColorValues {

    const val OPAQUE = -0x1000000

    /** Default threshold and algorithm of `colors.isSimilar` / `colors.distance`. */
    const val DEFAULT_THRESHOLD = 4
    const val DEFAULT_ALGORITHM = "diff"

    /** `SK_ScalarNearlyZero`. */
    private const val NEARLY_ZERO = 1f / (1 shl 12)

    private val SHORT_HEX = Regex("#\\w{3}")
    private val HEX = Regex("#[A-F\\d]{3}([A-F\\d]{3}([A-F\\d]{2})?)?", RegexOption.IGNORE_CASE)
    private val OPAQUE_HEX = Regex("#FF[A-F\\d]{6}", RegexOption.IGNORE_CASE)

    /** Names accepted by `colors.toHex` / `toInt`: the four color tables in order, as `ColorTable.getColorByName` reads them. */
    private val TABLES: List<Class<*>> = listOf(ColorTable.Android::class.java, ColorTable.Css::class.java, ColorTable.Web::class.java, ColorTable.Material::class.java)

    private fun intFields(table: Class<*>) = table.declaredFields.filter { it.type == Int::class.javaPrimitiveType && Modifier.isStatic(it.modifiers) }

    /** Name (upper case, without underscores) to color, the first table and field winning. */
    private val names: Map<String, Int> by lazy {
        HashMap<String, Int>().also { map ->
            TABLES.forEach { table ->
                intFields(table).forEach { field -> map.putIfAbsent(field.name.replace("_", ""), field.getInt(null)) }
            }
        }
    }

    /** `colors.all`: every constant of the four color tables by its own name, the first table winning. */
    val all: Map<String, Int> by lazy {
        LinkedHashMap<String, Int>().also { map ->
            TABLES.forEach { table -> intFields(table).forEach { field -> map.putIfAbsent(field.name, field.getInt(null)) } }
        }
    }

    // Color ints (android.graphics.Color).

    fun alpha(color: Int) = color ushr 24
    fun red(color: Int) = (color shr 16) and 0xFF
    fun green(color: Int) = (color shr 8) and 0xFF
    fun blue(color: Int) = color and 0xFF
    fun argb(alpha: Int, red: Int, green: Int, blue: Int) = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    fun rgb(red: Int, green: Int, blue: Int) = OPAQUE or (red shl 16) or (green shl 8) or blue

    // Hex codes and names (org.autojs.autojs.util.ColorUtils, android.graphics.Color.parseColor).

    enum class Alpha { AUTO, KEEP, NONE }

    /** `#RRGGBB` for an opaque color, `#AARRGGBB` otherwise (`ColorUtils.toString`). */
    fun hexOf(color: Int): String {
        val text = Integer.toHexString(color).uppercase().padStart(8, '0')
        return if (text.startsWith("FF")) "#" + text.substring(2) else "#$text"
    }

    /** The color named [name] (case, hyphens and underscores ignored), or null. */
    fun colorOfName(name: String): Int? {
        val key = StringBuilder(name.length)
        for (c in name) if (c != '-' && c != '_') key.append(c)
        return names[key.toString().uppercase()]
    }

    /** Whether [text] is an optionally signed run of ASCII digits (a color int as text), checked without a regex. */
    private fun isDigits(text: String): Boolean {
        val start = if (text.startsWith('+') || text.startsWith('-')) 1 else 0
        if (start == text.length) return false
        for (i in start until text.length) if (text[i] !in '0'..'9') return false
        return true
    }

    /** [text] as a hex code in the form [alpha] asks for (`ColorUtils.toHex(String, alpha)`), or null when it is not a color. */
    fun hexOf(text: String, alpha: Alpha): String? {
        var color = text
        when {
            color.startsWith('#') -> if (SHORT_HEX.matches(color)) color = "#${color[1]}${color[1]}${color[2]}${color[2]}${color[3]}${color[3]}"
            isDigits(color) -> return hexOf(color.toLongOrNull()?.toInt() ?: return null, alpha)
            else -> return colorOfName(color)?.let { hexOf(it, alpha) }
        }
        if (!HEX.matches(color)) return null
        return when (alpha) {
            Alpha.KEEP -> (if (color.length == 7) "#FF" + color.substring(1) else color).uppercase()
            Alpha.NONE -> ("#" + color.substring(color.length - 6)).uppercase()
            Alpha.AUTO -> if (OPAQUE_HEX.matches(color)) ("#" + color.substring(3)).uppercase() else color.uppercase()
        }
    }

    fun hexOf(color: Int, alpha: Alpha): String = hexOf(hexOf(color), alpha)!!

    /** The `#RGB` form of a color whose channels are all doubled digits (`#AA9966CC` is `#96C`), or null. */
    fun shortHexOf(color: Int): String? {
        val r = red(color)
        val g = green(color)
        val b = blue(color)
        if (r % 17 != 0 || g % 17 != 0 || b % 17 != 0) return null
        return "#" + listOf(r, g, b).joinToString("") { Integer.toHexString(it / 17).uppercase() }
    }

    /** A hex code of [hexOf] as a color int (`android.graphics.Color.parseColor`). */
    private fun parseHex(hex: String): Int {
        val value = hex.substring(1).toLong(16)
        return if (hex.length == 7) (value or 0xFF000000L).toInt() else value.toInt()
    }

    /** [text] as a color int (`ColorUtils.toInt(String)`), or null when it is not a color. */
    fun parse(text: String): Int? = hexOf(text, Alpha.AUTO)?.let(::parseHex)

    /** A script value unwrapped for color conversion (Java objects; script objects such as Color instances stay). */
    fun unwrap(value: Any?): Any? = if (value is Wrapper && value !is NativeArray && value !is NativeObject) value.unwrap() else value

    /**
     * An OmniColor (a color int, a hex code or color name, a `Color` instance or a `ThemeColor`) as a color int; other types
     * are a TypeError, text that is no color a RangeError.
     */
    fun toInt(value: Any?, function: String, param: String): Int = when (val v = unwrap(value)) {
        is ColorNativeObject -> v.color
        is ThemeColor -> v.colorPrimary
        is Number -> v.toLong().toInt()
        is CharSequence -> parse(v.toString()) ?: throw rangeError(function, ScriptMessages.argumentType(param, OMNI_COLOR, quote(v)))
        else -> throw typeError(function, param, OMNI_COLOR, value)
    }

    // Color components.

    /** A component: numbers, and text as `Numberx.parseAny` reads it ("50%", "3:2", "0x10"); NaN for anything else. */
    fun component(value: Any?): Double = NumberxModule.parseAny(value)

    /** [value] as a component; NaN (text that is no number, objects, null, undefined) is a TypeError. */
    fun component(value: Any?, function: String, param: String): Double {
        val x = component(value)
        if (x.isNaN()) throw typeError(function, param, "ColorComponent", value)
        return x
    }

    /** A channel of 0..255: below 1 a fraction of 255, otherwise the number rounded (at most 255); negative is a RangeError. */
    fun unit8(x: Double, function: String, param: String): Int = when {
        x >= 1 -> minOf(255.0, x).roundToInt()
        x < 0 -> throw rangeError(function, ScriptMessages.argumentType(param, "ColorComponent >= 0", number(x)))
        else -> (x * 255.0).roundToInt()
    }

    /**
     * Red, green and blue components as channels: when every component is at most 1 and not all of them are 1, 1 means
     * 255 (all are fractions), otherwise each component goes through [unit8] (so `rgb(1, 1, 1)` is `#010101`).
     */
    fun channels(values: List<Any?>, function: String, params: List<String>): IntArray {
        val xs = values.mapIndexed { i, v -> component(v, function, params[i]) }
        val fractions = xs.all { it <= 1.0 } && xs.any { it != 1.0 }
        return IntArray(xs.size) { i -> if (fractions && xs[i] == 1.0) 255 else unit8(xs[i], function, params[i]) }
    }

    /** An alpha component: 1 is 255 (opaque), otherwise as [unit8]. */
    fun alphaChannel(value: Any?, function: String, param: String): Int {
        val x = component(value, function, param)
        return if (x == 1.0) 255 else unit8(x, function, param)
    }

    /** A component of 0..1 given as a fraction (0..1, 1 included) or as a channel (above 1, up to 255), for `toHsla(r, g, b, a)`. */
    fun fraction(value: Any?, function: String, param: String): Double {
        val v = unwrap(value)
        if (v !is Number) throw typeError(function, param, "number", value)
        val x = v.toDouble()
        return when {
            x in 0.0..1.0 -> x
            x in 1.0..255.0 -> x / 255
            else -> throw rangeError(function, ScriptMessages.argumentType(param, "number in [0, 255]", number(x)))
        }
    }

    /** A hue in degrees: |x| < 1 is a fraction of a turn, the result moved by whole turns into 0..360. */
    fun hue(value: Any?, function: String, param: String): Float {
        var x = component(value, function, param)
        if (abs(x) < 1.0) x *= 360
        return NumberxModule.clampTo(x, listOf(0, 360), 360).toFloat()
    }

    /** A saturation, value or lightness: 0..1 as it is, above 1 a percentage up to 100. */
    fun percentage(value: Any?, function: String, param: String): Float {
        val x = component(value, function, param)
        if (x < 0 || x > 100) throw rangeError(function, ScriptMessages.argumentType(param, "number in [0, 100]", number(x)))
        return (if (x <= 1.0) x else x / 100).toFloat()
    }

    // HSV (Skia).

    /** `SkRGBToHSV`: hue 0..360, saturation and value 0..1, in float arithmetic. */
    fun rgbToHsv(red: Int, green: Int, blue: Int, out: FloatArray) {
        val min = minOf(red, minOf(green, blue))
        val max = maxOf(red, maxOf(green, blue))
        val delta = max - min
        val v = max.toFloat() / 255
        if (delta == 0) {
            out[0] = 0f
            out[1] = 0f
            out[2] = v
            return
        }
        val s = delta.toFloat() / max
        var h = when (max) {
            red -> (green - blue).toFloat() / delta
            green -> 2f + (blue - red).toFloat() / delta
            else -> 4f + (red - green).toFloat() / delta
        }
        h *= 60
        if (h < 0) h += 360f
        out[0] = h
        out[1] = s
        out[2] = v
    }

    /** `SkHSVToColor`. */
    fun hsvToColor(alpha: Int, hsv: FloatArray): Int {
        val s = pin(hsv[1])
        val v = pin(hsv[2])
        val vByte = round2int(v * 255)
        if (abs(s) <= NEARLY_ZERO) return argb(alpha, vByte, vByte, vByte)
        val hx = if (hsv[0] < 0 || hsv[0] >= 360f) 0f else hsv[0] / 60
        val w = floor(hx)
        val f = hx - w
        val p = round2int((1f - s) * v * 255)
        val q = round2int((1f - (s * f)) * v * 255)
        val t = round2int((1f - (s * (1f - f))) * v * 255)
        return when (w.toInt()) {
            0 -> argb(alpha, vByte, t, p)
            1 -> argb(alpha, q, vByte, p)
            2 -> argb(alpha, p, vByte, t)
            3 -> argb(alpha, p, q, vByte)
            4 -> argb(alpha, t, p, vByte)
            else -> argb(alpha, vByte, p, q)
        }
    }

    /** `SkTPin(x, 0, 1)`: `std::max(lo, std::min(x, hi))`, so NaN gives 0. */
    private fun pin(x: Float): Float {
        val upper = if (1f < x) 1f else x
        return if (0f < upper) upper else 0f
    }

    /** `sk_float_round2int`. */
    private fun round2int(x: Float): Int = floor(x + 0.5f).toInt()

    // HSL, luminance, contrast and blending (androidx.core.graphics.ColorUtils).

    fun rgbToHsl(red: Int, green: Int, blue: Int, out: FloatArray) {
        val rf = red / 255f
        val gf = green / 255f
        val bf = blue / 255f
        val max = maxOf(rf, maxOf(gf, bf))
        val min = minOf(rf, minOf(gf, bf))
        val deltaMaxMin = max - min
        var h: Float
        val s: Float
        val l = (max + min) / 2f
        if (max == min) {
            h = 0f
            s = 0f
        } else {
            h = when (max) {
                rf -> ((gf - bf) / deltaMaxMin) % 6f
                gf -> ((bf - rf) / deltaMaxMin) + 2f
                else -> ((rf - gf) / deltaMaxMin) + 4f
            }
            s = deltaMaxMin / (1f - abs(2f * l - 1f))
        }
        h = (h * 60f) % 360f
        if (h < 0) h += 360f
        out[0] = constrain(h, 0f, 360f)
        out[1] = constrain(s, 0f, 1f)
        out[2] = constrain(l, 0f, 1f)
    }

    fun hslToColor(hsl: FloatArray): Int {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]
        val c = (1f - abs(2 * l - 1f)) * s
        val m = l - 0.5f * c
        val x = c * (1f - abs((h / 60f % 2f) - 1f))
        var r = 0
        var g = 0
        var b = 0
        when (h.toInt() / 60) {
            0 -> { r = Math.round(255 * (c + m)); g = Math.round(255 * (x + m)); b = Math.round(255 * m) }
            1 -> { r = Math.round(255 * (x + m)); g = Math.round(255 * (c + m)); b = Math.round(255 * m) }
            2 -> { r = Math.round(255 * m); g = Math.round(255 * (c + m)); b = Math.round(255 * (x + m)) }
            3 -> { r = Math.round(255 * m); g = Math.round(255 * (x + m)); b = Math.round(255 * (c + m)) }
            4 -> { r = Math.round(255 * (x + m)); g = Math.round(255 * m); b = Math.round(255 * (c + m)) }
            5, 6 -> { r = Math.round(255 * (c + m)); g = Math.round(255 * m); b = Math.round(255 * (x + m)) }
        }
        return rgb(constrain(r, 0, 255), constrain(g, 0, 255), constrain(b, 0, 255))
    }

    private fun constrain(amount: Float, low: Float, high: Float) = if (amount < low) low else minOf(amount, high)

    private fun constrain(amount: Int, low: Int, high: Int) = if (amount < low) low else minOf(amount, high)

    /** `calculateLuminance`: the Y component of CIE XYZ (sRGB, D65) over 100. */
    fun luminance(color: Int): Double {
        val sr = linear(red(color))
        val sg = linear(green(color))
        val sb = linear(blue(color))
        return 100 * (sr * 0.2126 + sg * 0.7152 + sb * 0.0722) / 100
    }

    private fun linear(channel: Int): Double {
        val s = channel / 255.0
        return if (s < 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }

    /** `calculateContrast` with an opaque [background]; a translucent foreground is composited over it first. */
    fun contrast(foreground: Int, background: Int): Double {
        val fg = if (alpha(foreground) < 255) composite(foreground, background) else foreground
        val luminance1 = luminance(fg) + 0.05
        val luminance2 = luminance(background) + 0.05
        return maxOf(luminance1, luminance2) / minOf(luminance1, luminance2)
    }

    /** `compositeColors`: [foreground] over [background] (source over, not premultiplied). */
    fun composite(foreground: Int, background: Int): Int {
        val bgAlpha = alpha(background)
        val fgAlpha = alpha(foreground)
        val a = 0xFF - (((0xFF - bgAlpha) * (0xFF - fgAlpha)) / 0xFF)
        fun component(fg: Int, bg: Int) = if (a == 0) 0 else ((0xFF * fg * fgAlpha) + (bg * bgAlpha * (0xFF - fgAlpha))) / (a * 0xFF)
        return argb(a, component(red(foreground), red(background)), component(green(foreground), green(background)), component(blue(foreground), blue(background)))
    }

    /** `blendARGB`: [ratio] is the weight of [color2]; channels are truncated. */
    fun blend(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1 - ratio
        val a = alpha(color1) * inverseRatio + alpha(color2) * ratio
        val r = red(color1) * inverseRatio + red(color2) * ratio
        val g = green(color1) * inverseRatio + green(color2) * ratio
        val b = blue(color1) * inverseRatio + blue(color2) * ratio
        return argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
    }

    // Text forms (`ColorUtils.roundTo*String`, decimal rounding of the shortest double text, half up).

    fun alphaText(fraction: Double, keepTrailingZeroForFullAlpha: Boolean): String =
        decimal(fraction, 1).let { if (keepTrailingZeroForFullAlpha && it == "1") "1.0" else it }

    fun hueText(hue: Double): String = decimal(hue, 0)

    fun percentText(fraction: Double): String = decimal(fraction * 100.0, 1) + "%"

    private fun decimal(x: Double, scale: Int): String = x.toBigDecimal().setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    // Errors (D10): wrong types are TypeErrors, values out of the accepted set RangeErrors, prefixed with the function name.

    const val OMNI_COLOR = "OmniColor"

    fun typeError(function: String, param: String, expected: String, value: Any?): RuntimeException =
        RhinoScriptRuntime.typeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentType(param, expected, ScriptArgs.describe(value))))

    fun typeError(function: String, cause: String): RuntimeException = RhinoScriptRuntime.typeError(ScriptMessages.failedToCall(function, cause))

    fun rangeError(function: String, cause: String): RuntimeException = RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(function, cause))

    /** A value for a message: text quoted, numbers in their JS form. */
    fun quote(value: Any?): String = when (val v = unwrap(value)) {
        null -> "null"
        Undefined.instance -> "undefined"
        is CharSequence -> "\"$v\""
        is Number -> number(v.toDouble())
        else -> ScriptArgs.describe(value)
    }

    fun number(x: Double): String = RhinoScriptRuntime.numberToString(x, 10)

}
