package org.autojs.autojs.runtime.api.augment.differential

import org.autojs.autojs.runtime.api.augment.base64.Base64
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Obsolete `__base64__.js` against the Kotlin [Base64] augmentable (roadmap P0.5). Both sides run on the JVM fake
 * of `android.util.Base64` (`app/src/test/java/android/util/Base64.java`).
 */
class Base64DifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsBase64: Any
    private lateinit var ktBase64: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        jsBase64 = ObsoleteModuleHost(rhino.context, rhino.jsScope).loadAndDefine("base64")!!
        ktBase64 = Base64.augmentWithRuntime(rhino.ktScope.globalThis, rhino.ktRuntime)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheKotlinModule() {
        val report = SurfaceDiff.compare(SurfaceDiff.surface(jsBase64, rhino.jsScope.globalThis), SurfaceDiff.surface(ktBase64, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        assertTrue("Obsolete base64 members missing from the Kotlin module: ${report.onlyInFirst}", report.onlyInFirst.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, tolerateErrorTypes = true)
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
            "base64.encode(true)",
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
            "base64.decode(base64.encode('\\u4f60\\u597d', 'UTF-16'), 'UTF-16')",
            "base64.decode(base64.encode('\\u4f60\\u597d', 'UTF-16LE'), 'UTF-16LE')",
        )

        /**
         * Differences between the obsolete module and the current Kotlin module, see ROADMAP appendix E. Where the
         * obsolete module did not recognize a charset it fell back to the platform default (UTF-8 on Android and on
         * JDK 18+), which the expected JS values below assume.
         */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference(
                "base64.encode('\\u4f60\\u597d', 'utf_16le')",
                js = "\"5L2g5aW9\"",
                kt = "\"YE99WQ==\"",
                reason = "the obsolete module only knew six StandardCharsets and stripped \\W+ (an underscore is a word character), " +
                        "falling back to the default charset; the documentation says names ignore case and punctuation, as Kotlin does",
            ),
            DifferentialCases.KnownDifference(
                "base64.encode('\\u00e9', 'latin1')",
                js = "\"w6k=\"",
                kt = "\"6Q==\"",
                reason = "the obsolete module knew no charset aliases and fell back to the default charset; Kotlin resolves every available charset and alias",
            ),
            DifferentialCases.KnownDifference(
                "base64.encode('\\u4f60\\u597d', 'GBK') === base64.encode('\\u4f60\\u597d', 'UTF-8')",
                js = "true",
                kt = "false",
                reason = "same as latin1: GBK was unknown to the obsolete module",
            ),
            DifferentialCases.KnownDifference(
                "base64.encode('a', 'definitely-not-a-charset')",
                js = "\"YQ==\"",
                kt = "throws WrappedIllegalArgumentException",
                reason = "documented: an unknown charset throws; the obsolete module silently used the default charset",
            ),
            DifferentialCases.KnownDifference(
                "base64.encode({ toString() { return 'obj'; } })",
                js = "\"b2Jq\"",
                kt = "\"W29iamVjdCBPYmplY3Rd\"",
                reason = "the d.ts input type includes { toString(): string }; Kotlin calls the Kotlin toString of the NativeObject " +
                        "([object Object]) instead of the script's toString (P2.2)",
            ),
            DifferentialCases.KnownDifference(
                "base64.encode(123)",
                js = "\"MTIz\"",
                kt = "\"MTIzLjA=\"",
                reason = "Kotlin stringifies the boxed Double with the Kotlin toString (123.0) instead of the JS ToString (123) (P2.2)",
            ),
            DifferentialCases.KnownDifference(
                "base64.encode('a', 'UTF-8', 'extra')",
                js = "\"YQ==\"",
                kt = "throws MessageLookup",
                reason = "surplus arguments: ignored by the obsolete module, rejected by Kotlin (appendix C Q7)",
            ),
            DifferentialCases.KnownDifference(
                "base64.decode('xOO6ww==', 'GBK') === '\\u4f60\\u597d'",
                js = "false",
                kt = "true",
                reason = "same as encode: GBK was unknown to the obsolete module",
            ),
            DifferentialCases.KnownDifference(
                "base64.decode('aGk=', 'definitely-not-a-charset')",
                js = "\"hi\"",
                kt = "throws WrappedIllegalArgumentException",
                reason = "documented: an unknown charset throws",
            ),
            DifferentialCases.KnownDifference(
                "base64.decode({ toString() { return 'aGk='; } }) === 'hi'",
                js = "true",
                kt = "false",
                reason = "same as encode: Kotlin decodes '[object Object]' instead of the script's toString (P2.2)",
            ),
        )

    }

}
