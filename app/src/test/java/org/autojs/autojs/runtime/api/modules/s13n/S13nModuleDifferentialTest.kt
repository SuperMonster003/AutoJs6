package org.autojs.autojs.runtime.api.modules.s13n

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
 * Obsolete `__s13n__.js` against `s13n` of the new script module layer (roadmap P2.2 / P2.4), mounted the way
 * `ScriptRuntime.augment()` does with the switch on. Error types are compared (D10). `s13n.color` needs
 * `android.graphics.Color` on both sides, so only the paths that fail before the conversion are compared here (appendix
 * E); `PureLogicModulesTest` exports the conversions for the device. `bytes` (6.7.0) has no obsolete counterpart.
 */
class S13nModuleDifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsS13n: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        if (ScriptModuleExpectations.RECORDING) jsS13n = ObsoleteModuleHost(rhino.context, rhino.jsScope).loadAndDefine("s13n")!!
        rhino.mountNewModules("s13n")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val ktS13n = rhino.evalKt("s13n")
        val report = SurfaceDiff.compare(ScriptModuleExpectations.obsoleteSurface("surface") { SurfaceDiff.surface(jsS13n, rhino.jsScope.globalThis) }, SurfaceDiff.surface(ktS13n, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        assertTrue("Obsolete s13n members missing from the new module: ${report.onlyInFirst}", report.onlyInFirst.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {

        /** Renders a returned android.graphics.Point (a Java object) as `[x, y]`. */
        private fun point(call: String) = "(function (p) { return [p.x, p.y]; })(s13n.point($call))"

        /** Renders a returned java.lang.Throwable as `[class name, message]`. */
        private fun throwable(call: String) = "(function (t) { return [String(t.getClass().getName()), String(t.getMessage())]; })(s13n.throwable($call))"

        private val EXPRESSIONS = listOf(
            "s13n.time(2, 'seconds')",
            "s13n.time(120000, { fromUnit: 'ms', toUnit: 'minutes' })",
            "s13n.time(1000)",
            "s13n.time(1500, 'ms', 's')",
            "s13n.time(1500.7, 'ms', 's')",
            "s13n.time(2, 'm', 's')",
            "s13n.time(1, 'h', 'MINUTES')",
            "s13n.time(1, 'd', 'hrs')",
            "s13n.time(3, 'SECS', 'micro')",
            "s13n.time(5, 'nanos', 'NANOSECONDS')",
            "s13n.time(1, 'x')",
            "s13n.time(1, 's', 'x')",
            "s13n.time(1, null, 's')",
            "s13n.time(1000, { fromUnit: 'ms', toUnit: 's' })",
            "s13n.time(1000, { toUnit: 's' })",
            "s13n.time(1000, {})",
            "s13n.time(1000, { fromUnit: 's' }, 'ignored')",
            "s13n.time(60, java.util.concurrent.TimeUnit.SECONDS, java.util.concurrent.TimeUnit.MINUTES)",
            "s13n.time(1, java.util.concurrent.TimeUnit.DAYS, 'h')",
            "s13n.time(90, 's', java.util.concurrent.TimeUnit.MINUTES)",
            "s13n.time(1, { fromUnit: java.util.concurrent.TimeUnit.HOURS, toUnit: 'm' })",
            "s13n.time(NaN)",
            "s13n.time('1000')",
            "s13n.time(' 1 000 ')",
            "s13n.time('1000', 'ms', 's')",
            "s13n.time('abc')",
            "s13n.time('')",
            "s13n.time('12:30')",
            "s13n.time('1 2 3 4 5 6 7')",
            "s13n.time('2024/01/02 03:04:05') === new Date('2024/01/02 03:04:05').getTime()",
            "s13n.time('2024-01-02 03:04:05', 'ms', 's') === new Date('2024/01/02 03:04:05').getTime() / 1000",
            "s13n.time(new Date(0))",
            "s13n.time(new Date(90000), 'ms', 'm')",
            "s13n.time(undefined)",
            "s13n.time(null)",
            "s13n.time(true)",
            "s13n.time([1])",
            "s13n.time({})",
            "s13n.time()",
            "s13n.time(1, 'ms', 's', 'extra')",
            point("1, 2"),
            point("1"),
            point("1.6, 2.4"),
            point("-1.6, -2.4"),
            point("1, 2.5"),
            point("1.5, -2.7"),
            point("1, 'x'"),
            point("1, null"),
            point("1, undefined"),
            point("1, '3'"),
            point("[3, 4]"),
            point("[3.7, '4']"),
            point("[3]"),
            point("[]"),
            point("{ x: 5, y: 6 }"),
            point("{ x: '5', y: 6.5 }"),
            point("{ x: 'a', y: 1 }"),
            point("{ x: 5 }"),
            point("new android.graphics.Point(7, 8)"),
            "(function (p) { return s13n.point(p) === p; })(new android.graphics.Point(7, 8))",
            point("new org.opencv.core.Point(1.5, 2.5)"),
            point("'1'"),
            point("null"),
            point("true"),
            point(""),
            throwable("'boom'"),
            throwable("new java.lang.IllegalStateException('bad state')"),
            "(function (t) { return s13n.throwable(t) === t; })(new java.lang.IllegalStateException('x'))",
            throwable("new Error('js error')"),
            throwable("new TypeError('type error')"),
            throwable("Error()"),
            "(function () { try { java.lang.Integer.parseInt('x'); } catch (e) { return String(s13n.throwable(e).getClass().getName()); } })()",
            "(function () { try { undefined.x; } catch (e) { return String(s13n.throwable(e).getClass().getName()); } })()",
            "(function (s) { return String(s13n.throwable(s + 'x').getMessage()); })('a')",
            throwable("123"),
            throwable("null"),
            throwable("{ message: 'm' }"),
            throwable(""),
            "s13n.color(null)",
            "s13n.color(true)",
            "s13n.color({})",
            "s13n.color([])",
            "s13n.color()",
            "typeof s13n",
            "typeof s13n.time",
        )

        private const val D10_REASON = "D10: the obsolete module failed inside Rhino's Java bridge (no android.graphics.Point constructor for " +
                "undefined coordinates, no unambiguous util.getClassName overload for null), an InternalError; the new layer reports the argument " +
                "as a TypeError"

        /** Differences kept from the current Kotlin module (ROADMAP appendix E), plus D10 and appendix C Q7. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference(point("[3]"), js = "throws InternalError", kt = "throws TypeError", reason = D10_REASON),
            DifferentialCases.KnownDifference(point("[]"), js = "throws InternalError", kt = "throws TypeError", reason = D10_REASON),
            DifferentialCases.KnownDifference(throwable("null"), js = "throws InternalError", kt = "throws TypeError", reason = D10_REASON),
            DifferentialCases.KnownDifference(
                "s13n.time(null)",
                js = "0",
                kt = "throws TypeError",
                reason = "documented sources are number / string / Date; the obsolete module turned null into 0 through Number(null) " +
                        "(appendix E: keep the Kotlin error, a TypeError by D10)",
            ),
            DifferentialCases.KnownDifference(
                "s13n.time(1, 'ms', 's', 'extra')",
                js = "0.001",
                kt = "throws TypeError",
                reason = "surplus arguments: ignored by the obsolete module, a TypeError on the new layer (appendix C Q7)",
            ),
            DifferentialCases.KnownDifference(
                point("1, null"),
                js = "[1,0]",
                kt = "[1,1]",
                reason = "documented: a single number is used for both axes when y is omitted; a nullish y counts as omitted, the obsolete module " +
                        "used Number(null) = 0 (appendix E: keep the Kotlin behavior)",
            ),
        )

    }

}
