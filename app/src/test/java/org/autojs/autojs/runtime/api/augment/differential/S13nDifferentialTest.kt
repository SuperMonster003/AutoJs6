package org.autojs.autojs.runtime.api.augment.differential

import org.autojs.autojs.runtime.api.augment.s13n.S13n
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Obsolete `__s13n__.js` against the Kotlin [S13n] augmentable (roadmap P0.5). Points are built on the JVM fake of
 * `android.graphics.Point`. `color` needs `android.graphics.Color` (no JVM fake) on both sides, and the Kotlin module
 * rewraps that failure into its own message, so only the paths that fail before the conversion are listed here; the
 * conversion itself is checked on a device.
 */
class S13nDifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsS13n: Any
    private lateinit var ktS13n: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        jsS13n = ObsoleteModuleHost(rhino.context, rhino.jsScope).loadAndDefine("s13n")!!
        ktS13n = S13n.augmentWithRuntime(rhino.ktScope.globalThis, rhino.ktRuntime)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheKotlinModule() {
        val report = SurfaceDiff.compare(SurfaceDiff.surface(jsS13n, rhino.jsScope.globalThis), SurfaceDiff.surface(ktS13n, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        assertTrue("Obsolete s13n members missing from the Kotlin module: ${report.onlyInFirst}", report.onlyInFirst.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, tolerateErrorTypes = true)
    }

    companion object {

        /** Renders a returned android.graphics.Point (a Java object) as `[x, y]`. */
        private fun point(call: String) = "(function (p) { return [p.x, p.y]; })(s13n.point($call))"

        /** Renders a returned java.lang.Throwable as `[class name, message]`. */
        private fun throwable(call: String) = "(function (t) { return [String(t.getClass().getName()), String(t.getMessage())]; })(s13n.throwable($call))"

        private val EXPRESSIONS = listOf(
            // The documentation examples.
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
            "s13n.time(60, java.util.concurrent.TimeUnit.SECONDS, java.util.concurrent.TimeUnit.MINUTES)",
            "s13n.time(NaN)",
            "s13n.time('1000')",
            "s13n.time(' 1 000 ')",
            "s13n.time('1000', 'ms', 's')",
            "s13n.time('abc')",
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
            "s13n.time()",
            "s13n.time(1, 'ms', 's', 'extra')",
            point("1, 2"),
            point("1"),
            point("1.6, 2.4"),
            point("-1.6, -2.4"),
            point("1, 'x'"),
            point("1, null"),
            point("1, undefined"),
            point("1, '3'"),
            point("[3, 4]"),
            point("[3.7, '4']"),
            point("[3]"),
            point("{ x: 5, y: 6 }"),
            point("{ x: '5', y: 6.5 }"),
            point("{ x: 'a', y: 1 }"),
            point("{ x: 5 }"),
            point("new android.graphics.Point(7, 8)"),
            "(function (p) { return s13n.point(p) === p; })(new android.graphics.Point(7, 8))",
            point("new org.opencv.core.Point(1.5, 2.5)"),
            point("'1'"),
            point("null"),
            point(""),
            throwable("'boom'"),
            throwable("new java.lang.IllegalStateException('bad state')"),
            "(function (t) { return s13n.throwable(t) === t; })(new java.lang.IllegalStateException('x'))",
            throwable("new Error('js error')"),
            throwable("new TypeError('type error')"),
            "(function () { try { java.lang.Integer.parseInt('x'); } catch (e) { return String(s13n.throwable(e).getClass().getName()); } })()",
            throwable("123"),
            throwable("null"),
            throwable("{ message: 'm' }"),
            throwable(""),
            "s13n.color(null)",
            "s13n.color(true)",
            "s13n.color({})",
            "s13n.color()",
        )

        /** Differences between the obsolete module and the current Kotlin module, see ROADMAP appendix E. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference(
                "s13n.time(60, java.util.concurrent.TimeUnit.SECONDS, java.util.concurrent.TimeUnit.MINUTES)",
                js = "1",
                kt = "throws ClassCastException",
                reason = "documented: fromUnit / toUnit accept java.util.concurrent.TimeUnit; Kotlin takes the TimeUnit for an options object " +
                        "(isJsObject) and fails to cast it (P2.2)",
            ),
            DifferentialCases.KnownDifference(
                "s13n.time(null)",
                js = "0",
                kt = "throws IllegalArgumentException",
                reason = "documented sources are number / string / Date; the obsolete module turned null into 0 through Number(null), Kotlin rejects it; keep Kotlin",
            ),
            DifferentialCases.KnownDifference(
                "s13n.time(1, 'ms', 's', 'extra')",
                js = "0.001",
                kt = "throws MessageLookup",
                reason = "surplus arguments: ignored by the obsolete module, rejected by Kotlin (appendix C Q7)",
            ),
            DifferentialCases.KnownDifference(
                point("1, null"),
                js = "[1,0]",
                kt = "[1,1]",
                reason = "documented: a single number is used for both axes when y is omitted; Kotlin treats a nullish y as omitted, " +
                        "the obsolete module used Number(null) = 0; keep Kotlin",
            ),
        )

    }

}
