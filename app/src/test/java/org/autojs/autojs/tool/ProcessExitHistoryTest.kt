package org.autojs.autojs.tool

import android.app.ApplicationExitInfo.*
import org.autojs.autojs.tool.ProcessExitHistory.Category
import org.autojs.autojs.tool.ProcessExitHistory.Record
import org.junit.Assert.*
import org.junit.Test

class ProcessExitHistoryTest {

    private val now = 1_800_000_000_000L
    private fun record(
        timestamp: Long = now,
        process: String = "org.autojs.autojs6",
        pid: Int = 123,
        reason: Int = REASON_OTHER,
        status: Int = 0,
        description: String? = null,
    ) = Record(timestamp, process, pid, reason, status, description, 1024, 2048)

    @Test
    fun memoryLimiterRequiresBothDocumentedFields() {
        assertEquals(Category.MEMORY_LIMITER, record(description = "MemoryLimiter:AnonSwap limit=4096").category)
        assertEquals(Category.SYSTEM, record(description = "system update").category)
        assertEquals(Category.SYSTEM, record().category)
        assertEquals(Category.SYSTEM, record(description = "MemoryLimiter:Other").category)
        assertEquals(Category.LOW_MEMORY, record(reason = REASON_LOW_MEMORY, description = "MemoryLimiter:AnonSwap").category)
        assertEquals(Category.CRASH, record(reason = REASON_CRASH, description = "OutOfMemoryError").category)
        assertEquals(Category.UNKNOWN, record(reason = REASON_SIGNALED, status = 9).category)
    }

    @Test
    fun routineExitsAreRetainedWithoutCallingThemCrashes() {
        val records = listOf(
            record(reason = REASON_EXIT_SELF),
            record(timestamp = now - 1, reason = REASON_USER_REQUESTED),
            record(timestamp = now - 2, reason = REASON_PERMISSION_CHANGE),
            record(timestamp = now - 3, reason = REASON_PACKAGE_UPDATED),
        )
        assertEquals(records, ProcessExitHistory.select(records, now, 0))
        assertEquals(listOf(Category.SELF_EXIT, Category.USER_STOP, Category.SYSTEM, Category.SYSTEM), records.map { it.category })
        assertEquals(Category.SELF_EXIT, record(reason = REASON_EXIT_SELF, status = 1).category)
        assertEquals(Category.USER_STOP, record(reason = REASON_USER_STOPPED).category)
    }

    @Test
    fun distinguishesCrashAnrAndLowMemory() {
        assertEquals(Category.CRASH, record(reason = REASON_CRASH).category)
        assertEquals(Category.CRASH, record(reason = REASON_CRASH_NATIVE).category)
        assertEquals(Category.ANR, record(reason = REASON_ANR).category)
        assertEquals(Category.LOW_MEMORY, record(reason = REASON_LOW_MEMORY).category)
    }

    @Test
    fun preservesFuturePlatformReasonAndRawEvidence() {
        val value = record(reason = 999, status = 137, description = "vendor detail")
        assertEquals(Category.UNKNOWN, value.category)
        assertEquals("REASON_UNKNOWN_999", value.reasonName)
        assertEquals(value, ProcessExitHistory.select(listOf(value), now, 0).single())
    }

    @Test
    fun knownReasonNameDoesNotDependOnCategory() {
        assertEquals("REASON_OTHER", record(description = "MemoryLimiter:AnonSwap").reasonName)
        assertEquals("REASON_CRASH_NATIVE", record(reason = REASON_CRASH_NATIVE).reasonName)
        assertEquals("REASON_SIGNALED", record(reason = REASON_SIGNALED).reasonName)
    }

    @Test
    fun sortsAndBoundsRecentRecords() {
        val records = (1..30).map { record(timestamp = now - it) }.reversed()
        val selected = ProcessExitHistory.select(records, now, 0)
        assertEquals(20, selected.size)
        assertEquals(now - 1, selected.first().timestamp)
        assertEquals(now - 20, selected.last().timestamp)
    }

    @Test
    fun dropsOldFutureAndInvalidTimestampsWithInclusiveAgeBoundary() {
        val boundary = now - ProcessExitHistory.MAX_AGE_MILLIS
        val records = listOf(record(), record(boundary), record(boundary - 1), record(now + 1), record(0), record(-1))
        assertEquals(listOf(now, boundary), ProcessExitHistory.select(records, now, 0).map { it.timestamp })
    }

    @Test
    fun rejectsRecordsFromBeforeThisInstallation() {
        val records = listOf(record(now - 1), record(now - 2), record(now - 3))
        assertEquals(records.take(2), ProcessExitHistory.select(records, now, now - 2))
        assertTrue(ProcessExitHistory.select(records, now, now + 1).isEmpty())
    }

    @Test
    fun deduplicatesBeforeApplyingLimit() {
        val duplicate = record()
        val older = record(now - 1)
        assertEquals(listOf(duplicate, older), ProcessExitHistory.select(List(30) { duplicate } + older, now, 0))
    }

    @Test
    fun retainsDifferentProcessesAndReusedPids() {
        val values = listOf(record(), record(process = "org.autojs.autojs6:crash_report"), record(now - 1), record(pid = 456))
        assertEquals(values.toSet(), ProcessExitHistory.select(values, now, 0).toSet())
    }

    @Test
    fun refreshingTheSameHistoryIsStable() {
        val records = listOf(record(now - 1), record(now - 2), record(now - 1))
        val first = ProcessExitHistory.select(records, now, 0)
        assertEquals(first, ProcessExitHistory.select(records, now + 1000, 0))
        assertEquals(first, ProcessExitHistory.select(first, now, 0))
    }

    @Test
    fun keepsMemoryEvidenceAndDescriptionForClassificationBeforeDisplayTruncation() {
        val value = record(description = "x".repeat(4096) + "MemoryLimiter:AnonSwap")
        val selected = ProcessExitHistory.select(listOf(value), now, 0).single()
        assertEquals(Category.MEMORY_LIMITER, selected.category)
        assertEquals(1024L, selected.pss)
        assertEquals(2048L, selected.rss)
    }
}
