package org.autojs.autojs.runtime.api.modules.base64

import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.autojs.autojs.runtime.api.modules.differential.DifferentialCases
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.differential.SurfaceDiff
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Obsolete `__base64__.js` against `base64` of the new script module layer (roadmap P2.2 / P2.4), mounted the way
 * `ScriptRuntime.augment()` does with the switch on; both sides run on the JVM fake of `android.util.Base64`. Error types
 * are compared (D10); the differences left are the ones ROADMAP appendix E judges for the current Kotlin module (charset
 * names and unknown charsets as documented) plus D10 and appendix C Q7.
 */
class Base64ModuleDifferentialTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        rhino.mountNewModules("base64")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val ktBase64 = rhino.evalKt("base64")
        val report = SurfaceDiff.compare(ScriptModuleExpectations.obsoleteSurface("surface"), SurfaceDiff.surface(ktBase64, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        assertTrue("Obsolete base64 members missing from the new module: ${report.onlyInFirst}", report.onlyInFirst.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {

        private val EXPRESSIONS = listOf(
            "base64.encode('hello')",
            "base64.encode('')",
            "base64.encode('hello', 'UTF-8')",
            "base64.encode('\\u4f60\\u597d', 'utf8')",
            "base64.encode('\\u4f60\\u597d', 'UTF-16')",
            "base64.encode('\\u4f60\\u597d', 'utf_16le')",
            "base64.encode('\\u4f60\\u597d', ' UTF-16BE ')",
            "base64.encode('\\u00e9', 'ISO-8859-1')",
            "base64.encode('\\u00e9', 'latin1')",
            "base64.encode('\\u4f60\\u597d', 'GBK') === base64.encode('\\u4f60\\u597d', 'UTF-8')",
            "base64.encode('a', 'definitely-not-a-charset')",
            "base64.encode('\\u4f60\\u597d', java.nio.charset.StandardCharsets.UTF_16LE)",
            "base64.encode([104, 105])",
            "base64.encode([104, 'x', 300, -1])",
            "base64.encode([])",
            "base64.encode(new java.lang.String('hi').getBytes())",
            "base64.encode({ toString() { return 'obj'; } })",
            "base64.encode(123)",
            "base64.encode(1.5)",
            "base64.encode(true)",
            "(function (s) { return base64.encode(s + 'i'); })('h')",
            "base64.encode(null)",
            "base64.encode(undefined)",
            "base64.encode()",
            "base64.encode('a', 'UTF-8', 'extra')",
            "base64.decode('aGVsbG8=')",
            "base64.decode('aGVsbG8')",
            "base64.decode('aGVs bG8=')",
            "base64.decode('aGVs\\nbG8=')",
            "base64.decode('aGVsbG8=!!')",
            "base64.decode('a')",
            "base64.decode('')",
            "base64.decode('5L2g5aW9', 'UTF-8')",
            "base64.decode('YM99WQ==', 'UTF-16LE')",
            "base64.decode('/v9PYFl9', 'UTF-16')",
            "base64.decode('6Q==', 'ISO-8859-1')",
            "base64.decode('xOO6ww==', 'GBK') === '\\u4f60\\u597d'",
            "base64.decode('aGk=', 'definitely-not-a-charset')",
            "base64.decode([97, 71, 107, 61])",
            "base64.decode(new java.lang.String('aGk=').getBytes())",
            "base64.decode({ toString() { return 'aGk='; } }) === 'hi'",
            "base64.decode(null)",
            "base64.decode()",
            "base64.decode('aGk=', 'UTF-8', 'extra')",
            "base64.decode(base64.encode('\\u4f60\\u597d', 'UTF-16'), 'UTF-16')",
            "base64.decode(base64.encode('\\u4f60\\u597d', 'UTF-16LE'), 'UTF-16LE')",
            "typeof base64",
            "typeof base64.encode",
        )

        private const val CHARSET_REASON = "documented: charset names ignore case and punctuation and every available charset and alias is known; the " +
                "obsolete module knew six StandardCharsets and stripped \\W+ (an underscore is a word character), falling back to the default charset " +
                "(appendix E: keep the Kotlin behavior)"

        private const val UNKNOWN_CHARSET_REASON = "documented: an unknown charset is an argument error (RangeError on the new layer, D10); the obsolete " +
                "module silently used the default charset (appendix E: keep the Kotlin behavior)"

        private const val NULLISH_REASON = "D10: the obsolete module threw a plain Error for a nullish input, the new layer a TypeError (the argument error type " +
                "fixed by D10, as recorded for Numberx.check / Mathx.dist in P2.1)"

        private const val NULL_REASON = "D10: for null the obsolete module failed in util.getClassName (Rhino could not choose a Java overload, " +
                "an InternalError) before its own Error; the new layer throws a TypeError"

        private const val SURPLUS_REASON = "surplus arguments: ignored by the obsolete module, a TypeError on the new layer (appendix C Q7)"

        /** Differences kept from the current Kotlin module, see ROADMAP appendix E, plus D10 and Q7. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference("base64.encode('\\u4f60\\u597d', 'utf_16le')", js = "\"5L2g5aW9\"", kt = "\"YE99WQ==\"", reason = CHARSET_REASON),
            DifferentialCases.KnownDifference("base64.encode('\\u00e9', 'latin1')", js = "\"w6k=\"", kt = "\"6Q==\"", reason = CHARSET_REASON),
            DifferentialCases.KnownDifference("base64.encode('\\u4f60\\u597d', 'GBK') === base64.encode('\\u4f60\\u597d', 'UTF-8')", js = "true", kt = "false", reason = CHARSET_REASON),
            DifferentialCases.KnownDifference("base64.decode('xOO6ww==', 'GBK') === '\\u4f60\\u597d'", js = "false", kt = "true", reason = CHARSET_REASON),
            DifferentialCases.KnownDifference("base64.encode('a', 'definitely-not-a-charset')", js = "\"YQ==\"", kt = "throws RangeError", reason = UNKNOWN_CHARSET_REASON),
            DifferentialCases.KnownDifference("base64.decode('aGk=', 'definitely-not-a-charset')", js = "\"hi\"", kt = "throws RangeError", reason = UNKNOWN_CHARSET_REASON),
            DifferentialCases.KnownDifference("base64.encode(null)", js = "throws InternalError", kt = "throws TypeError", reason = NULL_REASON),
            DifferentialCases.KnownDifference("base64.encode(undefined)", js = "throws Error", kt = "throws TypeError", reason = NULLISH_REASON),
            DifferentialCases.KnownDifference("base64.encode()", js = "throws Error", kt = "throws TypeError", reason = NULLISH_REASON),
            DifferentialCases.KnownDifference("base64.decode(null)", js = "throws InternalError", kt = "throws TypeError", reason = NULL_REASON),
            DifferentialCases.KnownDifference("base64.decode()", js = "throws Error", kt = "throws TypeError", reason = NULLISH_REASON),
            DifferentialCases.KnownDifference("base64.encode('a', 'UTF-8', 'extra')", js = "\"YQ==\"", kt = "throws TypeError", reason = SURPLUS_REASON),
            DifferentialCases.KnownDifference("base64.decode('aGk=', 'UTF-8', 'extra')", js = "\"hi\"", kt = "throws TypeError", reason = SURPLUS_REASON),
        )

    }

}
