package org.autojs.autojs.runtime.api.modules.memory

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino.Side
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.runtime.memory.ExitKind
import org.autojs.autojs.runtime.memory.MemoryClock
import org.autojs.autojs.runtime.memory.MemoryDiagnosticsCore
import org.autojs.autojs.runtime.memory.MemoryProbes
import org.autojs.autojs.runtime.memory.MemorySnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mozilla.javascript.NativeObject
import java.io.File
import java.lang.reflect.Modifier

/**
 * JVM tests of the `memory` module (docs/memory/ROADMAP.md P5.1 / T9): the shape of every result (the `bridgeSessions` of a snapshot
 * included, docs/memory-phase2/ROADMAP.md P4.4), argument checks, the refusal of `memory.dumpHeap` in release builds while "Allow scripts
 * to export hprof" is off (appendix C Q8), and `memory.watch` doing nothing without LeakCanary. The module runs in real Rhino on a fake
 * platform over a [MemoryDiagnosticsCore] with fake probes and clock.
 * zh-CN: `memory` 模块的 JVM 测试 (docs/memory/ROADMAP.md P5.1 / T9): 各返回值的形状 (含快照的 `bridgeSessions`, docs/memory-phase2/ROADMAP.md P4.4),
 * 参数校验, release 构建在 "允许脚本导出 hprof" 关闭时拒绝 `memory.dumpHeap` (附录 C Q8), 以及没有 LeakCanary 时 `memory.watch` 什么也不做.
 * 模块运行在真实 Rhino 中, 平台为替身, 诊断核心使用替身探针与时钟.
 */
class MemoryModuleTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var rhino: DifferentialRhino
    private lateinit var clock: FakeClock
    private lateinit var probes: FakeProbes
    private lateinit var core: MemoryDiagnosticsCore
    private lateinit var platform: FakePlatform

    // Runtimes the tests keep reachable. zh-CN: 测试中保持可达的 runtime.
    private val held = mutableListOf<Any>()

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        clock = FakeClock(now = 5_000L)
        probes = FakeProbes()
        core = MemoryDiagnosticsCore(probes, clock)
        platform = FakePlatform(core, clock, temp.newFolder("cwd"))
        Memory(rhino.ktRuntime, platform).augment(rhino.ktScope.globalThis)
    }

    @After
    fun tearDown() = rhino.close()

    @Test
    fun moduleIsAGlobalObjectWithTheFunctionsOfT9() {
        assertEquals("\"object\"", eval("typeof memory"))
        assertEquals("true", eval("memory === \$memory"))
        assertEquals("[\"collect\",\"dumpHeap\",\"retained\",\"snapshot\",\"watch\"]", eval("Object.keys(memory).sort()"))
        assertEquals("true", eval("Object.keys(memory).every(function (k) { return typeof memory[k] === 'function'; })"))
    }

    @Test
    fun snapshotHasEveryFieldOfTheDataSourceAsNumbers() {
        val fields = MemorySnapshot::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.map { it.name }
        val expected = listOf(
            "javaHeapUsed", "javaHeapTotal", "javaHeapMax", "nativeHeapAllocated", "nativeHeapSize", "pss",
            "engines", "retainedRuntimes", "bridgeSessions", "images", "threads", "allThreads", "contexts", "timestamp",
        )
        // A field added to MemorySnapshot must reach the module (and its documentation) too.
        assertEquals(expected.sorted(), fields.sorted())
        assertEquals(json(expected), eval("Object.keys(memory.snapshot())"))
        // Every field is a number, but bridgeSessions: a number per engine.
        assertEquals("true", eval("(function (s) { return Object.keys(s).every(function (k) { return k === 'bridgeSessions' || typeof s[k] === 'number'; }); })(memory.snapshot())"))
        assertEquals(
            "{\"javaHeapUsed\":11,\"javaHeapTotal\":12,\"javaHeapMax\":13,\"nativeHeapAllocated\":21,\"nativeHeapSize\":22,\"pss\":31," +
                    "\"engines\":2,\"retainedRuntimes\":0,\"bridgeSessions\":{\"node\":0,\"bun\":0,\"quickjs\":0,\"lua\":0,\"python\":0}," +
                    "\"images\":5,\"threads\":1,\"allThreads\":2,\"contexts\":3,\"timestamp\":${EPOCH + 5_000L}}",
            eval("memory.snapshot()"),
        )
        assertEquals(0, probes.gcCalls)
        assertThrows("memory.snapshot(1)")
        assertThrows("memory.snapshot(undefined)")
    }

    @Test
    fun retainedDescribesFinishedRuntimesStillReachable() {
        assertEquals("[]", eval("memory.retained()"))
        val runtime = Any().also(held::add)
        core.track(runtime, 7, "leaky.js", "/sdcard/Scripts/leaky.js", ExitKind.STOPPED)
        clock.now += 1_234L
        assertEquals(
            "[{\"id\":7,\"name\":\"leaky.js\",\"source\":\"/sdcard/Scripts/leaky.js\",\"endedAt\":${EPOCH + 5_000L},\"elapsedMs\":1234,\"exitKind\":\"stopped\",\"engine\":\"rhino\"}]",
            eval("memory.retained()"),
        )
        assertEquals("1", eval("memory.snapshot().retainedRuntimes"))
        assertThrows("memory.retained(0)")
    }

    @Test
    fun bridgeSessionsCountTheFinishedExecutionsStillReachablePerEngine() {
        assertEquals("[\"node\",\"bun\",\"quickjs\",\"lua\",\"python\"]", eval("Object.keys(memory.snapshot().bridgeSessions)"))
        core.trackBridgeSession("lua", "lua-1", listOf(Any().also(held::add)))
        core.trackBridgeSession("lua", "lua-2", listOf(Any().also(held::add)))
        core.trackBridgeSession("python", "py-1", listOf(Any().also(held::add)))
        assertEquals("{\"node\":0,\"bun\":0,\"quickjs\":0,\"lua\":2,\"python\":1}", eval("memory.snapshot().bridgeSessions"))
        assertEquals("true", eval("(function (b) { return Object.keys(b).every(function (k) { return typeof b[k] === 'number'; }); })(memory.snapshot().bridgeSessions)"))
        assertEquals("0", eval("memory.snapshot().retainedRuntimes"))
        // collect() waits for held bridge sessions as for held runtimes.
        val before = clock.now
        assertEquals("2", eval("memory.collect(500).bridgeSessions.lua"))
        assertTrue(clock.now - before in 500L..500L + MemoryDiagnosticsCore.POLL_INTERVAL_MS)
    }

    @Test
    fun retainedNamesTheEngineOfARuntime() {
        core.track(Any().also(held::add), 1, "n.js", "n.js", ExitKind.NORMAL, engine = MemoryDiagnosticsCore.ENGINE_NODE)
        assertEquals("[\"node\"]", eval("memory.retained().map(function (r) { return r.engine; })"))
    }

    @Test
    fun exitKindsUseTheirScriptNames() {
        ExitKind.entries.forEach { kind -> core.track(Any().also(held::add), kind.ordinal, kind.id, kind.id, kind) }
        assertEquals("[\"normal\",\"error\",\"stopped\"]", eval("memory.retained().map(function (r) { return r.exitKind; })"))
    }

    @Test
    fun collectForcesGcAndReturnsANewSnapshot() {
        assertEquals("0", eval("memory.collect().retainedRuntimes"))
        // Nothing to wait for: one forced GC (3 rounds with finalization and a last GC), no waiting.
        assertEquals(MemoryDiagnosticsCore.GC_ROUNDS + 1, probes.gcCalls)
        assertEquals(5_000L, clock.now)
    }

    @Test
    fun collectWaitsUpToItsTimeoutForHeldRuntimes() {
        core.track(Any().also(held::add), 1, "held.js", "held.js", ExitKind.NORMAL)
        assertEquals("1", eval("memory.collect().retainedRuntimes"))
        assertTrue("default timeout 10 s, waited ${clock.now - 5_000L} ms", clock.now - 5_000L in 10_000L..10_000L + MemoryDiagnosticsCore.POLL_INTERVAL_MS)
        val before = clock.now
        assertEquals("1", eval("memory.collect(500).retainedRuntimes"))
        assertTrue(clock.now - before in 500L..500L + MemoryDiagnosticsCore.POLL_INTERVAL_MS)
        val zero = clock.now
        assertEquals("1", eval("memory.collect(0).retainedRuntimes"))
        assertEquals(zero, clock.now)
    }

    @Test
    fun collectChecksItsTimeout() {
        listOf("-1", "NaN", "Infinity", "60001", "'500'", "{}", "true", "0, 1").forEach { assertThrows("memory.collect($it)") }
        assertEquals("0", eval("memory.collect(60000).retainedRuntimes"))
        assertEquals("0", eval("memory.collect(null).retainedRuntimes"))
        assertEquals("0", eval("memory.collect(undefined).retainedRuntimes"))
        assertEquals("0", eval("memory.collect(12.5).retainedRuntimes"))
    }

    @Test
    fun collectStopsWhenTheScriptIsStopped() {
        core.track(Any().also(held::add), 1, "held.js", "held.js", ExitKind.NORMAL)
        platform.interrupted = true
        try {
            rhino.evalKt("memory.collect()")
            fail("memory.collect() must stop when the script is stopped")
        } catch (e: Throwable) {
            assertTrue("$e", ScriptInterruptedException.causedByInterrupt(e))
        }
        assertEquals(5_000L, clock.now)
    }

    @Test
    fun dumpHeapIsRefusedInReleaseBuildsWhileTheSwitchIsOff() {
        platform.isDebugBuild = false
        platform.heapDumpAllowed = false
        assertThrows("memory.dumpHeap()")
        assertThrows("memory.dumpHeap('heap.hprof')")
        assertEquals(emptyList<File>(), platform.dumps)
        // The switch alone decides: the same calls succeed once it is on. (The JVM cannot build the localized message,
        // error_memory_script_heap_dump_not_allowed; the device check prints it.)
        platform.heapDumpAllowed = true
        eval("memory.dumpHeap()", raw = true)
        eval("memory.dumpHeap('heap.hprof')", raw = true)
        assertEquals(2, platform.dumps.size)
    }

    @Test
    fun dumpHeapIsAllowedByTheSwitchInReleaseBuilds() {
        platform.isDebugBuild = false
        platform.heapDumpAllowed = true
        val path = rhino.evalKt("memory.dumpHeap()").toString()
        val file = File(path)
        assertEquals(platform.cwd.canonicalFile, file.parentFile.canonicalFile)
        assertTrue(file.name, Regex("""AutoJs6-heap-\d{8}-\d{6}\.hprof""").matches(file.name))
        assertEquals(Memory.heapDumpFileName(EPOCH + 5_000L), file.name)
        assertEquals(listOf(file), platform.dumps)
        assertTrue(file.isFile)
    }

    @Test
    fun dumpHeapIsAllowedInDebugBuildsAndResolvesPaths() {
        platform.isDebugBuild = true
        platform.heapDumpAllowed = false
        val defaultName = Memory.heapDumpFileName(EPOCH + 5_000L)
        assertEquals(File(platform.cwd, "sub/a.hprof").canonicalPath, File(eval("memory.dumpHeap('sub/a.hprof')", raw = true)).canonicalPath)
        assertEquals(File(platform.cwd, "dir/$defaultName").canonicalPath, File(eval("memory.dumpHeap('dir/')", raw = true)).canonicalPath)
        File(platform.cwd, "existing").mkdirs()
        assertEquals(File(platform.cwd, "existing/$defaultName").canonicalPath, File(eval("memory.dumpHeap('existing')", raw = true)).canonicalPath)
        val absolute = File(temp.root, "abs.hprof").absolutePath.replace('\\', '/')
        assertEquals(File(absolute).canonicalPath, File(eval("memory.dumpHeap('$absolute')", raw = true)).canonicalPath)
        assertEquals(4, platform.dumps.size)
    }

    @Test
    fun dumpHeapChecksItsPath() {
        platform.isDebugBuild = true
        listOf("''", "'   '", "123", "{}", "true", "'a.hprof', 1").forEach { assertThrows("memory.dumpHeap($it)") }
        assertEquals(emptyList<File>(), platform.dumps)
    }

    @Test
    fun watchDoesNothingWithoutLeakCanary() {
        platform.leakCanary = false
        assertEquals("false", eval("memory.watch({})"))
        assertEquals("false", eval("memory.watch(new java.lang.StringBuilder(), 'builder')"))
        assertEquals(0, platform.watched.size)
    }

    @Test
    fun watchHandsObjectsToLeakCanary() {
        platform.leakCanary = true
        assertEquals("true", eval("memory.watch({ a: 1 })"))
        assertEquals("true", eval("memory.watch(new java.lang.StringBuilder('x'), 'builder')"))
        assertEquals(2, platform.watched.size)
        val (first, firstDescription) = platform.watched[0]
        assertTrue(first.javaClass.name, first is NativeObject)
        assertEquals("memory.watch(NativeObject) in test.js", firstDescription)
        val (second, secondDescription) = platform.watched[1]
        // The Java object itself is watched, not its script wrapper.
        assertTrue(second.javaClass.name, second is StringBuilder)
        assertEquals("builder", secondDescription)
    }

    @Test
    fun watchChecksItsArguments() {
        platform.leakCanary = true
        listOf("", "null", "undefined", "1", "'text'", "true", "{}, 5", "{}, 'a', 1").forEach { assertThrows("memory.watch($it)") }
        assertEquals(0, platform.watched.size)
    }

    private fun eval(expression: String, raw: Boolean = false): String {
        if (raw) return rhino.evalKt(expression).toString()
        val rendered = rhino.render(Side.KT, expression)
        if (rendered.key.startsWith("throws")) fail("$expression: $rendered")
        return rendered.key
    }

    private fun assertThrows(expression: String) {
        val rendered = rhino.render(Side.KT, expression)
        assertTrue("$expression -> $rendered", rendered.key.startsWith("throws"))
        assertFalse(rendered.toString(), rendered.key.startsWith("throws(java)"))
    }

    private fun json(names: List<String>) = names.joinToString(",", "[", "]") { "\"$it\"" }

    private class FakeClock(var now: Long) : MemoryClock {
        override fun currentTimeMillis() = EPOCH + now
        override fun elapsedRealtime() = now
        override fun sleep(ms: Long) {
            now += ms
        }
    }

    private class FakeProbes : MemoryProbes {
        var gcCalls = 0
        override fun javaHeapUsed() = 11L
        override fun javaHeapTotal() = 12L
        override fun javaHeapMax() = 13L
        override fun nativeHeapAllocated() = 21L
        override fun nativeHeapSize() = 22L
        override fun pss() = 31L
        override fun engines() = 2
        override fun images() = 5
        override fun contexts() = 3
        override fun liveThreads() = listOf(Thread("ScriptThread-1"), Thread("main-like"))
        override fun gc() {
            gcCalls++
        }

        override fun runFinalization() = Unit
    }

    private class FakePlatform(private val core: MemoryDiagnosticsCore, private val clock: FakeClock, val cwd: File) : MemoryScriptPlatform {
        override val diagnostics get() = core
        override var isDebugBuild = false
        var heapDumpAllowed = false
        var leakCanary = false
        var interrupted = false
        val dumps = mutableListOf<File>()
        val watched = mutableListOf<Pair<Any, String>>()

        override fun isScriptHeapDumpAllowed() = heapDumpAllowed

        override fun currentTimeMillis() = clock.currentTimeMillis()

        override fun resolvePath(path: String) = File(path).takeIf { it.isAbsolute } ?: File(cwd, path)

        override fun dumpHeap(file: File) = file.also {
            it.parentFile?.mkdirs()
            it.writeText("hprof")
            dumps += it
        }

        override fun scriptName() = "test.js"

        override fun watch(obj: Any, description: String): Boolean {
            if (!leakCanary) return false
            watched += obj to description
            return true
        }

        override fun checkInterrupted() {
            if (interrupted) throw ScriptInterruptedException()
        }
    }

    private companion object {
        const val EPOCH = 1_760_000_000_000L
    }

}
