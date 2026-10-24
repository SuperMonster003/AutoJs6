package org.autojs.autojs.runtime.memory

import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests of the memory diagnostics core (docs/memory/ROADMAP.md P2.1 / P2.3): registration and dequeuing of finished runtimes,
 * the snapshot fields, the collect timeout and the unreclaimed runtime check; the bridge sessions of plugin engines and the engine
 * column (docs/memory-phase2/ROADMAP.md P4.4).
 * zh-CN: 内存诊断核心的 JVM 测试 (docs/memory/ROADMAP.md P2.1 / P2.3): 已结束 runtime 的登记与出列, 快照字段, collect 超时与未回收检查;
 * 插件引擎的桥接会话与引擎列 (docs/memory-phase2/ROADMAP.md P4.4).
 */
class MemoryDiagnosticsCoreTest {

    private class FakeClock(var now: Long = 0L) : MemoryClock {
        override fun currentTimeMillis() = EPOCH + now
        override fun elapsedRealtime() = now
        override fun sleep(ms: Long) {
            now += ms
        }
    }

    private class FakeProbes(private val realGc: Boolean = false) : MemoryProbes {
        var gcCalls = 0
        var finalizationCalls = 0
        var threads: List<Thread> = emptyList()
        var pssCalls = 0
        override fun javaHeapUsed() = 11L
        override fun javaHeapTotal() = 12L
        override fun javaHeapMax() = 13L
        override fun nativeHeapAllocated() = 21L
        override fun nativeHeapSize() = 22L
        override fun pss() = 31L.also { pssCalls++ }
        override fun engines() = 2
        override fun images() = 5
        override fun contexts() = 3
        override fun liveThreads() = threads
        override fun gc() {
            gcCalls++
            if (realGc) System.gc()
        }

        @Suppress("DEPRECATION")
        override fun runFinalization() {
            finalizationCalls++
            if (realGc) System.runFinalization()
        }
    }

    private object RealClock : MemoryClock {
        override fun currentTimeMillis() = System.currentTimeMillis()
        override fun elapsedRealtime() = System.nanoTime() / 1_000_000
        override fun sleep(ms: Long) = Thread.sleep(ms)
    }

    // Stand-ins for the session objects of T1 (a).
    private class FakeBroker
    private class FakeCallback

    @Test
    fun trackedRuntimeIsListedWithItsDescription() {
        val clock = FakeClock(now = 1_000L)
        val core = MemoryDiagnosticsCore(FakeProbes(), clock)
        val runtime = Any()
        core.track(runtime, id = 7, name = "demo.js", source = "/sdcard/Scripts/demo.js", exitKind = ExitKind.STOPPED)
        clock.now += 2_500L

        val retained = core.retained()
        assertEquals(1, retained.size)
        with(retained.single()) {
            assertEquals(7, id)
            assertEquals("demo.js", name)
            assertEquals("/sdcard/Scripts/demo.js", source)
            assertEquals(EPOCH + 1_000L, endedAt)
            assertEquals(2_500L, elapsedMs)
            assertEquals(ExitKind.STOPPED, exitKind)
            assertEquals(MemoryDiagnosticsCore.ENGINE_RHINO, engine)
        }
        assertEquals(1, core.retainedCount())
        assertNotNull(runtime)
    }

    @Test
    fun trackedNodeRuntimeCarriesItsEngine() {
        val core = MemoryDiagnosticsCore(FakeProbes(), FakeClock())
        val runtime = Any()
        core.track(runtime, id = 3, name = "n.js", source = "n.js", exitKind = ExitKind.NORMAL, engine = MemoryDiagnosticsCore.ENGINE_NODE)

        assertEquals(listOf(MemoryDiagnosticsCore.ENGINE_NODE), core.retained().map { it.engine })
        assertNotNull(runtime)
    }

    @Test
    fun reclaimedRuntimeLeavesTheRegistry() {
        val core = MemoryDiagnosticsCore(FakeProbes(realGc = true), RealClock)
        val kept = Any()
        core.track(kept, id = 1, name = "kept", source = "kept", exitKind = ExitKind.NORMAL)
        trackUnreachable(core)
        assertEquals(2, core.retainedCount())

        val elapsed = core.awaitCleared(10_000L) { core.retainedCount() == 1 }

        assertNotNull("the unreachable runtime was not reclaimed within 10 s", elapsed)
        assertEquals(listOf(1), core.retained().map { it.id })
        assertNotNull(kept)
    }

    // A separate frame, so that no local variable of the test keeps the runtime reachable.
    private fun trackUnreachable(core: MemoryDiagnosticsCore) {
        core.track(Any(), id = 2, name = "gone", source = "gone", exitKind = ExitKind.ERROR)
    }

