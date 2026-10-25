package org.autojs.autojs.runtime.api.modules.recorder

import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.autojs.autojs.runtime.api.modules.differential.DifferentialCases
import org.autojs.autojs.runtime.api.modules.differential.DifferentialCases.KnownDifference
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.differential.SurfaceDiff
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.autojs.autojs.runtime.api.Recorder as ApiRecorder

/**
 * Obsolete `__recorder__.js` against `recorder` of the new script module layer (roadmap P3.5). The obsolete module was
 * a callable object with its own `save` / `load` / ... members; the current module and the new layer are callable with
 * the Java object `runtime.recorder` as prototype, whose methods implement the documented rules. The results are
 * compared on the JVM (time values only through relations or fixed timestamps), the differences left are the
 * documented ones (appendix E, P3.5); the surface and the same expressions are also compared with the current
 * `augment/recorder/Recorder`.
 *
 * zh-CN: 旧 __recorder__.js 对新层 recorder (P3.5). 旧模块是自带 save / load 等成员的可调用对象; 现有模块与新层可调用, 以 Java 对象 runtime.recorder 为原型,
 * 由其方法实现文档所述的规则. 在 JVM 上比较结果 (时间值只经比较关系或固定时间戳), 剩下的差异都是文档所述的 (附录 E, P3.5); 表面与同一批表达式另与现有
 * augment/recorder/Recorder 比较.
 */
class RecorderModuleDifferentialTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        prepare(rhino)
        rhino.mountNewModules("recorder")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun prepare(side: DifferentialRhino) = HostModuleTestSupport.setField(side.ktRuntime, "recorder", ApiRecorder(side.ktRuntime))

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val report = SurfaceDiff.compare(ScriptModuleExpectations.obsoleteSurface("surface"), SurfaceDiff.surface(rhino.evalKt("recorder"), rhino.ktScope.globalThis))
        assertTrue(SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())
    }

    @Test
    fun surfaceMatchesTheCurrentModule() {
        HostModuleTestSupport.assertSameSurface(rhino, "recorder")
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN, exportDir = "build/script-modules-device", exportAll = true)
        // The current module refused the surplus arguments too, with a message it cannot build on the JVM.
        HostModuleTestSupport.assertSameResults(rhino, EXPRESSIONS, mapOf("recorder(1, 2, 3) >= 0" to ("throws TypeError" to "appendix C Q7")))
    }

    companion object {

        private val EXPRESSIONS = listOf(
            "typeof recorder",
            "[typeof recorder.save, typeof recorder.load, typeof recorder.isLessThan, typeof recorder.isGreaterThan, typeof recorder.has, typeof recorder.remove, typeof recorder.clear]",
            "(recorder.clear(), recorder.save('a', 1000), recorder.load('a', 1500))",
            "(recorder.clear(), recorder.save('a', 1000))",
            "(recorder.clear(), recorder.save('a', 1000), [recorder.has('a'), recorder.has('nope')])",
            "(recorder.clear(), recorder.save('r', 1), recorder.remove('r'))",
            "(recorder.clear(), recorder.remove('absent'))",
            "(recorder.clear(), recorder.save('c', 1), recorder.clear(), recorder.has('c'))",
            "(recorder.clear(), recorder.load('missing'))",
            "(recorder.clear(), recorder.save(undefined, 100), recorder.load(undefined, 150))",
            "(recorder.clear(), recorder.save(null, 100), recorder.load(null, 160))",
            "(recorder.clear(), recorder.load())",
            "(recorder.clear(), recorder.save('x', 1000), [recorder.isLessThan('x', 1e15), recorder.isGreaterThan('x', 0), recorder.isGreaterThan('x', 1e15)])",
            "(recorder.clear(), [typeof recorder('k'), recorder.has('k')])",
            "(recorder.clear(), recorder('k') > 1e12)",
            "(recorder.clear(), recorder('k'), recorder('k') < 1000)",
            "(recorder.clear(), recorder(), recorder() < 1000)",
            "recorder(function () {}) >= 0",
            "(function () { var n = 0; recorder(function () { n++; }); return n; })()",
            "(function () { var self; recorder(function () { self = this; }, { v: 7 }); return self.v; })()",
            "(function () { var name; recorder(function named() { }); return recorder.has('named'); })()",
            "(recorder.clear(), recorder('t', 5000), recorder('t', 9000) < 1000)",
            "recorder(1, 2, 3) >= 0",
            "(recorder.clear(), recorder.save('a', 'x') > 0)",
        )

        private val KNOWN = listOf(
            KnownDifference(
                "(recorder.clear(), recorder.remove('absent'))", js = "true", kt = "false",
                reason = "Documented: remove returns whether a named record was removed; the obsolete JS returned the result of delete (always true)",
            ),
            KnownDifference(
                "(recorder.clear(), recorder.load('missing'))", js = "throws Error", kt = "throws JavaException NoSuchElementException",
                reason = "A missing named record fails in the Java method of runtime.recorder (current behavior, D10 keeps the Java type)",
            ),
            KnownDifference(
                "(recorder.clear(), recorder('t', 5000), recorder('t', 9000) < 1000)", js = "false", kt = "true",
                reason = "Documented: the timestamp argument of a named record is accepted and not used (the current rule); the obsolete JS saved and loaded with it",
            ),
            KnownDifference(
                "recorder(1, 2, 3) >= 0", js = "true", kt = "throws TypeError",
                reason = "Surplus arguments are a TypeError (appendix C Q7; the current module already refused them)",
            ),
            KnownDifference(
                "(recorder.clear(), recorder.save('a', 'x') > 0)", js = "true", kt = "throws JavaException IllegalArgumentException",
                reason = "Documented: the timestamp is a number, null or undefined; the Java method refuses other values (current behavior), the obsolete JS used the current time",
            ),
        )

    }

}
