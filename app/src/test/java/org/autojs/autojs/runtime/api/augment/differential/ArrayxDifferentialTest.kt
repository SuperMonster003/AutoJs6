package org.autojs.autojs.runtime.api.augment.differential

import org.autojs.autojs.runtime.api.modules.jsox.Arrayx
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Obsolete `__Arrayx__.js` against the Kotlin [Arrayx] augmentable (roadmap P0.4).
 */
class ArrayxDifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsArrayx: Any
    private lateinit var ktArrayx: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        jsArrayx = ObsoleteModuleHost(rhino.context, rhino.jsScope).loadAndDefine("Arrayx")!!
        ktArrayx = Arrayx(rhino.ktRuntime).augment(rhino.ktScope.globalThis)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheKotlinModule() {
        val report = SurfaceDiff.compare(SurfaceDiff.surface(jsArrayx, rhino.jsScope.globalThis), SurfaceDiff.surface(ktArrayx, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        val missing = report.onlyInFirst - KNOWN_MISSING
        assertTrue("Obsolete Arrayx members missing from the Kotlin module: $missing", missing.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, tolerateErrorTypes = true)
    }

    companion object {

        /** `different` only threw `Error('TODO')` in the obsolete module. */
        private val KNOWN_MISSING = setOf("different")

        private val EXPRESSIONS = listOf(
            "Arrayx.distinct([1, 1, 2, '2', NaN, NaN])",
            "Arrayx.distinctBy([{ a: 1 }, { a: 1 }, { a: 2 }], o => o.a)",
            "Arrayx.union([1, 2], [2, 3], 4)",
            "Arrayx.union([1, 1])",
            "Arrayx.intersect([1, 2, 2, 3], [2, 3], 3)",
            "Arrayx.intersect([1, 2, 2, 3], [2, 3])",
            "Arrayx.intersect([1, 2])",
            "Arrayx.intersect([])",
            "Arrayx.sortBy([3, 1, 2], x => -x)",
            "Arrayx.sortByDescending([1, 3, 2], x => x)",
            "Arrayx.sortDescending([1, 3, 2])",
            "Arrayx.sorted([3, 1, 2])",
            "Arrayx.sorted(['b', 'a', 10, 9])",
            "Arrayx.sortedBy([3, 1, 2], x => x)",
            "Arrayx.sortedDescending([3, 1, 2])",
            "Arrayx.sortedByDescending([3, 1, 2], x => x)",
            "Arrayx.shuffle([1, 2, 3]).length",
            "(function () { var a = [3, 1, 2]; var r = Arrayx.sortBy(a, x => x); return [a, r === a]; })()",
            "(function () { var a = [3, 1, 2]; var r = Arrayx.sorted(a); return [a, r === a]; })()",
            "(function () { var a = [3, 1, 2]; var r = Arrayx.sortedByDescending(a, x => x); return [a, r]; })()",
            "Arrayx.ensureArray([])",
            "Arrayx.ensureArray(1)",
            "Arrayx.distinct(1)",
            "typeof Arrayx",
        )

        /** Differences between the obsolete module and the current Kotlin module, see ROADMAP appendix E. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference(
                "(function () { var a = [3, 1, 2]; var r = Arrayx.sortedByDescending(a, x => x); return [a, r]; })()",
                js = "[[3,2,1],[3,2,1]]",
                kt = "[[3,1,2],[3,2,1]]",
                reason = "the obsolete module sorted the input in place (it called sortByDescending on the original); Kotlin keeps the input untouched as the sorted* family documents",
            ),
            DifferentialCases.KnownDifference(
                "typeof Arrayx",
                js = "\"function\"",
                kt = "\"object\"",
                reason = "the obsolete module was Array.bind(Array) with members assigned; the d.ts interface declares no call signature",
            ),
        )

    }

}