    @Test
    fun snapshotCarriesEveryField() {
        val clock = FakeClock(now = 42L)
        val probes = FakeProbes().apply {
            threads = listOf("main", "ScriptThread-3[demo]", "Spawn-1", "Pool-2", "SurfaceSyncGroupTimer", "MemoryDiagnostics").map { Thread(null, null, it) }
        }
        val core = MemoryDiagnosticsCore(probes, clock)
        val runtime = Any()
        core.track(runtime, id = 1, name = "a", source = "a", exitKind = ExitKind.NORMAL)
        val session = FakeBroker()
        core.trackBridgeSession("lua", "lua-1", listOf(session))

        val snapshot = core.snapshot()
        assertEquals(
            MemorySnapshot(
                javaHeapUsed = 11L, javaHeapTotal = 12L, javaHeapMax = 13L,
                nativeHeapAllocated = 21L, nativeHeapSize = 22L, pss = 31L,
                engines = 2, retainedRuntimes = 1,
                bridgeSessions = mapOf("node" to 0, "bun" to 0, "quickjs" to 0, "lua" to 1, "python" to 0),
                images = 5, threads = 3, allThreads = 6, contexts = 3,
                timestamp = EPOCH + 42L,
            ),
            snapshot,
        )
        assertEquals(MemoryDiagnosticsCore.BRIDGE_ENGINES, snapshot.bridgeSessions.keys.toList())
        assertEquals(1, probes.pssCalls)
        assertEquals(-1L, core.snapshot(includePss = false).pss)
        assertEquals(1, probes.pssCalls)
        assertEquals("snapshot() must not collect garbage", 0, probes.gcCalls)
        assertNotNull(runtime)
        assertNotNull(session)
    }

    @Test
    fun bridgeSessionIsListedWithItsObjects() {
        val clock = FakeClock(now = 1_000L)
        val core = MemoryDiagnosticsCore(FakeProbes(), clock)
        val broker = FakeBroker()
        val callback = FakeCallback()
        core.trackBridgeSession("python", "py-7", listOf(broker, callback))
        clock.now += 3_000L

        with(core.retainedBridgeSessions().single()) {
            assertEquals("python", engine)
            assertEquals("py-7", executionId)
            assertEquals(EPOCH + 1_000L, endedAt)
            assertEquals(3_000L, elapsedMs)
            assertEquals(listOf(FakeBroker::class.java, FakeCallback::class.java).map { it.name.substringAfterLast('.') }, objects)
        }
        assertEquals(1, core.bridgeSessionCount())
        assertEquals("finished runtimes and bridge sessions are counted apart", 0, core.retainedCount())
        assertNotNull(broker)
        assertNotNull(callback)
    }

    @Test
    fun bridgeSessionsAreCountedPerEngine() {
        val core = MemoryDiagnosticsCore(FakeProbes(), FakeClock())
        val held = List(4) { FakeBroker() }
        core.trackBridgeSession("python", "py-1", listOf(held[0]))
        core.trackBridgeSession("node", "node-1", listOf(held[1]))
        core.trackBridgeSession("python", "py-2", listOf(held[2]))
        core.trackBridgeSession("future", "f-1", listOf(held[3]))
        core.trackBridgeSession("bun", "bun-1", emptyList())

        assertEquals(
            listOf("node" to 1, "bun" to 0, "quickjs" to 0, "lua" to 0, "python" to 2, "future" to 1),
            core.bridgeSessionCounts().toList(),
        )
        assertEquals(listOf("py-1", "node-1", "py-2", "f-1"), core.retainedBridgeSessions().map { it.executionId })
        assertNotNull(held)
    }

    @Test
    fun reclaimedBridgeSessionLeavesTheRegistry() {
        val core = MemoryDiagnosticsCore(FakeProbes(realGc = true), RealClock)
        val kept = FakeBroker()
        trackBridgeSessionWithReclaimableCallback(core, kept)
        trackUnreachableBridgeSession(core)
        assertEquals(2, core.bridgeSessionCount())

        val elapsed = core.awaitCleared(10_000L) {
            core.bridgeSessionCount() == 1 && core.retainedBridgeSessions().single().objects.size == 1
        }

        assertNotNull("the unreachable session objects were not reclaimed within 10 s", elapsed)
        with(core.retainedBridgeSessions().single()) {
            assertEquals("lua-1", executionId)
            assertEquals(listOf(FakeBroker::class.java.name.substringAfterLast('.')), objects)
        }
        assertFalse(core.isCleared())
        assertNotNull(kept)
    }

    // Separate frames, so that no local variable of the test keeps the session objects reachable.
    private fun trackBridgeSessionWithReclaimableCallback(core: MemoryDiagnosticsCore, kept: Any) {
        core.trackBridgeSession("lua", "lua-1", listOf(kept, FakeCallback()))
    }

    private fun trackUnreachableBridgeSession(core: MemoryDiagnosticsCore) {
        core.trackBridgeSession("quickjs", "qjs-1", listOf(FakeBroker(), FakeCallback()))
    }

    @Test
    fun collectWaitsForBridgeSessionsToo() {
        val clock = FakeClock()
        val core = MemoryDiagnosticsCore(FakeProbes(), clock)
        val pinned = FakeBroker()
        core.trackBridgeSession("bun", "bun-1", listOf(pinned))
        assertFalse(core.isCleared())

        val snapshot = core.collect(timeoutMs = 2_000L)

        assertEquals(2_000L, clock.now)
        assertEquals(1, snapshot.bridgeSessions["bun"])
        assertEquals(0, snapshot.retainedRuntimes)
        assertNotNull(pinned)
    }

