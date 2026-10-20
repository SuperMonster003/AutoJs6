package org.autojs.autojs.runtime.api.modules.jsox

import org.autojs.autojs.runtime.api.augment.differential.DifferentialCases
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Obsolete `__Arrayx__.js` against `Arrayx` of the new script module layer (roadmap P2.1 / P2.4), mounted the way
 * `ScriptRuntime.augment()` does with the switch on. Error types are compared (D10); the only differences left are the
 * ones ROADMAP appendix E keeps from the current Kotlin module.
 */
class ArrayxModuleDifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsArrayx: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        jsArrayx = ObsoleteModuleHost(rhino.context, rhino.jsScope).loadAndDefine("Arrayx")!!
        rhino.mountNewModules("Arrayx")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val ktArrayx = rhino.evalKt("Arrayx")
        val report = SurfaceDiff.compare(SurfaceDiff.surface(jsArrayx, rhino.jsScope.globalThis), SurfaceDiff.surface(ktArrayx, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        val missing = report.onlyInFirst - KNOWN_MISSING
        assertTrue("Obsolete Arrayx members missing from the new module: $missing", missing.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {

        /** `different` only threw `Error('TODO')` in the obsolete module. */
        private val KNOWN_MISSING = setOf("different")

        private val EXPRESSIONS = listOf(
            "Arrayx.distinct([1, 1, 2, '2', NaN, NaN])",
            "Arrayx.distinct([1, 2, 1, 3, 2, 2])",
            "Arrayx.distinct([1, , 1])",
            "Arrayx.distinct([0, -0])",
            "Arrayx.distinctBy([{ a: 1 }, { a: 1 }, { a: 2 }], o => o.a)",
            "Arrayx.distinctBy(['a', 'b', 'ab', 'ba', 'abc'], a => a.length)",
            "Arrayx.distinctBy([1, 2, 1, 3, 2, 2], e => e % 2)",
            "Arrayx.union([1, 2], [2, 3], 4)",
            "Arrayx.union([1, 1])",
            "Arrayx.union([1, 2, 3], 0, 5, [2, 3, 4], [2, 3, 4, 2, 2, 2])",
            "Arrayx.intersect([1, 2, 2, 3], [2, 3], 3)",
            "Arrayx.intersect([1, 2, 2, 3], [2, 3])",
            "Arrayx.intersect([1, 2, 3, 3], 2, [2, 3, 4], [2, 3, 3])",
            "Arrayx.intersect([1, 1, 2], [1, 1, 3])",
            "Arrayx.intersect([1, 2])",
            "Arrayx.intersect([])",
            "Arrayx.sortBy([3, 1, 2], x => -x)",
            "Arrayx.sortBy(['10', '2', '30', '4', '50', '0x6'], Number)",
            "Arrayx.sortBy(['a', 'ab', 'c', 'bc', 'abc', '123'], e => e.length)",
            "Arrayx.sortBy([5], x => x)",
            "Arrayx.sortByDescending([1, 3, 2], x => x)",
            "Arrayx.sortDescending([1, 3, 2])",
            "Arrayx.sortDescending([])",
            "Arrayx.sorted([3, 1, 2])",
            "Arrayx.sorted([80, 70, 9])",
            "Arrayx.sorted(['b', 'a', 10, 9])",
            "Arrayx.sortedBy([3, 1, 2], x => x)",
            "Arrayx.sortedBy([], x => x)",
            "Arrayx.sortedDescending([3, 1, 2])",
            "Arrayx.sortedByDescending([3, 1, 2], x => x)",
            "Arrayx.shuffle([1, 2, 3]).length",
            "(function () { var a = [1, 2, 3, 4]; return Arrayx.shuffle(a) === a && Arrayx.sorted(a).join() === '1,2,3,4'; })()",
            "(function () { var a = [3, 1, 2]; var r = Arrayx.sortBy(a, x => x); return [a, r === a]; })()",
            "(function () { var a = [3, 1, 2]; var r = Arrayx.sortDescending(a); return [a, r === a]; })()",
            "(function () { var a = [3, 1, 2]; var r = Arrayx.sorted(a); return [a, r === a]; })()",
            "(function () { var a = [3, 1, 2]; var r = Arrayx.sortedBy(a, x => x); return [a, r === a]; })()",
            "(function () { var a = [3, 1, 2]; var r = Arrayx.sortedByDescending(a, x => x); return [a, r]; })()",
            "Arrayx.ensureArray([])",
            "Arrayx.ensureArray([5], [2, 3])",
            "Arrayx.ensureArray([], 9)",
            "Arrayx.ensureArray(1)",
            "Arrayx.ensureArray()",
            "Arrayx.distinct(1)",
            "Arrayx.sortBy(1, x => x)",
            "Arrayx.sorted('abc')",
            "typeof Arrayx",
        )

        /** Differences kept from the current Kotlin module, see ROADMAP appendix E. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference(
                "(function () { var a = [3, 1, 2]; var r = Arrayx.sortedByDescending(a, x => x); return [a, r]; })()",
                js = "[[3,2,1],[3,2,1]]",
                kt = "[[3,1,2],[3,2,1]]",
                reason = "obsolete module bug: it sorted the input in place (sortByDescending on the original); the sorted* family leaves the input untouched as documented (appendix E: keep the Kotlin behavior)",
            ),
            DifferentialCases.KnownDifference(
                "typeof Arrayx",
                js = "\"function\"",
                kt = "\"object\"",
                reason = "the obsolete module was Array.bind(Array) with members assigned; the d.ts interface and the documentation declare an object (appendix E: keep the Kotlin behavior)",
            ),
        )

    }

}
