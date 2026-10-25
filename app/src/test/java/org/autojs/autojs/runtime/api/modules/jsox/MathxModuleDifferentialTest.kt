package org.autojs.autojs.runtime.api.modules.jsox

import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.autojs.autojs.runtime.api.modules.differential.DifferentialCases
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.differential.SurfaceDiff
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Obsolete `__Mathx__.js` against `Mathx` of the new script module layer (roadmap P2.1 / P2.4). The obsolete module
 * reads `Arrayx` and `Numberx` from the global object, so those are loaded first on both sides. Error types are compared
 * (D10); the only differences left are the ones ROADMAP appendix E keeps from the current Kotlin module or decides by
 * the documentation.
 */
class MathxModuleDifferentialTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        rhino.mountNewModules("Arrayx", "Numberx", "Mathx")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val report = SurfaceDiff.compare(ScriptModuleExpectations.obsoleteSurface("surface"), SurfaceDiff.surface(rhino.evalKt("Mathx"), rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        assertTrue("Obsolete Mathx members missing from the new module: ${report.onlyInFirst}", report.onlyInFirst.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {

        private val EXPRESSIONS = listOf(
            "Mathx.sum([1, 2, 3])",
            "Mathx.sum(1, 2, 3)",
            "Mathx.sum([1.111, 2.222], 1)",
            "Mathx.sum([0.1, 0.2, 0.309], 2)",
            "Mathx.sum([0.1, 0.2, 0.309], 0)",
            "Mathx.sum([[1, 2], [3]])",
            "Mathx.sum(1, [2, [3, [4]], 5])",
            "Mathx.sum([])",
            "Mathx.sum()",
            "Mathx.sum([1, NaN])",
            "Mathx.sum(7, 8, 9, NaN)",
            "Mathx.sum(['1', '2'])",
            "Mathx.sum({ valueOf: () => 11 }, { valueOf: () => 12 })",
            "Mathx.sum(1, null)",
            "Mathx.sum([1, , 2])",
            "Mathx.sum([1], [2])",
            "Mathx.sum([1.5, 2], -1)",
            "Mathx.avg([1, 2, 3, 4])",
            "Mathx.avg([1, 2], 3)",
            "Mathx.avg([0.1, 0.2, 0.309], 2)",
            "Mathx.avg(1, [2], [[3, 4], 5])",
            "Mathx.avg([])",
            "Mathx.avg()",
            "Mathx.median([5, 1, 3])",
            "Mathx.median([4, 1, 3, 2])",
            "Mathx.median([10, 9, 100])",
            "Mathx.median(5, 3, 1, 4)",
            "Mathx.median([0.1, 0.2, 0.309], 2)",
            "Mathx.median([])",
            "Mathx.median()",
            "Mathx.median(7, 8, 9, NaN)",
            "Mathx.var([1, 2, 3, 4])",
            "Mathx.var([1, 2, 3, 4], 2)",
            "Mathx.var(97, 98, 98, 99, 100, 101, 103, 104)",
            "Mathx.var([97, 98, 98, 99, 100, 101, 103, 104], 0)",
            "Mathx.var()",
            "Mathx.std([1, 2, 3, 4], 3)",
            "Mathx.std([1, 2, 3, 4, 5], 1)",
            "Mathx.std([5])",
            "Mathx.std()",
            "Mathx.cv([1, 2, 3, 4], 3)",
            "Mathx.cv(22, 985, 3654, 98474, 698)",
            "Mathx.cv([22, 985, 3654, 98474, 698], 2)",
            "Mathx.cv(1, 1)",
            "Mathx.cv([5])",
            "Mathx.max([1, 5, 3])",
            "Mathx.max(1, 5, 3)",
            "Mathx.max([1.26, 5.55], 1)",
            "Mathx.max([0.1, 0.2, 0.309], 2)",
            "Mathx.max(1, [[[10], 20], 83], 20, [-9], [27, 72])",
            "Mathx.max(3, '2', [1], 'false')",
            "Mathx.max([3], '5', 1, false)",
            "Mathx.max([])",
            "Mathx.max()",
            "Mathx.min([1, 5, 3])",
            "Mathx.min([0.123, 0.234], 1)",
            "Mathx.min('3', [2], 1, false)",
            "Mathx.min([])",
            "Mathx.min()",
            "Mathx.dist()",
            "Mathx.dist([0, 0], [3, 4])",
            "Mathx.dist({ x: 0, y: 0 }, { x: 1, y: 1 }, 2)",
            "Mathx.dist({ x: 6, y: 7 }, { x: 12, y: 15 })",
            "Mathx.dist([0, 0], [1, 1])",
            "Mathx.dist([1, 2, 3], [0, 0])",
            "Mathx.dist(new android.graphics.Rect(6, 7, 12, 15))",
            "Mathx.dist(new android.graphics.Rect(0, 0, 1, 1), 3)",
            "Mathx.dist(new android.graphics.Rect(0, 0, 10, 10), new android.graphics.Rect(10, 10, 20, 20))",
            "Mathx.dist(new android.graphics.Rect(0, 0, 10, 10), new android.graphics.Rect(10, 10, 20, 20), 3)",
            "Mathx.dist([0, 0], new android.graphics.Rect(0, 0, 6, 8))",
            "Mathx.dist([0, 0], new org.opencv.core.Point(3, 4))",
            "Mathx.logMn(2, 8)",
            "Mathx.logMn(10, 1000)",
            "Mathx.logMn(2, 10, 3)",
            "Mathx.logMn(2, -1)",
            "Mathx.logMn(81, 9)",
            "Mathx.floorLog(2, 10)",
            "Mathx.floorLog(2, 1500)",
            "Mathx.ceilLog(2, 10)",
            "Mathx.roundLog(2, 10)",
            "Mathx.roundLog(2, -1)",
            "Mathx.roundLog(1, 2)",
            "Mathx.floorPow(2, 10)",
            "Mathx.ceilPow(2, 10)",
            "Mathx.roundPow(2, 10)",
            "Mathx.roundPow(2, 1500)",
            "typeof Mathx.randInt(1, 10)",
            "Mathx.randInt(5, 5)",
            "Mathx.randInt(15, 15, 15)",
            "Mathx.randInt([7, 7])",
            "Mathx.randInt(0)",
            "Mathx.randInt(1, NaN)",
            "Mathx.randInt(1.2, 1.8)",
            "(function () { var r = Mathx.randInt(1, 3); return r >= 1 && r <= 3 && r === Math.floor(r); })()",
            "(function () { var r = Mathx.randInt(-3); return r >= -3 && r <= 0 && r === Math.floor(r); })()",
            "(function () { var r = Mathx.randInt(1.8, 3.3); return r >= 2 && r <= 3; })()",
            "(function () { var r = Mathx.randInt([20, 10, 13, 11, 15]); return r >= 10 && r <= 20; })()",
            "(function () { var r = Mathx.randInt([]); return Number.isSafeInteger(r); })()",
            "Mathx.randInt('x')",
            "typeof Mathx",
        )

        private const val FRACTION_DOCS = "the documentation describes these calls as the numbers overload (all arguments flattened); the obsolete module took the second argument as the fraction whenever the first was an array (d.ts shows the obsolete reading, to sync)"

        /** Differences kept from the current Kotlin module or decided by the documentation, see ROADMAP appendix E. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference(
                "Mathx.sum([1], [2])",
                js = "1",
                kt = "3",
                reason = FRACTION_DOCS,
            ),
            DifferentialCases.KnownDifference(
                "Mathx.max([3], '5', 1, false)",
                js = "3",
                kt = "5",
                reason = FRACTION_DOCS,
            ),
            DifferentialCases.KnownDifference(
                "Mathx.dist([1, 2, 3], [0, 0])",
                js = "throws Error",
                kt = "throws TypeError",
                reason = "an argument error is a TypeError (D10); the obsolete module threw a plain Error",
            ),
            DifferentialCases.KnownDifference(
                "Mathx.logMn(10, 1000)",
                js = "2.9999999999999996",
                kt = "3",
                reason = "obsolete module bug: it only rounded when fraction was -1 (inverted condition); the documentation describes the default 13-digit rounding (appendix E: keep the Kotlin behavior)",
            ),
            DifferentialCases.KnownDifference(
                "Mathx.logMn(2, 10, 3)",
                js = "3.3219280948873626",
                kt = "3.322",
                reason = "same inverted fraction condition in the obsolete module; the module follows the documentation (appendix E: keep the Kotlin behavior)",
            ),
            DifferentialCases.KnownDifference(
                "Mathx.randInt(1.2, 1.8)",
                js = "2",
                kt = "throws RangeError",
                reason = "obsolete module bug: no integer lies within [1.2, 1.8] and it returned 2, outside the range; the module reports the empty range (current Kotlin behavior)",
            ),
        )

    }

}
