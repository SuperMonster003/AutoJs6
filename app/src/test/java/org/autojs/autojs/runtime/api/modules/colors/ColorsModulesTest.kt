package org.autojs.autojs.runtime.api.modules.colors

import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.autojs.autojs.util.ColorUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Roadmap P2.3: `colors`, `colors.theme` and `Color` of the new script module layer beyond the differential test against
 * the obsolete JS: the Android ports of [ColorValues] (against the host's `ColorUtils` and the documented results), one
 * test per deviation fixed here (ROADMAP appendix E, "P2.3 new deviations"), the documented examples of the members the
 * obsolete JS did not have, the D10 error types, and the surface against the current Kotlin modules in the same JVM
 * (names, order, attributes, kinds, prototypes; `length` may differ, appendix C Q9). Cases that need Android classes
 * (Color instances, `toColorStateList`, `setPaintColor`, `extract`, `themeColor`, `colors.theme`) are only exported for the
 * device run, with their expected values.
 */
class ColorsModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        rhino.mountNewModules("util", "colors", "Color")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    /** Checks [cases] on the JVM and adds them to `build/script-modules-device/ColorsModulesTest.json` for the device run. */
    private fun check(cases: List<Pair<String, String>>) {
        val failures = cases.mapNotNull { (expression, expected) ->
            val actual = rhino.render(DifferentialRhino.Side.KT, expression)
            if (actual.key == expected) null else "$expression\n    expected $expected\n    actual   $actual"
        }
        assertTrue("Unexpected results:\n" + failures.joinToString("\n"), failures.isEmpty())
        exportForDevice(cases)
    }

    /** Hex codes and names read as the host's `ColorUtils.toHex` reads them (the new module only adds `#RGB` shortening). */
    @Test
    fun hexCodesFollowTheHostColorUtils() {
        val texts = listOf(
            "#CC5500", "#C50", "#c50", "#FFCC5500", "#DECC5500", "#80cc5500", "red", "RED", "burnt-orange", "BURNT_ORANGE",
            "dark_gray", "DarkGray", "orange", "pansy", "purple_300", "-65536", "+16711680", "4294901760", "0", "#00000000",
        )
        texts.forEach { text ->
            listOf("auto" to ColorValues.Alpha.AUTO, "keep" to ColorValues.Alpha.KEEP, "none" to ColorValues.Alpha.NONE).forEach { (mode, alpha) ->
                assertEquals("$text / $mode", ColorUtils.toHex(text, mode), ColorValues.hexOf(text, alpha))
            }
        }
        listOf(0, -1, -65536, 0x80FF0000.toInt(), 0x12345678, 0xFF000000.toInt(), 0x00FFFFFF).forEach { color ->
            assertEquals(ColorUtils.toHex(color.toLong()), ColorValues.hexOf(color, ColorValues.Alpha.AUTO))
            assertEquals(ColorUtils.toHex(color.toLong(), 8), ColorValues.hexOf(color, ColorValues.Alpha.KEEP))
            assertEquals(ColorUtils.toHex(color.toLong(), 6), ColorValues.hexOf(color, ColorValues.Alpha.NONE))
        }
        listOf("#GGG", "nope", "#1234", "FF0000", " #FF0000", "#FF0000 ", "").forEach { text ->
            assertEquals(text, null, ColorValues.hexOf(text, ColorValues.Alpha.AUTO))
            assertTrue(text, runCatching { ColorUtils.toHex(text) }.isFailure)
        }
        assertEquals("#96C", ColorValues.shortHexOf(0xAA9966CC.toInt()))
        assertEquals(null, ColorValues.shortHexOf(0xFFFAEBD7.toInt()))
    }

    /** `parse` without the hex code round trip gives the int of `hexOf` (fixed texts and random ones of hex code characters). */
    @Test
    fun parseGivesTheIntOfTheHexCode() {
        fun viaHexCode(text: String) = ColorValues.hexOf(text, ColorValues.Alpha.AUTO)?.let { hex ->
            hex.substring(1).toLong(16).let { if (hex.length == 7) (it or 0xFF000000L).toInt() else it.toInt() }
        }
        val texts = listOf(
            "#CC5500", "#C50", "#c50", "#FFCC5500", "#DECC5500", "#80cc5500", "#00000000", "#ffffffff", "#FF000000", "#abcdef",
            "red", "RED", "burnt-orange", "BURNT_ORANGE", "pansy", "-65536", "+16711680", "4294901760", "0", "99999999999999999999",
            "#GGG", "#g_z", "#a-b", "#1234", "#12", "#12345", "#1234567", "#123456789", "#", "+", "-", "", " #FF0000", "#FF0000 ",
            "#\uFF10\uFF11\uFF12", "#\u0663\u0663\u0663", "#\u0661\u0662\u0663\u0664\u0665\u0666", "#\uFF26\uFF26\uFF26", "#\u212A00", "\u0663",
        )
        val random = java.util.Random(31)
        val alphabet = "#0123456789abcdefABCDEFgG_-+ \uFF10\u0663"
        val fuzz = List(20000) { String(CharArray(random.nextInt(11)) { alphabet[random.nextInt(alphabet.length)] }) }
        (texts + fuzz + fuzz.map { "#$it" }).forEach { text -> assertEquals(text, viaHexCode(text), ColorValues.parse(text)) }
    }

    /** Skia's HSV conversions and androidx's HSL, luminance, contrast and blending, by known results. */
    @Test
    fun androidPortsGiveTheAndroidResults() {
        val hsv = FloatArray(3)
        ColorValues.rgbToHsv(102, 51, 153, hsv)
        assertEquals(listOf(270f, 2f / 3, 0.6f), hsv.toList())
        ColorValues.rgbToHsv(255, 128, 0, hsv)
        assertEquals(listOf(128f / 255 * 60, 1f, 1f), hsv.toList())
        assertEquals(0xFFFF0000.toInt(), ColorValues.hsvToColor(255, floatArrayOf(0f, 1f, 1f)))
        assertEquals(0xFF808080.toInt(), ColorValues.hsvToColor(255, floatArrayOf(123f, 0f, 0.5f)))
        assertEquals(0x80663399.toInt(), ColorValues.hsvToColor(0x80, floatArrayOf(270f, 2f / 3, 0.6f)))
        assertEquals(0xFFFF0000.toInt(), ColorValues.hsvToColor(255, floatArrayOf(540f, 1f, 1f)))
        val hsl = FloatArray(3)
        ColorValues.rgbToHsl(102, 51, 153, hsl)
        assertEquals(listOf(270f, 0.50000006f, 0.4f), hsl.toList())
        assertEquals(0xFF663399.toInt(), ColorValues.hslToColor(floatArrayOf(270f, 0.5f, 0.4f)))
        assertEquals(1.0, ColorValues.luminance(-1), 0.0)
        assertEquals(0.0, ColorValues.luminance(0xFF000000.toInt()), 0.0)
        assertEquals(21.0, ColorValues.contrast(0xFF000000.toInt(), -1), 1e-12)
        assertEquals(0xFF7F7F7F.toInt(), ColorValues.blend(0xFF000000.toInt(), -1, 0.5f))
        assertEquals(0xFFBF003F.toInt(), ColorValues.blend(0xFFFF0000.toInt(), 0xFF0000FF.toInt(), 0.25f))
        assertEquals(0xFF808080.toInt(), ColorValues.composite(0x80FFFFFF.toInt(), 0xFF000000.toInt()))
    }

    /** The documented examples (`api/color.md`) of the members the obsolete JS did not have. */
    @Test
    fun documentedExamples() = check(
        listOf(
            "colors.toHex(colors.oklab(0.628, 0.2249, 0.1258))" to "\"#FF0000\"",
            "colors.toHex(colors.oklab('50%', 0, 0))" to "\"#636363\"",
            "colors.toHex(colors.oklab(0.5, 0, 0, 0.5), 8)" to "\"#80636363\"",
            "colors.toHex(colors.oklab(0.7, 0.4, 0))" to "\"#FF5799\"",
            "colors.oklab([0.5, 0, 0]) === colors.oklab(0.5, 0, 0)" to "true",
            "colors.toHex(colors.oklch(0.7, 0.1, 200))" to "\"#40B1B7\"",
            "colors.toHex(colors.oklch('50%', 0.1, 270))" to "\"#4D5F9C\"",
            "colors.toHex(colors.oklch(0.7, 0.1, 200, 0.5), 8)" to "\"#8040B1B7\"",
            "colors.toHex(colors.oklch(0.7, 0.1, 200, 128), 8)" to "\"#8040B1B7\"",
            "colors.oklch(0.6, 0.1, -20) === colors.oklch(0.6, 0.1, 340)" to "true",
            "colors.toHex(colors.oklch(0.6, 0.4, 145))" to "\"#009B29\"",
            "colors.oklch([0.7, 0.1, 200]) === colors.oklch(0.7, 0.1, 200)" to "true",
            "colors.toOklab('white').map(x => Math.round(x * 1000) / 1000)" to "[1,0,0]",
            "colors.toOklab('red').map(x => Math.round(x * 10000) / 10000)" to "[0.628,0.2249,0.1258]",
            "colors.toOklch('red').map(x => Math.round(x * 10000) / 10000)" to "[0.628,0.2577,29.2339]",
            "colors.toOklch('#808080').map(x => Math.round(x * 10000) / 10000)" to "[0.5999,0,0]",
            "colors.toRgbString('#663399')" to "\"rgb(102, 51, 153)\"",
            "colors.toRgbString(102, 51, 153)" to "\"rgb(102, 51, 153)\"",
            "colors.toRgbaString('#80663399')" to "\"rgba(102, 51, 153, 0.5)\"",
            "colors.toRgbaString('#663399')" to "\"rgba(102, 51, 153, 1.0)\"",
            "colors.toRgbaString('#663399', false)" to "\"rgba(102, 51, 153, 1)\"",
            "colors.toRgbaString('#663399', { keepTrailingZeroForFullAlpha: false })" to "\"rgba(102, 51, 153, 1)\"",
            "colors.toArgbString('#80663399')" to "\"argb(0.5, 102, 51, 153)\"",
            "colors.toHsvString('#663399')" to "\"hsv(270, 66.7%, 60%)\"",
            "colors.toHsvString(102, 51, 153)" to "\"hsv(270, 66.7%, 60%)\"",
            "colors.toHsvaString('#80663399')" to "\"hsva(270, 66.7%, 60%, 0.5)\"",
            "colors.toHslString('#663399')" to "\"hsl(270, 50%, 40%)\"",
            "colors.toHslString(102, 51, 153)" to "\"hsl(270, 50%, 40%)\"",
            "colors.toHslaString('#80663399')" to "\"hsla(270, 50%, 40%, 0.5)\"",
            "colors.distance('red', 'red')" to "0",
            "Math.round(colors.distance('orange', 'dark-orange') * 100) / 100" to "8.33",
            "colors.distance('orange', 'dark-orange') <= 9 === colors.isSimilar('orange', 'dark-orange', 9)" to "true",
            "colors.distance('orange', 'dark-orange', { algorithm: 'hs' }) === colors.distance('orange', 'dark-orange', 'hs')" to "true",
            "colors.toHex(colors.invert('#FF0000'))" to "\"#00FFFF\"",
            "colors.toHex(colors.invert('#80FF0000'), 8)" to "\"#8000FFFF\"",
            "colors.toHex(colors.blend('black', 'white'))" to "\"#7F7F7F\"",
            "colors.toHex(colors.blend('black', 'white', 0))" to "\"#000000\"",
            "colors.toHex(colors.blend('black', 'white', '100%'))" to "\"#FFFFFF\"",
            "colors.toHex(colors.blend('red', 'blue', 0.25))" to "\"#BF003F\"",
            "colors.contrast('black', 'white')" to "21",
            "colors.contrast('white', 'white')" to "1",
            "colors.contrast('#777777', 'white') >= 4.5" to "false",
            "Math.round(colors.contrast('#FFC107', 'white') * 100) / 100" to "1.63",
            "colors.toHex(colors.ensureContrast('#FFC107', 'white'))" to "\"#977000\"",
            "colors.toHex(colors.ensureContrast('#FFC107', 'white', 3))" to "\"#BC8D00\"",
            "colors.toHex(colors.ensureContrast('#2196F3', '#121212'))" to "\"#2196F3\"",
            "colors.toHex(colors.ensureContrast('#80FFC107', 'white'), 8)" to "\"#80977000\"",
            "colors.toHex(colors.ensureContrast('#777777', 'white', 21))" to "\"#000000\"",
            "colors.harmony('#2196F3', 'complementary').map(c => colors.toHex(c))" to "[\"#2196F3\",\"#C97E00\"]",
            "colors.harmony('#2196F3', 'triadic').map(c => colors.toHex(c))" to "[\"#2196F3\",\"#E45D7C\",\"#71A412\"]",
            "colors.harmony('#808080', 'triadic').map(c => colors.toHex(c))" to "[\"#808080\",\"#808080\",\"#808080\"]",
            "colors.tones('#2196F3').map(c => colors.toHex(c))" to "[\"#EEF6FF\",\"#D1E8FF\",\"#A6D3FF\",\"#73BAFF\",\"#3DA0F7\",\"#0F8AE4\",\"#0075C5\",\"#0060A4\",\"#004A7F\",\"#00345D\"]",
            "colors.tones('#2196F3', 3).map(c => colors.toHex(c))" to "[\"#EEF6FF\",\"#1C92EE\",\"#00345D\"]",
            "(function () { var d = colors.derive('#2196F3'); return [colors.toHex(d.primary), colors.toHex(d.accent), colors.toHex(d.dark)]; })()" to "[\"#2196F3\",\"#0095F9\",\"#007DD2\"]",
            "colors.toHex(colors.nightVariant('#E0F2F1'))" to "\"#196D6B\"",
            "colors.toHex(colors.nightVariant('#2196F3'))" to "\"#004E86\"",
            "colors.toHex(colors.nightVariant('#1A237E'))" to "\"#1A237E\"",
            "colors.toHex(colors.simulate('#FF0000', 'protanopia'))" to "\"#6D5F00\"",
            "colors.toHex(colors.simulate('#FF0000', 'deuteranopia'))" to "\"#A39000\"",
            "colors.toHex(colors.simulate('#80FF0000', 'tritanopia'), 8).slice(0, 3)" to "\"#80\"",
            "colors.summary('#009688')" to "\"hex(#009688), rgba(0,150,136/1.0), int(-16738680)\"",
            "colors.summary('#BE009688')" to "\"hex(#BE009688), rgba(0,150,136/0.75), int(-1107257720)\"",
            "colors.summary('red')" to "\"hex(#FF0000), rgba(255,0,0/1.0), int(-65536)\"",
            "colors.summary('#6400008B')" to "\"hex(#6400008B), rgba(0,0,139/0.39), int(1677721739)\"",
            "colors.summary('#00FFFFFF')" to "\"hex(#00FFFFFF), rgba(255,255,255/0.0), int(16777215)\"",
            "colors.toHex('#AAC0C0C0', 'auto')" to "\"#AAC0C0C0\"",
            "colors.toHex('#C0C0C0', 'keep')" to "\"#FFC0C0C0\"",
            "colors.toHex('#AAC0C0C0', 'none')" to "\"#C0C0C0\"",
            "colors.toHex('#AA9966CC', 3)" to "\"#96C\"",
            "colors.isEqual('dark-gray', 'DARK_GRAY')" to "true",
            "colors.alpha('#05060708', { max: 1 })" to "0.0196078431372549",
            "colors.luminance(colors.RED)" to "0.2126",
            "colors.luminance(colors.GREEN)" to "0.7152",
            "colors.luminance(colors.BLUE)" to "0.0722",
            "colors.luminance(colors.YELLOW)" to "0.9278",
            "colors.isSimilar('#010101', '#020202', { similarity: 0.95 })" to "true",
            "colors.all.RED === colors.android.RED" to "true",
            "colors.all.ORANGE_300 === colors.material.ORANGE_300" to "true",
            "Object.keys(colors).indexOf('all') < 0 && 'all' in colors" to "true",
        ),
    )

    /** Deviations of the current Kotlin module fixed here (ROADMAP appendix E, "P2.3 new deviations"). */
    @Test
    fun fixedDeviations() = check(
        listOf(
            // The *Double functions and the alpha of toHsva / toHsla divide by 255 (a channel of 1 was taken as 100%).
            "colors.alphaDouble('#01000000')" to "0.00392156862745098",
            "colors.redDouble('#010000')" to "0.00392156862745098",
            "colors.alpha('#01000000', { max: 1 })" to "0.00392156862745098",
            "colors.toHsva('#01663399')[3]" to "0.00392156862745098",
            "colors.toHsla('#01663399')[3]" to "0.00392156862745098",
            "colors.toRgbaString('#01FF0000')" to "\"rgba(255, 0, 0, 0)\"",
            // Setters keep the other channels as integers (channels of 0 / 1 were read as fractions again).
            "colors.toHex(colors.setAlpha('#010000', 0.5), 8)" to "\"#80010000\"",
            "colors.toHex(colors.setRed('#000001', 1))" to "\"#010001\"",
            "colors.toHex(colors.setGreen('#010001', 2))" to "\"#010201\"",
            "colors.toHex(colors.setBlue('#010100', 0.5))" to "\"#010180\"",
            "colors.toHex(colors.setRedRelative('#010000', 0.5))" to "\"#010000\"",
            "colors.toHex(colors.setRedRelative('#020000', 0.25))" to "\"#010000\"",
            "colors.toHex(colors.setAlphaRelative('#02FF0000', 2), 8)" to "\"#04FF0000\"",
            "colors.toHex(colors.hsla(0, 1, 0.002, 1), 8)" to "\"#FF010000\"",
            "colors.toHex(colors.hsla(0, 1, 0.5, 0.004), 8)" to "\"#01FF0000\"",
            "colors.toHslString('#010000')" to "\"hsl(0, 100%, 0.2%)\"",
            "colors.toHsvString('#010000')" to "\"hsv(0, 100%, 0.4%)\"",
            "colors.toHsla('#010000').slice(0, 3)" to "[0,1,0.0019607844296842813]",
            // Alpha components accept text such as '25%' (documented ColorComponent).
            "colors.hsva('25%', '80%', '64%', '25%') === colors.hsva(90, 0.8, 0.64, 0.25)" to "true",
            "colors.toHex(colors.argb('25%', 255, 0, 0), 8)" to "\"#40FF0000\"",
            "colors.toHex(colors.rgba(255, 0, 0, '50%'), 8)" to "\"#80FF0000\"",
            "colors.toHex(colors.hsla(0, 1, 0.5, '100%'), 8)" to "\"#FFFF0000\"",
            // rgba('#RRGGBB') and rgba('#RGB') are opaque; concatenated text is read like a literal.
            "colors.toHex(colors.rgba('#663399'), 8)" to "\"#FF663399\"",
            "colors.toHex(colors.rgba('#F00'), 8)" to "\"#FFFF0000\"",
            "colors.toHex(colors.rgba('#663399' + 'FF'), 8)" to "\"#FF663399\"",
            // toHex(color, 3) shortens any color.
            "colors.toHex('red', 3)" to "\"#F00\"",
            "colors.toHex('#f00', 3)" to "\"#F00\"",
            "colors.toHex('#aa9966cc', 3)" to "\"#96C\"",
            // A Java float[] result container is unwrapped.
            "(function () { var r = util.java.array('float', 3); colors.toHsv('#663399', r); return Array.from(r).map(x => Math.round(x * 100) / 100); })()" to "[270,0.67,0.6]",
            "(function () { var r = util.java.array('float', 4); colors.toHsva('#80663399', r); return Math.round(r[3] * 100) / 100; })()" to "0.5",
            "(function () { var r = util.java.array('float', 3); colors.toHsl(102, 51, 153, r); return Array.from(r); })()" to "[270,0.5000000596046448,0.4000000059604645]",
            "(function () { var r = util.java.array('float', 5); return colors.toHsv('#663399', r).length; })()" to "5",
            // String(colors) no longer exposes the Java object of the module.
            "String(colors)" to "\"[object Object]\"",
            "colors.toString('#663399')" to "\"#663399\"",
        ),
    )

    /** D10: wrong types are TypeErrors, values out of the accepted set RangeErrors; extra arguments are TypeErrors (T4). */
    @Test
    fun argumentErrors() = check(
        listOf(
            "colors.toInt({})" to "throws TypeError",
            "colors.toInt(true)" to "throws TypeError",
            "colors.toInt(null)" to "throws TypeError",
            "colors.toInt()" to "throws TypeError",
            "colors.toInt('nope')" to "throws RangeError",
            "colors.toInt('#1234')" to "throws RangeError",
            "colors.toInt('red', 1)" to "throws TypeError",
            "colors.toHex('red', 7)" to "throws RangeError",
            "colors.toHex('red', 'full')" to "throws RangeError",
            "colors.toHex('red', {})" to "throws TypeError",
            "colors.toHex('#FAEBD7', 3)" to "throws RangeError",
            "colors.alpha('#fff', { max: 3 })" to "throws RangeError",
            "colors.alpha('#fff', { max: 'x' })" to "throws TypeError",
            "colors.toRgba('#fff', { maxAlpha: 2 })" to "throws RangeError",
            "colors.rgb(-1, 0, 0)" to "throws RangeError",
            "colors.rgb('x', 0, 0)" to "throws TypeError",
            "colors.rgb(1, 2)" to "throws TypeError",
            "colors.rgb([1, 2])" to "throws TypeError",
            "colors.rgba(1, 2)" to "throws TypeError",
            "colors.hsv(1, 2)" to "throws TypeError",
            "colors.hsv(90, 120, 50)" to "throws RangeError",
            "colors.hsv('x', 1, 1)" to "throws TypeError",
            "colors.toHsv('#fff', [0, 0, 0])" to "throws TypeError",
            "colors.toHsv('#fff', util.java.array('float', 2))" to "throws RangeError",
            "colors.toHsla(1, 2, 3, 300)" to "throws RangeError",
            "colors.toHsla(1, 2, 3, '50%')" to "throws TypeError",
            "colors.setRed('#fff', -1)" to "throws RangeError",
            "colors.setRedRelative('#fff', -1)" to "throws RangeError",
            "colors.isEqual('red', 'red', 1)" to "throws TypeError",
            "colors.isSimilar('red', 'red', 4, 'fast')" to "throws RangeError",
            "colors.isSimilar('red', 'red', { threshold: 4, similarity: 0.9 })" to "throws TypeError",
            "colors.isSimilar('red', 'red', { threshold: 'x' })" to "throws TypeError",
            "colors.distance('red', 'red', 'fast')" to "throws RangeError",
            "colors.blend('red', 'blue', 2)" to "throws RangeError",
            "colors.blend('red', 'blue', 'x')" to "throws TypeError",
            "colors.contrast('red', '#80FFFFFF')" to "throws RangeError",
            "colors.ensureContrast('red', '#80FFFFFF')" to "throws RangeError",
            "colors.ensureContrast('red', 'white', 22)" to "throws RangeError",
            "colors.harmony('red', 'x')" to "throws RangeError",
            "colors.harmony('red')" to "throws TypeError",
            "colors.tones('red', 1)" to "throws RangeError",
            "colors.tones('red', 2.5)" to "throws RangeError",
            "colors.simulate('red', 'x')" to "throws RangeError",
            "colors.oklab(2, 0, 0)" to "throws RangeError",
            "colors.oklab(0.5, 'x', 0)" to "throws TypeError",
            "colors.oklab([0.5, 0])" to "throws TypeError",
            "colors.oklab(0.5, 0)" to "throws TypeError",
            "colors.oklch(0.5, -1, 0)" to "throws RangeError",
            "colors.oklch(0.5, Infinity, 0)" to "throws RangeError",
            "colors.extract(1)" to "throws TypeError",
            "colors.toColorStateList()" to "throws TypeError",
            "colors.setPaintColor({}, 'red')" to "throws TypeError",
            "colors.summary('red', 1)" to "throws TypeError",
            "colors.theme.set('red', 1)" to "throws TypeError",
            "colors.theme.get(1)" to "throws TypeError",
            "Color(1, 2)" to "throws TypeError",
            "new Color(1, 2, 3, 4, 5)" to "throws TypeError",
            "Color({})" to "throws TypeError",
        ),
    )

    /** Module objects and the function lengths taken from the signatures (appendix C Q9). */
    @Test
    fun moduleObjects() = check(
        listOf(
            "typeof colors" to "\"object\"",
            "typeof Color" to "\"function\"",
            "typeof colors.theme" to "\"object\"",
            "[colors.toInt.length, colors.toHex.length, colors.rgb.length, colors.hsva.length, colors.isSimilar.length, colors.build.length]" to "[1,1,1,1,2,0]",
            "Color.RED === colors.RED && typeof Color.call === 'function'" to "true",
            "Color.prototype.constructor === Color" to "true",
            "colors.hasOwnProperty('RED')" to "false",
            "'parseColor' in colors && colors.RED === -65536 && Color.RED === -65536" to "true",
            "colors.__augmented_custom_to_string__ !== undefined && !Object.keys(colors).includes('__augmented_custom_to_string__')" to "true",
        ),
    )

    /** Members, order, attributes and kinds against the current Kotlin modules in the same JVM (length aside). */
    @Test
    fun surfaceMatchesTheCurrentModules() {
        val actual = rhino.evalKt(DESCRIBE).toString()
        val expected = ScriptModuleExpectations.obsolete("describe")
        assertEquals(expected.replace(';', '\n'), actual.replace(';', '\n'))
    }

    /** Cases that need Android classes, exported for the device run with their expected values. */
    @Test
    fun deviceCases() {
        exportForDevice(
            listOf(
                "Color('#f00').toInt()" to "-65536",
                "new Color('#f00').toHex()" to "\"#FF0000\"",
                "Color(255, 0, 0, 128).toHex()" to "\"#80FF0000\"",
                "Color(1, 0.5, '3.53%').toHex()" to "\"#FF8009\"",
                "Color().toInt()" to "-16777216",
                "Color(Color('#00FF00')).toInt()" to "-16711936",
                "typeof Color('red')" to "\"object\"",
                "colors.build('dark-orange').setAlpha(0.85).removeBlue().toHex()" to "\"#D9FF8C00\"",
                "colors.build().setHsla(0.25, 0.8, 0.64, 0.9).toHex()" to "\"#E6A3ED5A\"",
                "colors.build(120, 60, 240).setAlpha(0.85).toHex()" to "\"#D9783CF0\"",
                "colors.build(120, 60, 240, 0.85).toHex()" to "\"#D9783CF0\"",
                "colors.toInt(Color('#f00'))" to "-65536",
                "colors.isEqual(Color('red'), 'red')" to "true",
                "colors.toHex(Color('#80FF0000'), 8)" to "\"#80FF0000\"",
                "colors.toColorStateList('red').getDefaultColor()" to "-65536",
                "colors.toColorStateList('red', 'green').getColors().length" to "2",
                "(function () { var p = new android.graphics.Paint(); colors.setPaintColor(p, '#80FF0000'); return p.getColor(); })()" to "-2130771968",
                "(function () { var p = new android.graphics.Paint(); colors.setPaintColor(p, Color('blue')); return p.getColor(); })()" to "-16776961",
                "typeof colors.themeColor" to "\"object\"",
                "colors.toInt(colors.themeColor) === colors.themeColor.getColorPrimary()" to "true",
                "typeof colors.theme.get()" to "\"number\"",
                "colors.theme.libraries().length > 0" to "true",
                "typeof colors.theme.current().color" to "\"number\"",
                "colors.theme.state()" to "throws TypeError",
                "colors.toHex(colors.parseColor('#663399'))" to "\"#663399\"",
                "colors.theme.activate(-123456)" to "throws RangeError",
                "(function () { var img = images.fromBase64(SMALL_PNG); try { return colors.extract(img, { count: 1 }).length; } finally { img.recycle(); } })()".replace("SMALL_PNG", "'$SMALL_PNG'") to "1",
                "(function () { var img = images.fromBase64(SMALL_PNG); try { return colors.toHex(colors.extract(img, { count: 1 })[0]); } finally { img.recycle(); } })()".replace("SMALL_PNG", "'$SMALL_PNG'") to "\"#FF0000\"",
                "colors.extract(images.fromBase64('$SMALL_PNG'), { count: 0 })" to "throws RangeError",
            ),
        )
    }

    companion object {

        /** A 2 x 2 opaque red PNG (RGB, 8 bits per channel). */
        private const val SMALL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR42mP4z8AARAwQCgAf7gP9Y167WwAAAABJRU5ErkJggg=="

        /** Every exported case so far (JUnit creates an instance per test method). */
        private val exported = LinkedHashMap<String, String>()

        @Synchronized
        private fun exportForDevice(cases: List<Pair<String, String>>) {
            cases.forEach { (expression, expected) -> exported[expression] = expected }
            val dir = File("build/script-modules-device").apply { mkdirs() }
            val entries = exported.entries.joinToString(",\n") { (expression, expected) ->
                "  {\"expression\": ${json(expression)}, \"js\": null, \"kt\": ${json(expected)}, \"reason\": \"new script module layer (P2.3)\"}"
            }
            File(dir, "ColorsModulesTest.json").writeText("[\n$entries\n]\n", Charsets.UTF_8)
        }

        private fun json(value: String): String = buildString {
            append('"')
            value.forEach { c ->
                if (c == '"' || c == BACKSLASH) append(BACKSLASH)
                append(c)
            }
            append('"')
        }

        private const val BACKSLASH = '\\'

        /** Names, attributes and kinds of the module objects; values of getters that need Android are not read. */
        private val DESCRIBE = """
            (function () {
                var out = [];
                function attrs(d) { return (d.enumerable ? 'E' : '-') + (d.writable ? 'W' : '-') + (d.configurable ? 'C' : '-') + (d.get || d.set ? 'A' : ''); }
                function kind(v) {
                    if (typeof v === 'function') return 'function ' + v.name;
                    if (v === null) return 'null';
                    if (typeof v === 'object') {
                        var cls = Object.prototype.toString.call(v);
                        return cls === '[object JavaObject]' ? 'java ' + String(v.getClass().getName()) : 'object';
                    }
                    return typeof v + ' ' + String(v);
                }
                function walk(label, o, skip, only) {
                    (only || Object.getOwnPropertyNames(o)).forEach(function (k) {
                        if (['length', 'arity', 'name'].indexOf(k) >= 0) return;
                        if (skip && skip.indexOf(k) >= 0) { out.push(label + '.' + k + ' ' + (o.propertyIsEnumerable(k) ? 'E' : '-') + ' (not read)'); return; }
                        var d = Object.getOwnPropertyDescriptor(o, k);
                        out.push(label + '.' + k + ' ' + attrs(d) + ' ' + kind(d.value));
                    });
                }
                function chain(label, o) {
                    var names = [];
                    for (var p = Object.getPrototypeOf(o); p; p = Object.getPrototypeOf(p)) {
                        var cls = Object.prototype.toString.call(p);
                        names.push(cls === '[object JavaObject]' ? String(p.getClass().getName()) : p === Object.prototype ? 'Object.prototype' : p === Function.prototype ? 'Function.prototype' : cls);
                    }
                    out.push(label + ' proto ' + names.join(' > '));
                }
                out.push('keys ' + Object.keys(globalThis).filter(function (k) { return ['colors', '${'$'}colors', 'Color'].indexOf(k) >= 0; }).join());
                walk('global', globalThis, null, ['colors', '${'$'}colors', 'Color']);
                walk('colors', colors, ['themeColor']);
                out.push('colors keys ' + Object.keys(colors).join());
                chain('colors', colors);
                walk('colors.theme', colors.theme, null);
                chain('colors.theme', colors.theme);
                walk('Color', Color, null);
                chain('Color', Color);
                walk('Color.prototype', Color.prototype, null);
                return out.join(';');
            })()
        """.trimIndent()

    }

}
