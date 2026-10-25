package org.autojs.autojs.runtime.api.augment.differential

import org.autojs.autojs.runtime.api.modules.jsox.Numberx
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Obsolete `__Numberx__.js` against the Kotlin [Numberx] augmentable (roadmap P0.4).
 */
class NumberxDifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsNumberx: Any
    private lateinit var ktNumberx: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        jsNumberx = ObsoleteModuleHost(rhino.context, rhino.jsScope).loadAndDefine("Numberx")!!
        ktNumberx = Numberx(rhino.ktRuntime).augment(rhino.ktScope.globalThis)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheKotlinModule() {
        val report = SurfaceDiff.compare(SurfaceDiff.surface(jsNumberx, rhino.jsScope.globalThis), SurfaceDiff.surface(ktNumberx, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        val missing = report.onlyInFirst - KNOWN_MISSING
        assertTrue("Obsolete Numberx members missing from the Kotlin module: $missing", missing.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, tolerateErrorTypes = true)
    }

    companion object {

        private val KNOWN_MISSING = emptySet<String>()

        private val EXPRESSIONS = listOf(
            "Numberx.ICU",
            "Numberx.check()",
            "Numberx.check(1)",
            "Numberx.check('1')",
            "Numberx.check(1, 1)",
            "Numberx.check(1, 2)",
            "Numberx.check(1, '<', 2, '<=', 2, '>=', 1)",
            "Numberx.check(3, '<', 2)",
            "Numberx.check(1, '==', 1)",
            "Numberx.check(1, '<', '2')",
            "Numberx.clamp(5, 1, 3)",
            "Numberx.clamp(5, [1, 3])",
            "Numberx.clamp(5, [3, 1])",
            "Numberx.clamp(-1, 0, 10)",
            "Numberx.clamp(5)",
            "Numberx.clamp(5, 'x', 3)",
            "Numberx.clamp('5', 1, 3)",
            "Numberx.clampTo(12, [0, 10])",
            "Numberx.clampTo(-3, [0, 10], 5)",
            "Numberx.clampTo(5, [0, 10])",
            "Numberx.clampTo(5, [])",
            "Numberx.clampTo(5, [3, 3])",
            "Numberx.toFixedNum(1.23456, 2)",
            "Numberx.toFixedNum(1.5, 0)",
            "Numberx.toFixedNum('1.5', 0)",
            "Numberx.padStart(7, 3)",
            "Numberx.padStart(7, 3, 'x')",
            "Numberx.padEnd(7, 3)",
            "Numberx.padEnd(7, 3, 'xy')",
            "Numberx.parseFloat('1.5')",
            "Numberx.parseFloat('1.1', 2)",
            "Numberx.parseFloat('ff.8', 16)",
            "Numberx.parseFloat(1.25, 2)",
            "Numberx.parsePercent('50%')",
            "Numberx.parsePercent('50%%')",
            "Numberx.parsePercent(' 5 0 % ')",
            "Numberx.parsePercent('-12.5%')",
            "Numberx.parsePercent(0.3)",
            "Numberx.parsePercent('abc')",
            "Numberx.parseRatio('1:4')",
            "Numberx.parseRatio('3 : 4')",
            "Numberx.parseRatio('x:4')",
            "Numberx.parseAny(' 3:4 ')",
            "Numberx.parseAny('12%')",
            "Numberx.parseAny('42')",
            "Numberx.parseAny(42)",
            "Numberx.parseAny(' ')",
            "Numberx.parseAny('abc')",
            "Numberx.ensureNumber(1, 2)",
            "Numberx.ensureNumber('1')",
            "typeof Numberx",
        )

        /** Differences between the obsolete module and the current Kotlin module, see ROADMAP appendix E. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference(
                "Numberx.check(1, '==', 1)",
                js = "throws Error",
                kt = "true",
                reason = "Kotlin also accepts the == operator; the obsolete module only knew <, <=, >, >= and =",
            ),
            DifferentialCases.KnownDifference(
                "Numberx.check(1, '<', '2')",
                js = "throws Error",
                kt = "true",
                reason = "Kotlin coerces numeric strings; the obsolete module required numbers",
            ),
            DifferentialCases.KnownDifference(
                "Numberx.clamp(5, 'x', 3)",
                js = "3",
                kt = "throws WrappedIllegalArgumentException",
                reason = "the obsolete module filtered non-numeric clamps out; Kotlin rejects them (roadmap: restore the obsolete behaviour in P2.2)",
            ),
            DifferentialCases.KnownDifference(
                "Numberx.clamp('5', 1, 3)",
                js = "throws TypeError",
                kt = "3",
                reason = "Kotlin coerces a numeric string argument; the obsolete module required a number",
            ),
            DifferentialCases.KnownDifference(
                "Numberx.toFixedNum('1.5', 0)",
                js = "throws TypeError",
                kt = "2",
                reason = "Kotlin coerces a numeric string argument; the obsolete module required a number",
            ),
            DifferentialCases.KnownDifference(
                "Numberx.parseFloat(1.25, 2)",
                js = "throws TypeError",
                kt = "0.25",
                reason = "the obsolete module used the in operator on a number; Kotlin converts the number to a string first",
            ),
            DifferentialCases.KnownDifference(
                "Numberx.parsePercent(' 5 0 % ')",
                js = "0.5",
                kt = "NaN",
                reason = "the obsolete module removed all whitespace before matching; Kotlin only trims the ends (roadmap: restore in P2.2)",
            ),
            DifferentialCases.KnownDifference(
                "typeof Numberx",
                js = "\"function\"",
                kt = "\"object\"",
                reason = "the obsolete module was Number.bind(Number) with members assigned; the d.ts interface declares no call signature",
            ),
        )

    }

}
