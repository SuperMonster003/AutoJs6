package org.autojs.autojs.runtime.api.modules.colors

import org.autojs.autojs.runtime.api.augment.differential.DifferentialCases
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Obsolete `__colors__.js` against `colors` of the new script module layer (roadmap P2.3 / P2.4), mounted the way
 * `ScriptRuntime.augment()` does with the switch on. The obsolete module runs on the JVM with the stand-ins of
 * [ObsoleteColorsFakes] (JS side only). Error types are compared (D10); the differences left are the ones ROADMAP appendix E
 * ("P2.3 new deviations") keeps or fixes on purpose. Members the obsolete module did not have (6.6.0 and later) and the
 * functions that need Android classes (`toColorStateList`, `setPaintColor`, Color instances) are covered by
 * [ColorsModulesTest] and the device cases.
 */
class ColorsModuleDifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsColors: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        if (ScriptModuleExpectations.RECORDING) {
            ObsoleteColorsFakes.install(rhino)
            jsColors = rhino.evalJs("colors")!!
        }
        rhino.mountNewModules("util", "colors")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val ktColors = rhino.evalKt("colors")
        val report = SurfaceDiff.compare(ScriptModuleExpectations.obsoleteSurface("surface") { SurfaceDiff.surface(jsColors, rhino.jsScope.globalThis) }, SurfaceDiff.surface(ktColors, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        // The color table constants sit on the Java prototypes as static fields, which a NativeJavaObject reads but does not
        // list: compare those by value instead.
        // zh-CN: 颜色表常量是 Java 原型上的静态字段, NativeJavaObject 能读取但不列出, 这些按值比较.
        val missing = (report.onlyInFirst - KNOWN_MISSING).filterNot { name ->
            val js = ScriptModuleExpectations.obsolete("js: colors['$name']") { rhino.render(DifferentialRhino.Side.JS, "colors['$name']").key }
            js != "undefined" && js == rhino.render(DifferentialRhino.Side.KT, "colors['$name']").key
        }
        assertTrue("Obsolete colors members missing from the new module: $missing", missing.isEmpty())
        val mismatches = report.typeMismatches - KNOWN_TYPE_MISMATCHES
        assertTrue("typeof differs: $mismatches", mismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {

        private val KNOWN_MISSING = emptySet<String>()

        /**
         * The color tables: the obsolete module exposed the Java classes (`ColorTable.Android` read through Packages, typeof
         * "function"), the current Kotlin module and the new layer the Kotlin objects (typeof "object"); the constants read the
         * same (ROADMAP appendix E, "P2.3 new deviations": keep the Kotlin behavior).
         */
        private val KNOWN_TYPE_MISMATCHES = setOf("android", "css", "web", "material")

        private val CONVERSIONS = listOf(
            "colors.toInt('#CC5500')",
            "colors.toInt('#C50')",
            "colors.toInt('#FFCC5500')",
            "colors.toInt('#80cc5500')",
            "colors.toInt(0xFFCC5500)",
            "colors.toInt(-3386112)",
            "colors.toInt(0x80FF0000)",
            "colors.toInt('BURNT_ORANGE')",
            "colors.toInt('burnt-orange')",
            "colors.toInt('red')",
            "colors.toInt('dark_gray')",
            "colors.toInt('-65536')",
            "colors.toInt('4294901760')",
            "colors.toInt('+16711680')",
            "colors.toInt('-')",
            "colors.toInt('Burnt_Orange-')",
            "colors.toInt(1.7)",
            "colors.toInt(colors.web.BURNT_ORANGE)",
            "colors.toInt(colors.RED)",
            "colors.toInt('#' + 'FF0000')",
            "colors.toInt('nope')",
            "colors.toInt('#GGGGGG')",
            "colors.toInt('#1234')",
            "colors.toInt({})",
            "colors.toInt(true)",
            "colors.toInt(null)",
            "colors.toInt()",
            "colors.toHex('#CC5500')",
            "colors.toHex('#C50')",
            "colors.toHex('#DECC5500')",
            "colors.toHex('#FFCC5500')",
            "colors.toHex(0xFFCC5500)",
            "colors.toHex('BURNT_ORANGE')",
            "colors.toHex('#AAC0C0C0', 'auto')",
            "colors.toHex('#FFC0C0C0', 'auto')",
            "colors.toHex('#AAC0C0C0', false)",
            "colors.toHex('#AAC0C0C0', 'none')",
            "colors.toHex('#FFC0C0C0', true)",
            "colors.toHex('#C0C0C0', 'keep')",
            "colors.toHex('#AA9966CC', 8)",
            "colors.toHex('#9966CC', 8)",
            "colors.toHex('#AA9966CC', 6)",
            "colors.toHex('#AA9966CC', 3)",
            "colors.toHex('#FF9966CC', 3)",
            "colors.toHex('#9966CC', 3)",
            "colors.toHex('#FAEBD7', 3)",
            "colors.toHex(0xFF9966CC, 3)",
            "colors.toHex('#AA9966CC', 7)",
            "colors.toHex('#AA9966CC', 'whatever')",
            "colors.toHex(-1, 8)",
            "colors.toHex(0)",
            "colors.toHex(0, 6)",
            "colors.toFullHex('#CC5500')",
            "colors.toFullHex(0x80FF0000)",
            "colors.toFullHex('red')",
            "colors.toString('#CC5500')",
            "colors.toString(0x80FF0000, 6)",
            "colors.toString(0xFFFF0000, true)",
        )

        private val COMPONENTS = listOf(
            "colors.alpha('#663399')",
            "colors.alpha(colors.TRANSPARENT)",
            "colors.alpha('#05060708')",
            "colors.alpha('#663399', { max: 1 })",
            "colors.alpha('#05060708', { max: 1 })",
            "colors.alpha('#05060708', { max: 255 })",
            "colors.alpha('#05060708', { max: 3 })",
            "colors.alpha('#05060708', {})",
            "colors.alpha('#05060708', null)",
            "colors.getAlpha('#05060708')",
            "colors.alphaDouble('#663399')",
            "colors.alphaDouble('#05060708')",
            "colors.alphaDouble('#80060708')",
            "colors.getAlphaDouble('#80060708')",
            "colors.red('#663399')",
            "colors.red('#663399', { max: 1 })",
            "colors.red('#05060708')",
            "colors.redDouble('#663399')",
            "colors.getRed('#663399')",
            "colors.getRedDouble('#663399')",
            "colors.green('#663399')",
            "colors.green('#663399', { max: 1 })",
            "colors.greenDouble('#663399')",
            "colors.getGreen('#663399')",
            "colors.blue('#663399')",
            "colors.blue('#663399', { max: 1 })",
            "colors.blueDouble('#663399')",
            "colors.getBlue('#663399')",
            "colors.toHex(colors.setAlpha('#663399', 0x80), 8)",
            "colors.toHex(colors.setAlpha('#663399', 0.5), 8)",
            "colors.toHex(colors.setAlpha('#663399', 255), 8)",
            "colors.toHex(colors.setAlpha('#663399', 1), 8)",
            "colors.toHex(colors.setAlpha('#663399', 0), 8)",
            "colors.toHex(colors.removeAlpha('#BE663399'), 8)",
            "colors.toHex(colors.removeAlpha('#CC5500'), 8)",
            "colors.toHex(colors.setRed('#663399', 0x80))",
            "colors.toHex(colors.setRed('#663399', 0.5))",
            "colors.toHex(colors.setRed('#663399', 255))",
            "colors.toHex(colors.setRed('#663399', 1))",
            "colors.toHex(colors.removeRed('#BE663399'), 8)",
            "colors.toHex(colors.setGreen('#663399', 0x80))",
            "colors.toHex(colors.setGreen('#663399', 1))",
            "colors.toHex(colors.removeGreen('#CC5500'))",
            "colors.toHex(colors.setBlue('#663399', 0x80))",
            "colors.toHex(colors.setBlue('#663399', 1))",
            "colors.toHex(colors.removeBlue('#BE663399'), 8)",
            "colors.toHex(colors.setAlphaRelative('#50FF0000', 0.5), 8)",
            "colors.toHex(colors.setAlphaRelative('#50FF0000', '50%'), 8)",
            "colors.toHex(colors.setAlphaRelative('#50FF0000', 1.5), 8)",
            "colors.toHex(colors.setAlphaRelative('#50FF0000', 10), 8)",
            "colors.toHex(colors.setAlphaRelative('#00FF0000', 10), 8)",
            "colors.toHex(colors.setRedRelative('#503399', 0.5))",
            "colors.toHex(colors.setRedRelative('#503399', '150%'))",
            "colors.toHex(colors.setRedRelative('#503399', 10))",
            "colors.toHex(colors.setGreenRelative('#335099', 0.5))",
            "colors.toHex(colors.setBlueRelative('#339950', 0.5))",
            "colors.setRedRelative('#503399', -1)",
            "colors.setRed('#663399', -1)",
            "colors.setRed('#663399', 'x')",
        )

        private val CONSTRUCTION = listOf(
            "colors.toHex(colors.rgb(255, 128, 9))",
            "colors.toHex(colors.rgb(0xFF, 0x80, 0x09))",
            "colors.toHex(colors.rgb('#FF8009'))",
            "colors.toHex(colors.rgb('#DE663399'))",
            "colors.toHex(colors.rgb(1, 0.5, '3.53%'))",
            "colors.toHex(colors.rgb([255, 128, 9]))",
            "colors.toHex(colors.rgb([1, 0.5, '3.53%']))",
            "colors.toHex(colors.rgb(1, 1, 1))",
            "colors.toHex(colors.rgb(1, 1, 0.5))",
            "colors.toHex(colors.rgb(300, 0, 7.4))",
            "colors.toHex(colors.rgb('50%', '25%', '12.5%'))",
            "colors.rgb(-1, 0, 0)",
            "colors.rgb('x', 0, 0)",
            "colors.rgb(1, 2)",
            "colors.rgb([1, 2])",
            "colors.toHex(colors.argb(64, 255, 128, 9), 8)",
            "colors.toHex(colors.argb('#40FF8009'), 8)",
            "colors.toHex(colors.argb(0.25, 1, 0.5, '3.53%'), 8)",
            "colors.toHex(colors.argb([64, 255, 128, 9]), 8)",
            "colors.toHex(colors.argb('#663399'), 8)",
            "colors.toHex(colors.argb('#DE663399'), 8)",
            "colors.toHex(colors.argb(1, 255, 0, 0), 8)",
            "colors.toHex(colors.rgba(255, 128, 9, 64), 8)",
            "colors.toHex(colors.rgba(1, 0.5, '3.53%', 0.25), 8)",
            "colors.toHex(colors.rgba('#FF800940'), 8)",
            "colors.toHex(colors.rgba('#663399FF'), 8)",
            "colors.toHex(colors.rgba('#11335577'), 8)",
            "colors.toHex(colors.rgba([255, 128, 9, 64]), 8)",
            "colors.toHex(colors.rgba([255, 128, 9], 64), 8)",
            "colors.toHex(colors.rgba('red'), 8)",
            "colors.toHex(colors.rgba(0x80FF0000), 8)",
            "colors.toHex(colors.hsv(90, 80, 64))",
            "colors.toHex(colors.hsv(90, 0.8, 0.64))",
            "colors.toHex(colors.hsv(0.25, 0.8, 0.64))",
            "colors.toHex(colors.hsv('25%', '80%', '64%'))",
            "colors.toHex(colors.hsv([90, 80, 64]))",
            "colors.toHex(colors.hsv(0, 1, 1))",
            "colors.toHex(colors.hsv(359.9, 1, 1))",
            "colors.toHex(colors.hsv(360, 1, 1))",
            "colors.toHex(colors.hsv(-90, 1, 1))",
            "colors.toHex(colors.hsv(450, 1, 1))",
            "colors.toHex(colors.hsv(200, 0, 0.5))",
            "colors.toHex(colors.hsva(90, 80, 64, 64), 8)",
            "colors.toHex(colors.hsva(90, 0.8, 0.64, 0.25), 8)",
            "colors.toHex(colors.hsva([90, 80, 64, 64]), 8)",
            "colors.toHex(colors.hsva([90, 80, 64], 64), 8)",
            "colors.toHex(colors.hsl(90, 80, 64))",
            "colors.toHex(colors.hsl(90, 0.8, 0.64))",
            "colors.toHex(colors.hsl(0.25, 0.8, 0.64))",
            "colors.toHex(colors.hsl('25%', '80%', '64%'))",
            "colors.toHex(colors.hsl([90, 80, 64]))",
            "colors.toHex(colors.hsl(270, 0.5, 0.4))",
            "colors.toHex(colors.hsl(0, 1, 0.5))",
            "colors.toHex(colors.hsla(90, 80, 64, 64), 8)",
            "colors.toHex(colors.hsla(90, 0.8, 0.64, 0.25), 8)",
            "colors.toHex(colors.hsla([90, 80, 64, 64]), 8)",
            "colors.toHex(colors.hsla([90, 80, 64], 64), 8)",
            "colors.hsv(1, 2)",
            "colors.hsv(90, 120, 50)",
        )

        private val DECOMPOSITION = listOf(
            "colors.toRgb('#663399')",
            "colors.toRgb(0x80112233)",
            "colors.toRgba('#DE663399')",
            "colors.toRgba('#DE663399', { maxAlpha: 1 })",
            "colors.toArgb('#DE663399')",
            "colors.toArgb('#DE663399', { maxAlpha: 1 })",
            "colors.toHsv('#663399')",
            "colors.toHsv('#FF8000')",
            "colors.toHsv('#123456')",
            "colors.toHsv(102, 51, 153)",
            "colors.toHsv([102, 51, 153])",
            "colors.toHsv('#888888')",
            "(function () { var r = util.java.array('float', 3); var v = colors.toHsv('#663399', r); return [v, Array.from(r)]; })()",
            "(function () { var r = util.java.array('float', 3); var v = colors.toHsv(102, 51, 153, r); return [v, Array.from(r)]; })()",
            "colors.toHsva('#BF663399')",
            "colors.toHsva(102, 51, 153, 191)",
            "colors.toHsva('#663399')",
            "(function () { var r = util.java.array('float', 4); var v = colors.toHsva('#BF663399', r); return [v, Array.from(r)]; })()",
            "colors.toHsl('#663399')",
            "colors.toHsl('#FF8000')",
            "colors.toHsl(102, 51, 153)",
            "colors.toHsl([102, 51, 153])",
            "(function () { var r = util.java.array('float', 3); var v = colors.toHsl('#663399', r); return [v, Array.from(r)]; })()",
            "colors.toHsla('#BF663399')",
            "colors.toHsla(102, 51, 153, 191)",
            "colors.toHsla(102, 51, 153, 0.5)",
            "colors.toHsla('#663399')",
        )

        private val COMPARISON = listOf(
            "colors.isSimilar('orange', 'dark-orange', 5)",
            "colors.isSimilar('orange', 'dark-orange', 10)",
            "colors.isSimilar('orange', 'dark-orange', 9, 'rgb+')",
            "colors.isSimilar('orange', 'dark-orange', 9, 'diff')",
            "colors.isSimilar('orange', 'dark-orange', 9, 'hs')",
            "colors.isSimilar('orange', 'dark-orange', 8, 'rgb+')",
            "colors.isSimilar('orange', 'dark-orange', 8, 'diff')",
            "colors.isSimilar('orange', 'dark-orange', 8, 'hs')",
            "colors.isSimilar('#010101', '#020202', { similarity: 0.95 })",
            "colors.isSimilar('#010101', '#090909', { threshold: 4 })",
            "colors.isSimilar('#010101', '#090909', { threshold: 10, algorithm: 'rgb' })",
            "colors.isSimilar('#010101', '#020202')",
            "colors.isSimilar('#010101', '#020202', { threshold: 4, similarity: 0.9 })",
            "colors.isEqual('#FF0000', '#FF0000')",
            "colors.isEqual('#FF0000', '#F00')",
            "colors.isEqual(-65536, 0xFF0000)",
            "colors.isEqual('red', 'RED')",
            "colors.isEqual('orange', 'Orange')",
            "colors.isEqual('dark-gray', 'DARK_GRAY')",
            "colors.isEqual('red', '#FF0000')",
            "colors.isEqual('orange', '#FFA500')",
            "colors.isEqual('#A1FF0000', '#A2FF0000')",
            "colors.isEqual('#A1FF0000', '#A2FF0000', true)",
            "colors.isEqual('#A1FF0000', '#A2FF0000', false)",
            "colors.isEqual('#A1FF0000', '#A2FF0000', 1)",
            "colors.luminance(colors.WHITE)",
            "colors.luminance(colors.BLACK)",
            "colors.luminance(colors.RED)",
            "colors.luminance(colors.GREEN)",
            "colors.luminance(colors.BLUE)",
            "colors.luminance(colors.YELLOW)",
            "colors.luminance('#663399')",
            "colors.summary('#009688')",
            "colors.summary('#BE009688')",
            "colors.summary('red')",
            "colors.summary('#6400008B')",
            "colors.summary(0)",
            "[colors.RED, colors.BLACK, colors.ORANGE, colors.TRANSPARENT, colors.WHITE]",
            "[colors.android.RED, colors.css.ORANGE, colors.web.BURNT_ORANGE, colors.material.ORANGE_300]",
            "[colors.PANSY, colors.ALIZARIN_CRIMSON, colors.PURPLE_300]",
            "typeof colors",
        )

        private val EXPRESSIONS = CONVERSIONS + COMPONENTS + CONSTRUCTION + DECOMPOSITION + COMPARISON

        private const val INVALID_TEXT_REASON = "D10: a color text or hex length that cannot be used is a RangeError on the new layer; the obsolete " +
                "module let the host ColorUtils / android.graphics.Color throw an IllegalArgumentException (a JavaException)"

        private const val NOT_A_COLOR_REASON = "D10: a value that is not a color at all (or no value) is a TypeError on the new layer; the obsolete " +
                "module turned it into text and failed in the host ColorUtils with an IllegalArgumentException (a JavaException)"

        private const val UNUSABLE_VALUE_REASON = "D10: max other than 1 / 255 and negative components or percentages are unusable values, a " +
                "RangeError on the new layer; the obsolete module threw a TypeError"

        private const val GREEN_RELATIVE_REASON = "obsolete JS bug: colors.setGreenRelative called getGreen instead of setGreen and returned the " +
                "green component; the new layer follows the documentation and the current Kotlin module"

        private const val GARBAGE_REASON = "Q12: the obsolete module silently produced a color from a wrong argument count or an out of range " +
                "saturation; the current Kotlin module throws, and so does the new layer (TypeError for the count or array length, RangeError " +
                "for the value, D10)"

        private const val JAVA_EXCEPTION = "throws JavaException IllegalArgumentException"

        /** Differences kept or fixed on purpose, see ROADMAP appendix E ("P2.3 new deviations"), plus D10 and Q12. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference("colors.toInt('nope')", js = JAVA_EXCEPTION, kt = "throws RangeError", reason = INVALID_TEXT_REASON),
            DifferentialCases.KnownDifference("colors.toInt('#GGGGGG')", js = JAVA_EXCEPTION, kt = "throws RangeError", reason = INVALID_TEXT_REASON),
            DifferentialCases.KnownDifference("colors.toInt('#1234')", js = JAVA_EXCEPTION, kt = "throws RangeError", reason = INVALID_TEXT_REASON),
            DifferentialCases.KnownDifference("colors.toInt('-')", js = JAVA_EXCEPTION, kt = "throws RangeError", reason = INVALID_TEXT_REASON),
            DifferentialCases.KnownDifference("colors.toInt({})", js = JAVA_EXCEPTION, kt = "throws TypeError", reason = NOT_A_COLOR_REASON),
            DifferentialCases.KnownDifference("colors.toInt(true)", js = JAVA_EXCEPTION, kt = "throws TypeError", reason = NOT_A_COLOR_REASON),
            DifferentialCases.KnownDifference("colors.toInt(null)", js = JAVA_EXCEPTION, kt = "throws TypeError", reason = NOT_A_COLOR_REASON),
            DifferentialCases.KnownDifference("colors.toInt()", js = JAVA_EXCEPTION, kt = "throws TypeError", reason = NOT_A_COLOR_REASON),
            DifferentialCases.KnownDifference("colors.toHex('#FAEBD7', 3)", js = JAVA_EXCEPTION, kt = "throws RangeError", reason = INVALID_TEXT_REASON),
            DifferentialCases.KnownDifference("colors.toHex('#AA9966CC', 7)", js = JAVA_EXCEPTION, kt = "throws RangeError", reason = INVALID_TEXT_REASON),
            DifferentialCases.KnownDifference("colors.toHex('#AA9966CC', 'whatever')", js = JAVA_EXCEPTION, kt = "throws RangeError", reason = INVALID_TEXT_REASON),
            DifferentialCases.KnownDifference("colors.alpha('#05060708', { max: 3 })", js = "throws TypeError", kt = "throws RangeError", reason = UNUSABLE_VALUE_REASON),
            DifferentialCases.KnownDifference("colors.toHex(colors.setGreenRelative('#335099', 0.5))", js = "\"#00000050\"", kt = "\"#332899\"", reason = GREEN_RELATIVE_REASON),
            DifferentialCases.KnownDifference("colors.setRedRelative('#503399', -1)", js = "throws TypeError", kt = "throws RangeError", reason = UNUSABLE_VALUE_REASON),
            DifferentialCases.KnownDifference("colors.setRed('#663399', -1)", js = "throws TypeError", kt = "throws RangeError", reason = UNUSABLE_VALUE_REASON),
            DifferentialCases.KnownDifference("colors.rgb(-1, 0, 0)", js = "throws TypeError", kt = "throws RangeError", reason = UNUSABLE_VALUE_REASON),
            DifferentialCases.KnownDifference("colors.rgb(1, 2)", js = "-16777215", kt = "throws TypeError", reason = GARBAGE_REASON),
            DifferentialCases.KnownDifference("colors.rgb([1, 2])", js = "-16777215", kt = "throws TypeError", reason = GARBAGE_REASON),
            DifferentialCases.KnownDifference("colors.hsv(90, 120, 50)", js = "-12550144", kt = "throws RangeError", reason = GARBAGE_REASON),
        )

    }

}
