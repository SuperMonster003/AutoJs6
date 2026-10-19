package org.autojs.autojs.runtime.api.augment.differential

import org.autojs.autojs.runtime.api.augment.jsox.Arrayx
import org.autojs.autojs.runtime.api.augment.jsox.Mathx
import org.autojs.autojs.runtime.api.augment.jsox.Numberx
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Obsolete `__Mathx__.js` against the Kotlin [Mathx] augmentable (roadmap P0.4). The obsolete module reads
 * `Arrayx` and `Numberx` from the global object, so those are loaded first on both sides.
 */
class MathxDifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsMathx: Any
    private lateinit var ktMathx: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
        host.loadAndDefine("Arrayx")
        host.loadAndDefine("Numberx")
        jsMathx = host.loadAndDefine("Mathx")!!
        val global = rhino.ktScope.globalThis
        Arrayx(rhino.ktRuntime).augment(global)
        Numberx(rhino.ktRuntime).augment(global)
        ktMathx = Mathx(rhino.ktRuntime).augment(global)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheKotlinModule() {
        val report = SurfaceDiff.compare(SurfaceDiff.surface(jsMathx, rhino.jsScope.globalThis), SurfaceDiff.surface(ktMathx, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        val missing = report.onlyInFirst - KNOWN_MISSING
        assertTrue("Obsolete Mathx members missing from the Kotlin module: $missing", missing.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, tolerateErrorTypes = true)
    }

    companion object {

        private val KNOWN_MISSING = emptySet<String>()

        private val EXPRESSIONS = listOf(
            "Mathx.sum([1, 2, 3])",
            "Mathx.sum(1, 2, 3)",
            "Mathx.sum([1.111, 2.222], 1)",
            "Mathx.sum([[1, 2], [3]])",
            "Mathx.sum([])",
            "Mathx.sum([1, NaN])",
            "Mathx.sum(['1', '2'])",
            "Mathx.avg([1, 2, 3, 4])",
            "Mathx.avg([1, 2], 3)",
            "Mathx.avg([])",
            "Mathx.median([5, 1, 3])",
            "Mathx.median([4, 1, 3, 2])",
            "Mathx.median([10, 9, 100])",
            "Mathx.median([])",
            "Mathx.var([1, 2, 3, 4])",
            "Mathx.var([1, 2, 3, 4], 2)",
            "Mathx.std([1, 2, 3, 4], 3)",
            "Mathx.std([5])",
            "Mathx.cv([1, 2, 3, 4], 3)",
            "Mathx.cv([5])",
            "Mathx.max([1, 5, 3])",
            "Mathx.max(1, 5, 3)",
            "Mathx.max([1.26, 5.55], 1)",
            "Mathx.min([1, 5, 3])",
            "Mathx.min([])",
            "Mathx.max([])",
            "Mathx.dist([0, 0], [3, 4])",
            "Mathx.dist({ x: 0, y: 0 }, { x: 1, y: 1 }, 2)",
            "Mathx.dist([0, 0], [1, 1])",
            "Mathx.dist([1, 2, 3], [0, 0])",
            "Mathx.logMn(2, 8)",
            "Mathx.logMn(10, 1000)",
            "Mathx.logMn(2, 10, 3)",
            "Mathx.logMn(2, -1)",
            "Mathx.floorLog(2, 10)",
            "Mathx.ceilLog(2, 10)",
            "Mathx.roundLog(2, 10)",
            "Mathx.floorPow(2, 10)",
            "Mathx.ceilPow(2, 10)",
            "Mathx.roundPow(2, 10)",
            "typeof Mathx.randInt(1, 10)",
            "Mathx.randInt(5, 5)",
            "Mathx.randInt([7, 7])",
            "Mathx.randInt(0)",
            "(function () { var r = Mathx.randInt(1, 3); return r >= 1 && r <= 3 && r === Math.floor(r); })()",
            "(function () { var r = Mathx.randInt(-3); return r >= -3 && r <= 0 && r === Math.floor(r); })()",
            "Mathx.randInt('x')",
            "typeof Mathx",
        )

        private const val FRACTION_FOLDED = "Kotlin calcBy receives the argument array as a single vararg element (missing spread), so the fraction argument is summed as a number; d.ts documents sum([1.01, 2.02], 2) === 3.03 (roadmap P2.1 fixes this)"

        /** Differences between the obsolete module and the current Kotlin module, see ROADMAP appendix E. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference("Mathx.sum([1.111, 2.222], 1)", js = "3.3", kt = "4.333", reason = FRACTION_FOLDED),
            DifferentialCases.KnownDifference("Mathx.avg([1, 2], 3)", js = "1.5", kt = "2", reason = FRACTION_FOLDED),
            DifferentialCases.KnownDifference("Mathx.var([1, 2, 3, 4], 2)", js = "1.25", kt = "1.04", reason = FRACTION_FOLDED),
            DifferentialCases.KnownDifference("Mathx.std([1, 2, 3, 4], 3)", js = "1.118", kt = "1.019803902718557", reason = FRACTION_FOLDED),
            DifferentialCases.KnownDifference("Mathx.cv([1, 2, 3, 4], 3)", js = "0.516", kt = "0.4385290096535146", reason = FRACTION_FOLDED),
            DifferentialCases.KnownDifference("Mathx.max([1.26, 5.55], 1)", js = "5.5", kt = "5.55", reason = FRACTION_FOLDED),
            DifferentialCases.KnownDifference(
                "Mathx.sum([])",
                js = "NaN",
                kt = "0",
                reason = "d.ts documents Mathx.sum() === 0; the obsolete module returned NaN for an empty list",
            ),
            DifferentialCases.KnownDifference(
                "Mathx.median([])",
                js = "NaN",
                kt = "throws IndexOutOfBoundsException",
                reason = "Kotlin does not handle an empty list and fails with an index error (roadmap P2.1 fixes this)",
            ),
            DifferentialCases.KnownDifference(
                "Mathx.min([])",
                js = "Infinity",
                kt = "NaN",
                reason = "undocumented edge case; Kotlin returns NaN where the obsolete module returned Math.min() of nothing",
            ),
            DifferentialCases.KnownDifference(
                "Mathx.max([])",
                js = "-Infinity",
                kt = "NaN",
                reason = "undocumented edge case; Kotlin returns NaN where the obsolete module returned Math.max() of nothing",
            ),
            DifferentialCases.KnownDifference(
                "Mathx.logMn(10, 1000)",
                js = "2.9999999999999996",
                kt = "3",
                reason = "the obsolete module only rounded when fraction was -1 (inverted condition); d.ts documents the default 13-digit rounding that Kotlin applies",
            ),
            DifferentialCases.KnownDifference(
                "Mathx.logMn(2, 10, 3)",
                js = "3.3219280948873626",
                kt = "3.322",
                reason = "same inverted fraction condition in the obsolete module; Kotlin follows the documentation",
            ),
        )

    }

}