    @Test
    fun collectGivesUpAfterTheTimeout() {
        val clock = FakeClock()
        val probes = FakeProbes()
        val core = MemoryDiagnosticsCore(probes, clock)
        val pinned = Any()
        core.track(pinned, id = 1, name = "pinned", source = "pinned", exitKind = ExitKind.NORMAL)

        val snapshot = core.collect(timeoutMs = 10_000L)

        assertEquals(10_000L, clock.now)
        assertEquals(1, snapshot.retainedRuntimes)
        // One forced collection at the start and one every 5 polls of 100 ms: 1 + 100 / 5 = 21, each 3 rounds plus one more GC.
        assertEquals(21 * (MemoryDiagnosticsCore.GC_ROUNDS + 1), probes.gcCalls)
        assertEquals(21 * MemoryDiagnosticsCore.GC_ROUNDS, probes.finalizationCalls)
        assertNotNull(pinned)
    }

    @Test
    fun collectReturnsAtOnceWhenNothingIsRetained() {
        val clock = FakeClock()
        val probes = FakeProbes()
        val core = MemoryDiagnosticsCore(probes, clock)

        val snapshot = core.collect()

        assertEquals(0L, clock.now)
        assertEquals(0, snapshot.retainedRuntimes)
        assertEquals(MemoryDiagnosticsCore.GC_ROUNDS + 1, probes.gcCalls)
    }

    @Test
    fun unreclaimedCheckReportsEachRuntimeOnceAfterTheDelay() {
        val clock = FakeClock()
        val core = MemoryDiagnosticsCore(FakeProbes(), clock)
        val pinned = Any()
        val silent = Any()
        core.track(pinned, id = 1, name = "b.js", source = "b.js", exitKind = ExitKind.NORMAL)
        core.track(silent, id = 2, name = "c.js", source = "c.js", exitKind = ExitKind.NORMAL, check = false)
        assertEquals(10_000L, core.nextCheckAt())

        clock.now = 5_000L
        assertTrue(core.checkEnded().isEmpty())
        assertEquals(10_000L, core.nextCheckAt())

        clock.now = 10_000L
        val reported = core.checkEnded()
        assertEquals(listOf(1), reported.map { it.id })
        assertEquals(10_000L + MemoryDiagnosticsCore.UNRECLAIMED_CHECK_BUDGET_MS, reported.single().elapsedMs)
        assertNull(core.nextCheckAt())
        assertTrue(core.checkEnded().isEmpty())

        // Still listed for the page and the script API.
        assertEquals(listOf(1, 2), core.retained().map { it.id })
        assertNotNull(pinned)
        assertNotNull(silent)
    }

    @Test
    fun unreclaimedCheckReportsTrackedResourcesStillAlive() {
        val clock = FakeClock()
        val core = object : MemoryDiagnosticsCore(FakeProbes(), clock) {
            val reported = mutableListOf<UnreleasedResource>()
            override fun onUnreleased(resource: UnreleasedResource) {
                reported += resource
            }
        }
        val scope = RuntimeResourceScope("runtime@test") { _, _ -> }
        val window = Any()
        scope.track(window, "floaty window")
        // The runtime itself is gone before the check; its resources are still checked (roadmap P4.1).
        // zh-CN: 检查之前 runtime 本身已被回收; 其资源仍要检查 (ROADMAP P4.1).
        core.track(Any(), id = 3, name = "d.js", source = "d.js", exitKind = ExitKind.STOPPED, resources = scope.trackedReferences())
        core.track(Any(), id = 4, name = "e.js", source = "e.js", exitKind = ExitKind.NORMAL, check = false, resources = scope.trackedReferences())
        assertEquals(10_000L, core.nextCheckAt())

        clock.now = 10_000L
        core.checkEnded()

        val expected = UnreleasedResource(3, "d.js", "d.js", "floaty window", 10_000L + MemoryDiagnosticsCore.UNRECLAIMED_CHECK_BUDGET_MS)
        assertEquals(listOf(expected), core.reported)
        assertEquals(listOf(expected), core.unreleased())
        assertNull(core.nextCheckAt())
        assertNotNull(window)
    }

    @Test
    fun scriptThreadsAreToldByName() {
        assertTrue(MemoryDiagnosticsCore.isScriptThread("ScriptThread-12[main.js]"))
        assertTrue(MemoryDiagnosticsCore.isScriptThread("Spawn-3"))
        assertTrue(MemoryDiagnosticsCore.isScriptThread("ScreenCapturer-ImageReader"))
        assertFalse(MemoryDiagnosticsCore.isScriptThread("SurfaceSyncGroupTimer"))
        assertFalse(MemoryDiagnosticsCore.isScriptThread("MemoryDiagnostics"))
        assertFalse(MemoryDiagnosticsCore.isScriptThread("main"))
    }

    private companion object {
        const val EPOCH = 1_760_000_000_000L
    }
}
