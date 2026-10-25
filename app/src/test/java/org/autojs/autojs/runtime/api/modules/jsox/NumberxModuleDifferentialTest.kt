package org.autojs.autojs.runtime.api.modules.jsox

import org.autojs.autojs.runtime.api.augment.differential.DifferentialCases
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Obsolete `__Numberx__.js` against `Numberx` of the new script module layer (roadmap P2.1 / P2.4). Error types are
 * compared (D10); the only differences left are the ones ROADMAP appendix E keeps from the current Kotlin module.
 */
class NumberxModuleDifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsNumberx: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        if (ScriptModuleExpectations.RECORDING) jsNumberx = ObsoleteModuleHost(rhino.context, rhino.jsScope).loadAndDefine("Numberx")!!
        rhino.mountNewModules("Numberx")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val report = SurfaceDiff.compare(ScriptModuleExpectations.obsoleteSurface("surface") { SurfaceDiff.surface(jsNumberx, rhino.jsScope.globalThis) }, SurfaceDiff.surface(rhino.evalKt("Numberx"), rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        assertTrue("Obsolete Numberx members missing from the new module: ${report.onlyInFirst}", report.onlyInFirst.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {

        private val EXPRESSIONS = listOf(
            "Numberx.ICU",
            "Numberx.check()",
            "Numberx.check(1)",
            "Numberx.check('1')",
            "Numberx.check(1, 1)",
            "Numberx.check(1, 2)",
            "Numberx.check(9, 2 + 7)",
            "Numberx.check('9', '9')",
            "Numberx.check(NaN, NaN)",
            "Numberx.check(1, '<', 2, '<=', 2, '>=', 1)",
            "Numberx.check(0.5, '<', 2011, '>', 5, '<', 23, '>', 0.5)",
            "Numberx.check(3, '<', 2)",
            "Numberx.check(0.5, 23, 2011)",
            "Numberx.check(1, '==', 1)",
            "Numberx.check(1, '<', '2')",
            "Numberx.clamp(5, 1, 3)",
            "Numberx.clamp(5, [1, 3])",
            "Numberx.clamp(5, [3, 1])",
            "Numberx.clamp(-1, 0, 10)",
            "Numberx.clamp(5)",
            "Numberx.clamp(307, 523)",
            "Numberx.clamp(5, [10, 12, 30, 22, 20])",
            "Numberx.clamp(5, 'x', 3)",
            "Numberx.clamp(5, [null, 3])",
            "Numberx.clamp(10, [false, 'Hi', [-1], '7'])",
            "Numberx.clamp('5', 1, 3)",
            "Numberx.clampTo(12, [0, 10])",
            "Numberx.clampTo(-3, [0, 10], 5)",
            "Numberx.clampTo(5, [0, 10])",
            "Numberx.clampTo(5, [])",
            "Numberx.clampTo(5, [3, 3])",
            "Numberx.clampTo(390, [0, 360])",
            "Numberx.clampTo(30 - 10 * 360, [0, 360])",
            "Numberx.clampTo(372, [0, 360], 10)",
            "Numberx.clampTo(372, [0, 'x', 360])",
            "Numberx.toFixedNum(1.23456, 2)",
            "Numberx.toFixedNum(1.5, 0)",
            "Numberx.toFixedNum(123.456, 2)",
            "Numberx.toFixedNum(3.004, 2)",
            "Numberx.toFixedNum(1.23456e3)",
            "Numberx.toFixedNum(1.5, 101)",
            "Numberx.toFixedNum('1.5', 0)",
            "Numberx.padStart(7, 3)",
            "Numberx.padStart(7, 3, 'x')",
            "Numberx.padStart(5, 2, 0)",
            "Numberx.padStart(99, 5, 'AB')",
            "Numberx.padStart(7, 2.9)",
            "Numberx.padStart(1e21, 25)",
            "Numberx.padStart(123, 2)",
            "Numberx.padEnd(7, 3)",
            "Numberx.padEnd(7, 3, 'xy')",
            "Numberx.padEnd(99.1, 5)",
            "Numberx.parseFloat('1.5')",
            "Numberx.parseFloat('12.5px')",
            "Numberx.parseFloat('  -.5e2x')",
            "Numberx.parseFloat('1.1', 2)",
            "Numberx.parseFloat('ff.8', 16)",
            "Numberx.parseFloat('0.8', 16)",
            "Numberx.parseFloat('0.101', 2)",
            "Numberx.parseFloat(1.25, 2)",
            "Numberx.parsePercent('50%')",
            "Numberx.parsePercent('50%%')",
            "Numberx.parsePercent('2%%')",
            "Numberx.parsePercent('50.00%')",
            "Numberx.parsePercent(' 5 0 % ')",
            "Numberx.parsePercent('-12.5%')",
            "Numberx.parsePercent(0.3)",
            "Numberx.parsePercent(1e21)",
            "Numberx.parsePercent('abc')",
            "Numberx.parseRatio('1:4')",
            "Numberx.parseRatio('3 : 4')",
            "Numberx.parseRatio('3:0.1')",
            "Numberx.parseRatio('0.1:0.01')",
            "Numberx.parseRatio('3px:2')",
            "Numberx.parseRatio('x:4')",
            "Numberx.parseRatio('5')",
            "Numberx.parseAny(' 3:4 ')",
            "Numberx.parseAny('12%')",
            "Numberx.parseAny('20%%')",
            "Numberx.parseAny('42')",
            "Numberx.parseAny('0xFF')",
            "Numberx.parseAny(42)",
            "Numberx.parseAny(' ')",
            "Numberx.parseAny('abc')",
            "Numberx.ensureNumber(1, 2)",
            "Numberx.ensureNumber(NaN, 0, Infinity)",
            "Numberx.ensureNumber()",
            "Numberx.ensureNumber('1')",
            "Numberx.ensureNumber(null)",
            "typeof Numberx",
        )

        /** Differences kept from the current Kotlin module, see ROADMAP appendix E. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference(
                "Numberx.check(1, '==', 1)",
                js = "throws Error",
                kt = "true",
                reason = "the module also accepts the documented == operator; the obsolete module only knew <, <=, >, >= and = (appendix E: keep the Kotlin behavior)",
            ),
            DifferentialCases.KnownDifference(
                "Numberx.check(1, '<', '2')",
                js = "throws Error",
                kt = "true",
                reason = "numeric strings are accepted in comparisons; the obsolete module required numbers (appendix E: keep the Kotlin behavior)",
            ),
            DifferentialCases.KnownDifference(
                "Numberx.check(0.5, 23, 2011)",
                js = "throws Error",
                kt = "throws TypeError",
                reason = "an argument error is a TypeError (D10); the obsolete module threw a plain Error",
            ),
            DifferentialCases.KnownDifference(
                "Numberx.clamp(10, [false, 'Hi', [-1], '7'])",
                js = "\"7\"",
                kt = "7",
                reason = "the result is a number as documented (d.ts example: 7); the obsolete module returned the bound as it was given",
            ),
            DifferentialCases.KnownDifference(
                "Numberx.clamp('5', 1, 3)",
                js = "throws TypeError",
                kt = "3",
                reason = "a numeric string argument is accepted; the obsolete module required a number (appendix E: keep the Kotlin behavior)",
            ),
            DifferentialCases.KnownDifference(
                "Numberx.toFixedNum('1.5', 0)",
                js = "throws TypeError",
                kt = "2",
                reason = "a numeric string argument is accepted; the obsolete module required a number (appendix E: keep the Kotlin behavior)",
            ),
            DifferentialCases.KnownDifference(
                "Numberx.parseFloat(1.25, 2)",
                js = "throws TypeError",
                kt = "0.25",
                reason = "obsolete module bug: the in operator on a number; the number is converted to a string first (appendix E: keep the Kotlin behavior)",
            ),
            DifferentialCases.KnownDifference(
                "typeof Numberx",
                js = "\"function\"",
                kt = "\"object\"",
                reason = "the obsolete module was Number.bind(Number) with members assigned; the d.ts interface and the documentation declare an object (appendix E: keep the Kotlin behavior)",
            ),
        )

    }

}
