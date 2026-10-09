package org.autojs.autojs.core.plugin.typescript

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptCompilationPerformanceTest {

    @Test
    fun recordsBoundedCompilerStagesWithoutSourceIdentity() {
        val clock = FakeClock()
        val reports = mutableListOf<TypeScriptCompilationPerformanceSnapshot>()
        var heapKiB = 10_000L
        val recorder = TypeScriptCompilationPerformanceRecorder(
            targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
            cacheEnabled = true,
            reporter = TypeScriptCompilationPerformanceReporter(reports::add),
            monotonicNanos = clock::now,
            usedHeapKiB = { heapKiB },
        )

        recorder.checkpoint().also { startedAt ->
            clock.advanceMillis(3)
            recorder.recordAwaitPrewarmSince(startedAt)
        }
        recorder.checkpoint().also { startedAt ->
            clock.advanceMillis(9)
            recorder.recordDiscoverySince(startedAt)
        }
        recorder.checkpoint().also { startedAt ->
            clock.advanceMillis(12)
            recorder.recordPreparedSince(
                startedAtNanos = startedAt,
                inputArchiveBytes = 4_096L,
                sourceFileCount = 3,
            )
        }
        recorder.recordCacheAvailability(available = true)
        recorder.checkpoint().also { startedAt ->
            clock.advanceMillis(2)
            recorder.recordCacheLookupSince(startedAt)
        }
        recorder.checkpoint().also { startedAt ->
            clock.advanceMillis(140)
            recorder.recordBindAndPrewarmSince(startedAt)
        }
        recorder.checkpoint().also { startedAt ->
            clock.advanceMillis(75)
            recorder.recordRemoteRoundTripSince(startedAt)
        }
        assertTrue(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_INGRESS,
                TypeScriptR2Contract.PHASE_STATE_STARTED,
                TimeUnit.MILLISECONDS.toNanos(5L),
            ),
        )
        assertTrue(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_INGRESS,
                TypeScriptR2Contract.PHASE_STATE_COMPLETED,
                TimeUnit.MILLISECONDS.toNanos(10L),
            ),
        )
        assertTrue(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_PARSE,
                TypeScriptR2Contract.PHASE_STATE_STARTED,
                TimeUnit.MILLISECONDS.toNanos(11L),
            ),
        )
        assertTrue(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_PARSE,
                TypeScriptR2Contract.PHASE_STATE_COMPLETED,
                TimeUnit.MILLISECONDS.toNanos(25L),
            ),
        )
        assertTrue(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_SEMANTIC,
                TypeScriptR2Contract.PHASE_STATE_STARTED,
                TimeUnit.MILLISECONDS.toNanos(26L),
            ),
        )
        assertTrue(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_SEMANTIC,
                TypeScriptR2Contract.PHASE_STATE_COMPLETED,
                TimeUnit.MILLISECONDS.toNanos(47L),
            ),
        )
        assertTrue(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_EMIT,
                TypeScriptR2Contract.PHASE_STATE_STARTED,
                TimeUnit.MILLISECONDS.toNanos(48L),
            ),
        )
        assertTrue(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_EMIT,
                TypeScriptR2Contract.PHASE_STATE_COMPLETED,
                TimeUnit.MILLISECONDS.toNanos(65L),
            ),
        )
        recorder.recordCompilerElapsed(68L)
        recorder.checkpoint().also { startedAt ->
            clock.advanceMillis(4)
            recorder.recordOutputProcessingSince(startedAt)
        }
        recorder.checkpoint().also { startedAt ->
            clock.advanceMillis(6)
            recorder.recordCachePublishSince(startedAt)
        }
        recorder.recordSuccess(TypeScriptCompilationOutput.Diagnostics(emptyList()))
        heapKiB = 10_750L
        recorder.finish()
        recorder.finish()

        assertEquals(1, reports.size)
        val report = reports.single()
        assertEquals(TypeScriptCompilerContract.PROFILE_RHINO, report.targetProfile)
        assertEquals(TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS, report.outcome)
        assertEquals(TypeScriptCompilationPerformanceSnapshot.CACHE_MISS, report.cacheStatus)
        assertEquals(251L, report.totalElapsedMs)
        assertEquals(3L, report.awaitPrewarmElapsedMs)
        assertEquals(9L, report.discoveryElapsedMs)
        assertEquals(12L, report.prepareElapsedMs)
        assertEquals(2L, report.cacheLookupElapsedMs)
        assertEquals(140L, report.bindAndPrewarmElapsedMs)
        assertEquals(75L, report.remoteRoundTripElapsedMs)
        assertEquals(68L, report.compilerElapsedMs)
        assertEquals(5L, report.compilerBeforeIngressElapsedMs)
        assertEquals(5L, report.compilerIngressElapsedMs)
        assertEquals(3L, report.compilerInterPhaseElapsedMs)
        assertEquals(14L, report.compilerParseElapsedMs)
        assertEquals(21L, report.compilerSemanticElapsedMs)
        assertEquals(17L, report.compilerEmitElapsedMs)
        assertEquals(3L, report.compilerAfterEmitElapsedMs)
        assertEquals(4L, report.outputProcessingElapsedMs)
        assertEquals(6L, report.cachePublishElapsedMs)
        assertEquals(4_096L, report.inputArchiveBytes)
        assertEquals(3, report.sourceFileCount)
        assertEquals(750L, report.hostHeapDeltaKiB)
        val line = report.toLogLine()
        assertTrue(line.startsWith("typescript-compile-perf schema=3"))
        assertTrue(line.contains(" awaitPrewarmMs=3 discoveryMs=9 prepareMs=12 "))
        assertTrue(line.contains(" outputMs=4 cachePublishMs=6 "))
        assertTrue(line.contains("bindPrewarmMs=140"))
        assertTrue(line.contains("compilerMs=68"))
        assertTrue(line.contains("compilerSemanticMs=21"))
        assertFalse(line.contains("requestId"))
        assertFalse(line.contains("sourcePath"))
    }

    @Test
    fun cacheHitRecordsSkippedRemoteStagesWithoutAThreshold() {
        val clock = FakeClock()
        val reports = mutableListOf<TypeScriptCompilationPerformanceSnapshot>()
        val recorder = TypeScriptCompilationPerformanceRecorder(
            targetProfile = TypeScriptCompilerContract.PROFILE_NODE,
            cacheEnabled = true,
            reporter = TypeScriptCompilationPerformanceReporter(reports::add),
            monotonicNanos = clock::now,
            usedHeapKiB = { 2_048L },
        )

        recorder.checkpoint().also { startedAt ->
            clock.advanceMillis(3)
            recorder.recordPreparedSince(
                startedAtNanos = startedAt,
                inputArchiveBytes = 512L,
                sourceFileCount = 1,
            )
        }
        recorder.recordCacheAvailability(available = true)
        recorder.checkpoint().also { startedAt ->
            clock.advanceMillis(1)
            recorder.recordCacheHit()
            recorder.recordCacheLookupSince(startedAt)
        }
        recorder.recordSuccess(TypeScriptCompilationOutput.Diagnostics(emptyList()))
        recorder.finish()

        val report = reports.single()
        assertEquals(TypeScriptCompilationPerformanceSnapshot.CACHE_HIT, report.cacheStatus)
        assertEquals(4L, report.totalElapsedMs)
        assertEquals(0L, report.awaitPrewarmElapsedMs)
        assertEquals(0L, report.discoveryElapsedMs)
        assertEquals(3L, report.prepareElapsedMs)
        assertEquals(0L, report.cachePublishElapsedMs)
        assertNull(report.bindAndPrewarmElapsedMs)
        assertNull(report.remoteRoundTripElapsedMs)
        assertNull(report.compilerElapsedMs)
        assertNull(report.compilerBeforeIngressElapsedMs)
        assertNull(report.compilerIngressElapsedMs)
        assertNull(report.compilerInterPhaseElapsedMs)
        assertNull(report.compilerParseElapsedMs)
        assertNull(report.compilerSemanticElapsedMs)
        assertNull(report.compilerEmitElapsedMs)
        assertNull(report.compilerAfterEmitElapsedMs)
        assertTrue(report.toLogLine().contains("bindPrewarmMs=na"))
        assertTrue(report.toLogLine().contains("compilerMs=na"))
    }

    @Test
    fun cancellationBeforePreparationLeavesCacheAndStagesUnobserved() {
        val reports = mutableListOf<TypeScriptCompilationPerformanceSnapshot>()
        val recorder = TypeScriptCompilationPerformanceRecorder(
            targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
            cacheEnabled = true,
            reporter = TypeScriptCompilationPerformanceReporter(reports::add),
            monotonicNanos = { 0L },
            usedHeapKiB = { 1_024L },
        )

        recorder.recordFailure(CancellationException("test cancellation"))
        recorder.finish()

        val report = reports.single()
        assertEquals(TypeScriptCompilerContract.OUTCOME_CANCELLED, report.outcome)
        assertEquals(
            TypeScriptCompilationPerformanceSnapshot.CACHE_NOT_CHECKED,
            report.cacheStatus,
        )
        assertEquals(0L, report.inputArchiveBytes)
        assertEquals(0, report.sourceFileCount)
        assertNull(report.bindAndPrewarmElapsedMs)
        assertNull(report.remoteRoundTripElapsedMs)
        assertNull(report.compilerElapsedMs)
    }

    @Test
    fun ignoresUnknownDuplicateAndOutOfOrderCompilerPhaseEvents() {
        val reports = mutableListOf<TypeScriptCompilationPerformanceSnapshot>()
        val recorder = TypeScriptCompilationPerformanceRecorder(
            targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
            cacheEnabled = false,
            reporter = TypeScriptCompilationPerformanceReporter(reports::add),
            monotonicNanos = { 0L },
            usedHeapKiB = { 1_024L },
        )

        assertFalse(
            recorder.recordCompilerPhase(
                "source/path.ts",
                TypeScriptR2Contract.PHASE_STATE_STARTED,
                TimeUnit.MILLISECONDS.toNanos(1L),
            ),
        )
        assertFalse(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_PARSE,
                TypeScriptR2Contract.PHASE_STATE_STARTED,
                TimeUnit.MILLISECONDS.toNanos(2L),
            ),
        )
        assertTrue(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_INGRESS,
                TypeScriptR2Contract.PHASE_STATE_STARTED,
                TimeUnit.MILLISECONDS.toNanos(5L),
            ),
        )
        assertFalse(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_INGRESS,
                TypeScriptR2Contract.PHASE_STATE_STARTED,
                TimeUnit.MILLISECONDS.toNanos(6L),
            ),
        )
        assertFalse(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_INGRESS,
                TypeScriptR2Contract.PHASE_STATE_COMPLETED,
                TimeUnit.MILLISECONDS.toNanos(4L),
            ),
        )
        assertTrue(
            recorder.recordCompilerPhase(
                TypeScriptR2Contract.PHASE_INGRESS,
                TypeScriptR2Contract.PHASE_STATE_COMPLETED,
                TimeUnit.MILLISECONDS.toNanos(7L),
            ),
        )
        recorder.recordCompilerElapsed(10L)
        recorder.finish()

        val report = reports.single()
        assertEquals(5L, report.compilerBeforeIngressElapsedMs)
        assertEquals(2L, report.compilerIngressElapsedMs)
        assertNull(report.compilerInterPhaseElapsedMs)
        assertNull(report.compilerParseElapsedMs)
        assertFalse(report.toLogLine().contains("source/path.ts"))
    }

    private class FakeClock {
        private var value = 0L

        fun now(): Long = value

        fun advanceMillis(milliseconds: Long) {
            value += TimeUnit.MILLISECONDS.toNanos(milliseconds)
        }
    }
}
