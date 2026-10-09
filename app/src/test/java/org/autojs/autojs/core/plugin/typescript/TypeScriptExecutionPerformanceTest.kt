package org.autojs.autojs.core.plugin.typescript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TypeScriptExecutionPerformanceTest {

    @Test
    fun sumsPrepareDispatchAndEngineCreationIntoUserWait() {
        assertEquals(
            "typescript-execution-perf schema=1 entry=activity prepareMs=1200 dispatchMs=15 " +
                "engineCreateMs=85 userWaitMs=1300",
            TypeScriptExecutionPerformance.logLine(
                entry = TypeScriptExecutionPerformance.ENTRY_ACTIVITY,
                prepareMs = 1_200L,
                dispatchMs = 15L,
                engineCreateMs = 85L,
            ),
        )
    }

    @Test
    fun clampsNegativeDurationsAndRejectsUnsafeEntries() {
        assertEquals(
            "typescript-execution-perf schema=1 entry=thread prepareMs=0 dispatchMs=0 " +
                "engineCreateMs=7 userWaitMs=7",
            TypeScriptExecutionPerformance.logLine(
                entry = TypeScriptExecutionPerformance.ENTRY_THREAD,
                prepareMs = -3L,
                dispatchMs = -1L,
                engineCreateMs = 7L,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            TypeScriptExecutionPerformance.logLine(
                entry = "source path.ts",
                prepareMs = 1L,
                dispatchMs = 0L,
                engineCreateMs = 1L,
            )
        }
    }
}
