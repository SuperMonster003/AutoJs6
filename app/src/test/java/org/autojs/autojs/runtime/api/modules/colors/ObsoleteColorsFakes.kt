package org.autojs.autojs.runtime.api.modules.colors

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.util.ColorUtils
import org.mozilla.javascript.Context

/**
 * What the obsolete `__colors__.js` needs from Android, for the JS side of a [DifferentialRhino] only (roadmap P2.3).
 *
 * The obsolete module called the host's `org.autojs.autojs.util.ColorUtils` (hex codes and names), `android.graphics.Color`
 * (`HSVToColor`), androidx `ColorUtils` (`HSLToColor`, `RGBToHSL`) and the runtime's `core.image.Colors` (`rgb`, `argb`,
 * `RGBToHSV`, `luminance`). On the plain JVM those are not mocked, so [install] defines JS stand-ins in the JS scope: hex codes
 * and names go through the real `ColorUtils.toHex` overloads (pure Kotlin), with `Color.parseColor` replaced by reading the
 * hex digits; HSV, HSL and luminance come from the ports in [ColorValues] that the new module uses, so both sides share
 * the Android arithmetic and a difference can only come from the module code. The new module itself needs none of this.
 *
 * zh-CN: 旧 `__colors__.js` 所需的 Android 能力, 只用于差分测试的 JS 侧 (P2.3). 颜色代码与名称经真实的 ColorUtils.toHex 重载 (纯 Kotlin), Color.parseColor
 * 改为直接读十六进制; HSV, HSL 与亮度使用新模块所用的 [ColorValues] 移植, 两侧共用同一套 Android 运算, 差异只可能来自模块代码. 新模块本身不需要这些.
 */
internal object ObsoleteColorsFakes {

    /** Loads the obsolete util, Numberx and colors modules into the JS side of [rhino], with the stand-ins in place. */
    fun install(rhino: DifferentialRhino): ObsoleteModuleHost {
        val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
        val fakes = "Packages.${ObsoleteColorsFakes::class.java.name}"
        rhino.evalJs(
            """
            (function () {
                var F = $fakes;
                var packages = Packages.android;
                global.android = {
                    os: packages.os,
                    graphics: {
                        Color: {
                            HSVToColor: function (a, hsv) {
                                if (arguments.length === 1) { hsv = a; a = 255; }
                                return F.hsvToColor(a, hsv[0], hsv[1], hsv[2]);
                            },
                        },
                    },
                    content: { res: { ColorStateList: { valueOf: function () { throw Error('ColorStateList is not available on the JVM'); } } } },
                };
                global.androidx = {
                    core: {
                        graphics: {
                            ColorUtils: {
                                HSLToColor: function (hsl) { return F.hslToColor(hsl[0], hsl[1], hsl[2]); },
                                RGBToHSL: function (r, g, b, out) { F.rgbToHsl(r, g, b, out); },
                            },
                        },
                    },
                };
                global.ColorUtils = {
                    toInt: function (color) { return F.toInt(color); },
                    toHex: function (color, alphaOrLength) { return arguments.length < 2 ? F.toHex(color, null) : F.toHex(color, alphaOrLength); },
                    toFullHex: function (color) { return F.toHex(color, 8); },
                };
            })();
            """.trimIndent(),
        )
        host.setRuntimeProperty(
            "colors",
            rhino.evalJs(
                """
                (function (F) {
                    return {
                        rgb: function (r, g, b) { return F.rgb(r, g, b); },
                        argb: function (a, r, g, b) { return F.argb(a, r, g, b); },
                        RGBToHSV: function (r, g, b, out) { F.rgbToHsv(r, g, b, out); },
                        luminance: function (color) { return F.luminance(color); },
                    };
                })($fakes)
                """.trimIndent(),
            ),
        )
        host.loadAndDefine("util")
        host.loadAndDefine("Numberx")
        host.loadAndDefine("colors")
        return host
    }

    // org.autojs.autojs.util.ColorUtils as the Java bridge picked its overloads (number, string or ThemeColor color;
    // length number, boolean or alpha text), Color.parseColor replaced by reading the hex digits.

    @JvmStatic
    fun toHex(color: Any?, alphaOrLength: Any?): String = when (color) {
        is Number -> when (alphaOrLength) {
            null -> ColorUtils.toHex(color)
            is Boolean -> ColorUtils.toHex(color, alphaOrLength)
            is Number -> ColorUtils.toHex(color, alphaOrLength.toInt())
            else -> ColorUtils.toHex(color, Context.toString(alphaOrLength))
        }
        is ThemeColor -> toHex(color.colorPrimary, alphaOrLength)
        else -> {
            val text = Context.toString(color)
            when (alphaOrLength) {
                null -> ColorUtils.toHex(text)
                is Boolean -> ColorUtils.toHex(text, alphaOrLength)
                is Number -> ColorUtils.toHex(text, alphaOrLength.toInt())
                else -> ColorUtils.toHex(text, Context.toString(alphaOrLength))
            }
        }
    }

    @JvmStatic
    fun toInt(color: Any?): Int = parseColor(toHex(color, null))

    private fun parseColor(hex: String): Int {
        val value = hex.substring(1).toLong(16)
        return when (hex.length) {
            7 -> (value or 0xFF000000L).toInt()
            9 -> value.toInt()
            else -> throw IllegalArgumentException("Unknown color")
        }
    }

    @JvmStatic
    fun rgb(r: Int, g: Int, b: Int): Int = ColorValues.rgb(r, g, b)

    @JvmStatic
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = ColorValues.argb(a, r, g, b)

    @JvmStatic
    fun hsvToColor(alpha: Int, h: Double, s: Double, v: Double): Int = ColorValues.hsvToColor(alpha, floatArrayOf(h.toFloat(), s.toFloat(), v.toFloat()))

    @JvmStatic
    fun rgbToHsv(r: Int, g: Int, b: Int, out: Any?) = ColorValues.rgbToHsv(r, g, b, out as FloatArray)

    @JvmStatic
    fun hslToColor(h: Double, s: Double, l: Double): Int = ColorValues.hslToColor(floatArrayOf(h.toFloat(), s.toFloat(), l.toFloat()))

    @JvmStatic
    fun rgbToHsl(r: Int, g: Int, b: Int, out: Any?) = ColorValues.rgbToHsl(r, g, b, out as FloatArray)

    @JvmStatic
    fun luminance(color: Int): Double = ColorValues.luminance(color)

}
