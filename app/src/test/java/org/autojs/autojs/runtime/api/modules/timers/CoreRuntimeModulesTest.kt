package org.autojs.autojs.runtime.api.modules.timers

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.continuation.Continuation
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.engines.Engines
import org.autojs.autojs.runtime.api.augment.threads.Threads
import org.autojs.autojs.runtime.api.augment.timers.Timers
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P3.4, first group: `timers`, `threads`, `continuation` and `engines` on the new script module layer. The surface
 * is compared with the current augment calls in the same JVM ([RuntimeModulesFixture]; `length` is left out, appendix C
 * Q9); the argument rules that fail before any host call run here, everything that needs the host (timers, threads,
 * engines) goes to `build/script-modules-device/CoreRuntimeModulesTest.json` for
 * `device_eval.py --new-modules on --strict-errors`. The obsolete JS of these modules only forwarded to the same host
 * objects; its differences are rows of appendix E (P3.4).
 *
 * zh-CN: P3.4 第一组: 新层上的 timers, threads, continuation 与 engines. 表面在同一 JVM 中与现有 augment 调用比较 (不含 length, 附录 C Q9); 在调用宿主之前
 * 就失败的参数规则在此检查, 需要宿主的行为 (定时器, 线程, 引擎) 写入设备用例. 这些模块的旧 JS 只是转发给同样的宿主对象, 其差异记入附录 E (P3.4).
 */
class CoreRuntimeModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = newSide(newLayer = true)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun check(cases: List<Pair<String, String>>) {
        RuntimeModulesFixture.check(rhino, cases)
        RuntimeModulesFixture.exportForDevice(EXPORT, cases)
    }

    @Test
    fun surfaceMatchesTheCurrentModules() {
        val actual = RuntimeModulesFixture.describe(rhino, GLOBALS, MODULES)
        val expected = newSide(newLayer = false).use { RuntimeModulesFixture.describe(it, GLOBALS, MODULES) }
        assertEquals(expected, actual)
    }

    /** Installing builds nothing: every name is a pending slot, and installing engines sets no execArgv without a config. */
    @Test
    fun installingBuildsNothing() {
        val global = rhino.ktScope.globalThis
        GLOBALS.forEach { assertTrue(it, LazyDataSlot.isPending(global, it)) }
    }

    /**
     * The host skips the engine events of a runtime whose engines module was never built (no listener can exist), and
     * emits them again once the script reached the module by any of its names.
     */
    @Test
    fun enginesPendingUntilUsed() {
        val side = DifferentialRhino()
        side.use {
            RuntimeModulesFixture.prepareHost(it.ktRuntime)
            val mounter = RuntimeModulesFixture.mount(it, MODULES, newLayer = true) { name, _, _ -> throw AssertionError(name) }
            RuntimeModulesFixture.setField(it.ktRuntime, ScriptRuntime::class.java, "scriptModules", mounter)
            assertTrue(it.ktRuntime.isScriptModulePending("engines"))
            it.evalKt("\$engines.listenerCount('start')")
            assertFalse(it.ktRuntime.isScriptModulePending("engines"))
            assertFalse(it.ktRuntime.isScriptModulePending("no-such-module"))
        }
    }

    @Test
    fun timersArguments() = check(
        listOf(
            "typeof timers.setTimeout" to "\"function\"",
            "Object.keys(timers).join()" to "\"setIntervalExt,keepAlive,addDailyTask,addWeeklyTask,addDisposableTask,addIntentTask,getTimedTask,getIntentTask,removeIntentTask,removeTimedTask,queryIntentTasks,queryTimedTasks\"",
            "timers.keepAlive === keepAlive" to "true",
            "setTimeout()" to "throws TypeError",
            "setTimeout(1)" to "throws TypeError",
            "setTimeout(null, 1)" to "throws TypeError",
            "setTimeout(function () {}, 'a')" to "throws TypeError",
            "setTimeout(function () {}, NaN)" to "throws RangeError",
            "setTimeout(function () {}, Infinity)" to "throws RangeError",
            "setInterval()" to "throws TypeError",
            "setInterval(function () {}, {})" to "throws TypeError",
            "setImmediate('f')" to "throws TypeError",
            "clearTimeout()" to "throws TypeError",
            "clearTimeout(null)" to "throws TypeError",
            "clearTimeout(1, 2)" to "throws TypeError",
            "clearInterval('x')" to "throws TypeError",
            "clearImmediate(NaN)" to "throws RangeError",
            "loop(1)" to "throws TypeError",
            "timers.setIntervalExt()" to "throws TypeError",
            "timers.setIntervalExt(1)" to "throws TypeError",
            "timers.setIntervalExt(function () {}, 'x')" to "throws TypeError",
            "timers.setIntervalExt(function () {}, NaN)" to "throws RangeError",
            "timers.setIntervalExt(function () {}, 10, 'x')" to "throws TypeError",
            "timers.setIntervalExt(function () {}, 1, 2, 3, 4)" to "throws TypeError",
            "keepAlive('x')" to "throws TypeError",
            "keepAlive(Infinity)" to "throws RangeError",
            "keepAlive(1, 2)" to "throws TypeError",
            "timers.addDailyTask(1)" to "throws TypeError",
            "timers.addDailyTask({}, {})" to "throws TypeError",
            "timers.getTimedTask()" to "throws TypeError",
            "timers.queryTimedTasks('x')" to "throws TypeError",
        )
    )

    @Test
    fun threadsArguments() = check(
        listOf(
            "Object.keys(threads).join()" to "\"interrupt,start,pool,disposable\"",
            "[typeof threads.currentThread, typeof threads.atomic, typeof threads.lock, typeof threads.shutDownAll]" to
                "[\"function\",\"function\",\"function\",\"function\"]",
            "threads.interrupt()" to "throws TypeError",
            "threads.interrupt(1)" to "undefined",
            "threads.interrupt(null)" to "undefined",
            "threads.interrupt(1, 2)" to "throws TypeError",
            "threads.start()" to "throws TypeError",
            "threads.start(null)" to "throws TypeError",
            "threads.start(1)" to "throws TypeError",
            "threads.start({})" to "throws TypeError",
            "threads.pool(1)" to "throws TypeError",
            "threads.pool({}, 1)" to "throws TypeError",
            "threads.pool({ corePoolSize: -1 })" to "throws RangeError",
            "threads.pool({ maxPoolSize: 'x' })" to "throws TypeError",
            "threads.pool({ maxPoolSize: 1.5e10 })" to "throws RangeError",
            "threads.pool({ keepAliveTime: -1 })" to "throws RangeError",
            "threads.disposable(1)" to "throws TypeError",
            "Object.keys(threads.disposable()).join()" to "\"blockedGet,blockedGetOrThrow,setAndNotify\"",
            "sync()" to "throws TypeError",
            "sync(1)" to "throws TypeError",
            "typeof sync(function () {})" to "\"function\"",
            "sync(function (a) { return a + 1; })(1)" to "2",
            "sync(function (a) { return a * 2; }, {})(21)" to "42",
            "sync(function () {}, {}, 1)" to "throws TypeError",
        )
    )

    @Test
    fun continuationArguments() = check(
        listOf(
            "Object.keys(continuation).join()" to "\"create,enabled\"",
            "[typeof continuation.await, typeof continuation.delay]" to "[\"function\",\"function\"]",
            "(function () { continuation = 1; return typeof continuation; })()" to "\"object\"",
            "continuation.create(1)" to "throws TypeError",
            "continuation.create({}, 1)" to "throws TypeError",
        )
    )

    @Test
    fun enginesArguments() = check(
        listOf(
            "Object.keys(engines).join()" to "\"all,myEngine,getEngines,stopAll,stopAllAndToast,execScript,execScriptFile,execAutoFile\"",
            "[typeof engines.on, typeof engines.emit, typeof engines.listenerCount]" to "[\"function\",\"function\",\"function\"]",
            "engines.all(1)" to "throws TypeError",
            "engines.myEngine(1)" to "throws TypeError",
            "engines.stopAll(1)" to "throws TypeError",
            "engines.execScript()" to "throws TypeError",
            "engines.execScript('a')" to "throws TypeError",
            "engines.execScript(null, 'b')" to "throws TypeError",
            "engines.execScript('a', 'b', 1)" to "throws TypeError",
            "engines.execScript('a', 'b', [])" to "throws TypeError",
            "engines.execScript('a', 'b', { delay: 'x' })" to "throws TypeError",
            "engines.execScript('a', 'b', { interval: NaN })" to "throws RangeError",
            "engines.execScript('a', 'b', { loopTimes: 1e10 })" to "throws RangeError",
            "engines.execScript('a', 'b', { arguments: 1 })" to "throws TypeError",
            "engines.execScript('a', 'b', {}, 1)" to "throws TypeError",
            "engines.execScriptFile()" to "throws TypeError",
            "engines.execScriptFile('a.js', 'x')" to "throws TypeError",
            "engines.execAutoFile(undefined)" to "throws TypeError",
        )
    )

    /** Behaviour that needs the host: written for the device only. */
    @Test
    fun deviceOnlyCases() = RuntimeModulesFixture.exportForDevice(
        EXPORT,
        listOf(
            "typeof setTimeout(function () {}, 10)" to "\"number\"",
            "clearTimeout(setTimeout(function () {}, 60000))" to "true",
            "clearInterval(setInterval(function () {}, 60000))" to "true",
            "clearImmediate(setImmediate(function () {}))" to "true",
            "clearTimeout(-12345)" to "false",
            "clearTimeout(setTimeout(function () {}, undefined, 1, 2))" to "true",
            "(function () { var d = threads.disposable(); threads.start(function () { setTimeout(function (a, b) { d.setAndNotify(a + b); }, 10, 1, 2); }); return d.blockedGet(500); })()" to "3",
            "(function () { var d = threads.disposable(); threads.start(function () { setImmediate(function (a) { d.setAndNotify(a); }, 'x'); }); return d.blockedGet(500); })()" to "\"x\"",
            "(function () { var d = threads.disposable(); threads.start(function () { var n = 0; timers.setIntervalExt(function () { n++; }, 10, 60, function (r) { d.setAndNotify(n > 0 && r === true); }); }); return d.blockedGet(1500); })()" to "true",
            "(function () { var d = threads.disposable(); threads.start(function () { var n = 0; timers.setIntervalExt(function () { n++; }, 10, function () { return n >= 3 ? 'done' : null; }, function (r) { d.setAndNotify(r + n); }); }); return d.blockedGet(1500); })()" to "\"done3\"",
            "(function () { var d = threads.disposable(); threads.start(function () { var n = 0; timers.setIntervalExt(function () { n++; }, null, null, function () {}); setTimeout(function () { d.setAndNotify(n); }, 500); }); var n = d.blockedGet(1500); return n >= 1 && n <= 3; })()" to "true",
            "(function () { var id = keepAlive(); return clearInterval(id); })()" to "true",
            "(function () { var id = timers.keepAlive(-5); return clearInterval(id); })()" to "true",
            "(function () { var id = keepAlive(20); return clearTimeout(id); })()" to "true",
            "loop()" to "undefined",
            "Array.isArray(timers.queryTimedTasks())" to "true",
            "Array.isArray(timers.queryIntentTasks({}))" to "true",
            "timers.getTimedTask(-1)" to "null",
            "timers.getIntentTask(-1)" to "null",
            "timers.removeTimedTask(-1)" to "null",
            "String(threads.start(function () {}).getClass().getSimpleName())" to "\"TimerThread\"",
            "threads.start(new java.lang.Runnable({ run: function () {} })) !== undefined" to "true",
            "(function () { var p = threads.pool(); var r = [p.getCorePoolSize(), p.getMaximumPoolSize()].join(); p.shutdown(); return r; })()" to "\"0,2147483647\"",
            "(function () { var p = threads.pool({ corePoolSize: 2, maxPoolSize: 3, keepAliveTime: 1000 }); var r = [p.getCorePoolSize(), p.getMaximumPoolSize(), p.getKeepAliveTime(java.util.concurrent.TimeUnit.MILLISECONDS)].join(); p.shutdown(); return r; })()" to "\"2,3,1000\"",
            "threads.pool({ corePoolSize: 3, maxPoolSize: 2 })" to "throws JavaException IllegalArgumentException",
            "(function () { var t = threads.start(function () { sleep(5000); }); sleep(50); threads.interrupt(t); t.join(1000); return t.isAlive(); })()" to "false",
            "Number(threads.atomic(5).get())" to "5",
            "(function () { var d = threads.disposable(); d.setAndNotify(7); return d.blockedGet(10); })()" to "7",
            "(function () { var t = threads.currentThread(); return typeof t.setTimeout; })()" to "\"function\"",
            "continuation.enabled" to "false",
            "[typeof continuation.create().resume, typeof continuation.create(globalThis).await]" to "[\"function\",\"function\"]",
            "engines.myEngine() !== null && typeof engines.myEngine().getId" to "\"function\"",
            "Array.isArray(engines.all()) && engines.all().length >= 1" to "true",
            "engines.getEngines().size() >= 1" to "true",
            "typeof engines.myEngine().execArgv" to "\"object\"",
            "engines.listenerCount('start')" to "0",
            "(function () { var d = threads.disposable(); engines.once('start', function (e) { d.setAndNotify(String(e.getSource().getName())); }); engines.execScript('aj6-p34-engines', 'void 0', { delay: 0, loopTimes: 1 }); return d.blockedGet(2000); })()" to "\"aj6-p34-engines\"",
        )
    )

    private fun newSide(newLayer: Boolean): DifferentialRhino = DifferentialRhino().also { side ->
        RuntimeModulesFixture.prepareHost(side.ktRuntime)
        RuntimeModulesFixture.mount(side, MODULES, newLayer) { name, global, runtime ->
            when (name) {
                "timers" -> Timers(runtime).augment(global, runtime.timers)
                "threads" -> Threads(runtime).augment(global, runtime.threads)
                "continuation" -> Continuation(runtime).augment(global, runtime.js_mod_continuation, additionalAttributes = ScriptableObject.READONLY)
                "engines" -> Engines(runtime).augment(global)
                else -> throw AssertionError(name)
            }
        }
    }

    private companion object {
        const val EXPORT = "CoreRuntimeModulesTest"
        val MODULES = listOf("timers", "threads", "continuation", "engines")
        val GLOBALS = listOf(
            "setTimeout", "setInterval", "setImmediate", "clearTimeout", "clearInterval", "clearImmediate", "loop", "keepAlive", "timers", "\$timers",
            "sync", "threads", "\$threads", "continuation", "\$continuation", "engines", "\$engines",
        )
    }

}
