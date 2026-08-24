package org.autojs.autojs.core.plugin.typescript

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
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

        clock.advanceMillis(12)
        recorder.recordPrepared(inputArchiveBytes = 4_096L, sourceFileCount = 3)
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
        recorder.recordCompilerElapsed(68L)
        recorder.checkpoint().also { startedAt ->
            clock.advanceMillis(4)
            recorder.recordOutputProcessingSince(startedAt)
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
        assertEquals(233L, report.totalElapsedMs)
        assertEquals(12L, report.prepareElapsedMs)
        assertEquals(2L, report.cacheLookupElapsedMs)
        assertEquals(140L, report.bindAndPrewarmElapsedMs)
        assertEquals(75L, report.remoteRoundTripElapsedMs)
        assertEquals(68L, report.compilerElapsedMs)
        assertEquals(4L, report.outputProcessingElapsedMs)
        assertEquals(4_096L, report.inputArchiveBytes)
        assertEquals(3, report.sourceFileCount)
        assertEquals(750L, report.hostHeapDeltaKiB)
        val line = report.toLogLine()
        assertTrue(line.startsWith("typescript-compile-perf schema=1"))
        assertTrue(line.contains("bindPrewarmMs=140"))
        assertTrue(line.contains("compilerMs=68"))
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

        clock.advanceMillis(3)
        recorder.recordPrepared(inputArchiveBytes = 512L, sourceFileCount = 1)
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
        assertNull(report.bindAndPrewarmElapsedMs)
        assertNull(report.remoteRoundTripElapsedMs)
        assertNull(report.compilerElapsedMs)
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

    private class FakeClock {
        private var value = 0L

        fun now(): Long = value

        fun advanceMillis(milliseconds: Long) {
            value += TimeUnit.MILLISECONDS.toNanos(milliseconds)
        }
    }
}
