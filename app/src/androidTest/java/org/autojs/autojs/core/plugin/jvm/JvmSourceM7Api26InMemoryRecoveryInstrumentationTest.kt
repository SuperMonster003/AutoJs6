package org.autojs.autojs.core.plugin.jvm

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused M7 API-26 diagnostic. This is deliberately narrower than the pending R2 lifecycle
 * consumer: it proves the production happy-path boundary retires one in-memory worker and admits a
 * second, fresh worker after normal terminal cleanup. It does not claim cancel/timeout/hard-stop or
 * canonical lifecycle evidence.
 */
class JvmSourceM7Api26InMemoryRecoveryInstrumentationTest {
    @Test
    fun productionInMemoryWorkerRetiresAndRecoversWithFreshGeneration() {
        assertEquals("M7 in-memory transition evidence is exact-API only", 26, Build.VERSION.SDK_INT)
        assertTrue(
            "M7 production recovery evidence requires -Pautojs.jvmSource.experimental=true",
            BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED,
        )

        val r1 = JvmSourceR1EndToEndInstrumentationTest()
        r1.stopExistingScripts()
        try {
            val first = r1.runCanonicalProductionPipeline()
            // The shared production helper returns only after its ScriptExecution is destroyed and
            // removed. Re-check the empty service boundary before admitting the second execution.
            r1.stopSelectorScripts()
            val second = r1.runCanonicalProductionPipeline()
            r1.stopSelectorScripts()

            val firstPipeline = first.getJSONObject("pipeline")
            val secondPipeline = second.getJSONObject("pipeline")
            val firstProcesses = first.getJSONObject("processes")
            val secondProcesses = second.getJSONObject("processes")
            val firstGeneration = firstProcesses.getLong("workerGeneration")
            val secondGeneration = secondProcesses.getLong("workerGeneration")
            val firstWorkerPid = firstProcesses.getInt("workerPid")
            val secondWorkerPid = secondProcesses.getInt("workerPid")

            assertEquals(IN_MEMORY_LOADER, firstPipeline.getString("loaderKind"))
            assertEquals(IN_MEMORY_LOADER, secondPipeline.getString("loaderKind"))
            assertTrue("Second execution did not advance the worker generation", secondGeneration > firstGeneration)
            assertTrue("Second execution reused the retired worker PID", firstWorkerPid != secondWorkerPid)

            val observation = JSONObject()
                .put("schemaVersion", 1)
                .put("evidenceType", "m7-api26-in-memory-recovery-diagnostic")
                .put("apiLevel", Build.VERSION.SDK_INT)
                .put("selector", SELECTOR)
                .put("loaderKind", IN_MEMORY_LOADER)
                .put("firstWorkerGeneration", firstGeneration)
                .put("secondWorkerGeneration", secondGeneration)
                .put("workerGenerationAdvanced", true)
                .put("firstWorkerPid", firstWorkerPid)
                .put("secondWorkerPid", secondWorkerPid)
                .put("freshWorkerPid", true)
                .put("normalTerminalCleanupVerified", true)
                .put("r2LifecycleCanonical", false)
            val line = "$OUTPUT_PREFIX$observation"
            Log.i(TAG, line)
            InstrumentationRegistry.getInstrumentation().sendStatus(
                0,
                android.os.Bundle().apply { putString(OUTPUT_STATUS_KEY, observation.toString()) },
            )
            println(line)
        } finally {
            r1.stopSelectorScripts()
        }
    }

    private companion object {
        const val TAG = "JvmSourceM7Api26"
        const val IN_MEMORY_LOADER = "IN_MEMORY_DEX_CLASS_LOADER"
        const val OUTPUT_PREFIX = "JVM_SOURCE_M7_API26_RECOVERY="
        const val OUTPUT_STATUS_KEY = "JVM_SOURCE_M7_API26_RECOVERY"
        const val SELECTOR =
            "org.autojs.autojs.core.plugin.jvm.JvmSourceM7Api26InMemoryRecoveryInstrumentationTest#" +
                "productionInMemoryWorkerRetiresAndRecoversWithFreshGeneration"
    }
}
