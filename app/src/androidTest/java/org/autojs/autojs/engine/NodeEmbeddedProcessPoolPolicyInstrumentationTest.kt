package org.autojs.autojs.engine

import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class NodeEmbeddedProcessPoolPolicyInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun processPoolPolicyIsDeviceAwareButFailClosedByDefault() {
        val plan = NodeEmbeddedProcessPoolPolicy.plan(context)

        assertEquals(NodeEmbeddedProcessPoolPolicy.SCHEMA, plan.schema)
        assertEquals(NodeEmbeddedProcessPoolPolicy.VERSION, plan.version)
        assertEquals(NodeEmbeddedProcessPoolPolicy.STATUS_DESIGN_GATED, plan.status)
        assertEquals(NodeEmbeddedProcessPoolPolicy.MODE_PRIMARY_ONLY, plan.mode)
        assertFalse(plan.multiProcessEnabled)
        assertEquals(3, plan.declaredSlots.size)
        assertEquals(NodeEmbeddedProcessPoolPolicy.primarySlot, plan.selectedSlot)
        assertEquals(1, plan.effectiveMaxActiveSlots)
        assertTrue(plan.recommendedMaxActiveSlots in 1..plan.declaredSlots.size)
        assertEquals(NodeEmbeddedProcessPoolPolicy.longRunningSlot.id, plan.longRunningDedicatedSlotId)
        assertFalse(plan.longRunningDedicatedSlotEnabled)
        assertFalse(plan.queueWhenPoolFullEnabled)
        assertTrue(plan.oneActiveEnvironmentPerSlot)
        assertFalse(plan.sharedV8EnvironmentAllowed)
        assertEquals(NodeEmbeddedProcessPoolPolicy.SLOT_CRASH_FALLBACK_MODE_NO_RESTART, plan.slotCrashFallbackMode)
        assertFalse(plan.failedSlotReplacementEnabled)
        assertEquals(NodeEmbeddedProcessPoolPolicy.SLOT_CRASH_QUARANTINE_POLICY_NONE, plan.failedSlotQuarantinePolicy)
        assertEquals(NodeEmbeddedProcessPoolPolicy.SLOT_CRASH_SURVIVOR_POLICY_ALLOW_OTHER_SLOTS, plan.survivingSlotExecutionPolicy)
    }

    @Test
    fun processPoolManifestDeclaresReservedSlots() {
        NodeEmbeddedProcessPoolPolicy.declaredSlots.forEach { slot ->
            val info = serviceInfo(slot)
            assertNotNull("missing manifest service for ${slot.serviceClassName}", info)
            assertFalse("${slot.id} must not be exported", info.exported)
            assertTrue(
                "${slot.id} processName=${info.processName} expected suffix=${slot.processSuffix}",
                info.processName.endsWith(slot.processSuffix),
            )
            if (slot.id != NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID) {
                assertFalse("${slot.id} must remain disabled by default", slot.launchEnabledByDefault)
            }
        }
    }

    @Test
    fun defaultExecutionPublishesPrimaryProcessPoolDiagnostics() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("embedded process pool policy")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                console.log("process.pool.default=" + process.version);
            """.trimIndent(),
            sourceName = "<process-pool-policy.js>",
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("embedded process pool policy", result)
        assertEquals(NodeEmbeddedProcessPoolPolicy.SCHEMA, result.nativeValues["embedded_script.process_pool.schema"])
        assertEquals(NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID, result.nativeValues["embedded_script.process_pool.selected_slot_id"])
        assertEquals(":nodejs_embedded", result.nativeValues["embedded_script.process_pool.selected_process_suffix"])
        assertEquals("false", result.nativeValues["embedded_script.process_pool.long_running_dedicated_slot_enabled"])
        assertEquals("true", result.nativeValues["embedded_script.process_pool.one_active_environment_per_slot"])
        assertEquals("false", result.nativeValues["embedded_script.process_pool.shared_v8_environment_allowed"])
        if (NodeEmbeddedProcessPoolPolicy.defaultSchedulerEligible(context)) {
            assertEquals("true", result.nativeValues["embedded_script.process_pool.enabled"])
            assertEquals(NodeEmbeddedProcessPoolPolicy.MODE_DEFAULT_TWO_SLOT, result.nativeValues["embedded_script.process_pool.mode"])
            assertEquals(
                NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_DEFAULT_SCHEDULER,
                result.nativeValues["embedded_script.process_pool.slot_selection_source"],
            )
            assertEquals("2", result.nativeValues["embedded_script.process_pool.effective_max_active_slots"])
            assertEquals("true", result.nativeValues["embedded_script.process_pool.default_scheduler_enabled"])
            assertEquals("true", result.nativeValues["embedded_script.process_pool.queue_when_full_enabled"])
            assertEquals(
                "bounded_wait_for_available_process_slot",
                result.nativeValues["embedded_script.process_pool.queue_policy"],
            )
        } else {
            assertEquals("false", result.nativeValues["embedded_script.process_pool.enabled"])
            assertEquals(NodeEmbeddedProcessPoolPolicy.MODE_PRIMARY_ONLY, result.nativeValues["embedded_script.process_pool.mode"])
            assertEquals("1", result.nativeValues["embedded_script.process_pool.effective_max_active_slots"])
            assertEquals("false", result.nativeValues["embedded_script.process_pool.default_scheduler_enabled"])
            assertEquals("false", result.nativeValues["embedded_script.process_pool.queue_when_full_enabled"])
        }
    }

    @Test
    fun defaultSchedulerPlanMarksTwoSlotMode() {
        val plan = NodeEmbeddedProcessPoolPolicy.plan(
            context = context,
            requestedSlotId = NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID,
            slotSelectionSource = NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_DEFAULT_SCHEDULER,
            defaultSchedulerEnabled = true,
        )

        assertEquals(NodeEmbeddedProcessPoolPolicy.STATUS_DEFAULT_SCHEDULER_ENABLED, plan.status)
        assertEquals(NodeEmbeddedProcessPoolPolicy.MODE_DEFAULT_TWO_SLOT, plan.mode)
        assertTrue(plan.multiProcessEnabled)
        assertEquals(NodeEmbeddedProcessPoolPolicy.overflowSlot, plan.selectedSlot)
        assertEquals(NodeEmbeddedProcessPoolPolicy.DEFAULT_SCHEDULER_EFFECTIVE_MAX_ACTIVE_SLOTS, plan.effectiveMaxActiveSlots)
        assertEquals(NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_DEFAULT_SCHEDULER, plan.slotSelectionSource)
        assertEquals(NodeEmbeddedProcessPoolPolicy.defaultSchedulerSlotIds(), plan.defaultSchedulerSlotIds)
        assertFalse(plan.defaultSchedulerFallbackEnabled)
        assertTrue(plan.queueWhenPoolFullEnabled)
        assertEquals("bounded_wait_for_available_process_slot", plan.queuePolicy)
        assertTrue(plan.reason.contains("default_scheduler_selected"))
    }

    @Test
    fun slotCrashNoRestartFallbackCollectsOverflowMarkerAndPrimarySurvives() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("embedded process pool slot crash fallback")

        val plan = NodeEmbeddedProcessPoolPolicy.plan(
            context = context,
            requestedSlotId = NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID,
        )
        assertEquals(NodeEmbeddedProcessPoolPolicy.overflowSlot, plan.selectedSlot)
        assertEquals(NodeEmbeddedProcessPoolPolicy.SLOT_CRASH_FALLBACK_MODE_NO_RESTART, plan.slotCrashFallbackMode)
        assertFalse(plan.defaultSchedulerFallbackEnabled)
        assertFalse(plan.failedSlotReplacementEnabled)
        assertEquals(NodeEmbeddedProcessPoolPolicy.SLOT_CRASH_QUARANTINE_POLICY_NONE, plan.failedSlotQuarantinePolicy)
        assertEquals(NodeEmbeddedProcessPoolPolicy.SLOT_CRASH_SURVIVOR_POLICY_ALLOW_OTHER_SLOTS, plan.survivingSlotExecutionPolicy)

        val executionId = "p13-slot-crash-fallback-${System.nanoTime()}"
        val marker = NodeCrashEvidenceStore.begin(
            context = context,
            executionId = executionId,
            sourceName = "second-execution-slot-fallback.js",
            workingDirectory = context.cacheDir.absolutePath,
            executionMode = NodeExecutionModePolicy.MODE_ONE_SHOT,
            launchSurface = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
            timeoutMs = 5_000L,
            runtimeAdapter = "adapter_v1",
            runtimeSlotId = NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID,
            scenario = "second_execution_slot_no_restart_fallback",
        )
        assertNotNull(marker)
        marker ?: return
        val nextLaunchBundle = File(
            File(context.filesDir, "nodejs/crash-evidence/next-launch"),
            "${marker.id}.json",
        )
        try {
            val collected = NodeCrashEvidenceStore.collectPending(context, "instrumentation.second_slot_no_restart_fallback")
            assertTrue(
                "next-launch collection should include overflow slot marker ${marker.id}: $collected",
                (0 until collected.length()).any { index ->
                    collected.getJSONObject(index).getString("markerId") == marker.id
                },
            )
            assertTrue(nextLaunchBundle.isFile)
            val bundle = org.json.JSONObject(nextLaunchBundle.readText(Charsets.UTF_8))
            assertEquals(NodeCrashEvidenceStore.NEXT_LAUNCH_BUNDLE_SCHEMA, bundle.getString("schema"))
            assertEquals(
                NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID,
                bundle.getJSONObject("marker").getString("runtimeSlotId"),
            )

            val survivor = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return "primary-survivor-ok";
                """.trimIndent(),
                sourceName = "<process-pool-primary-survivor.js>",
                timeoutMs = 10_000L,
                processPoolSlot = NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID,
            )
            NodeEmbeddedTestHarness.assertSucceeded("primary survivor after overflow marker", survivor)
            assertEquals("primary-survivor-ok", survivor.resultText)
            assertEquals(
                NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID,
                survivor.nativeValues["embedded_script.process_pool.selected_slot_id"],
            )
        } finally {
            marker.file.delete()
            nextLaunchBundle.delete()
        }
    }

    @Test
    fun explicitOverflowSlotPlanEnablesTwoSlotMode() {
        val plan = NodeEmbeddedProcessPoolPolicy.plan(
            context = context,
            requestedSlotId = NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID,
        )

        assertEquals(NodeEmbeddedProcessPoolPolicy.STATUS_EXPLICIT_SLOT_ENABLED, plan.status)
        assertEquals(NodeEmbeddedProcessPoolPolicy.MODE_EXPLICIT_TWO_SLOT, plan.mode)
        assertTrue(plan.multiProcessEnabled)
        assertEquals(NodeEmbeddedProcessPoolPolicy.overflowSlot, plan.selectedSlot)
        assertEquals(NodeEmbeddedProcessPoolPolicy.EXPLICIT_EFFECTIVE_MAX_ACTIVE_SLOTS, plan.effectiveMaxActiveSlots)
        assertEquals(NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID, plan.requestedSlotId)
        assertTrue(plan.reason.contains("explicit_process_slot_selected"))
        assertTrue(plan.oneActiveEnvironmentPerSlot)
        assertFalse(plan.sharedV8EnvironmentAllowed)
    }

    @Test
    fun explicitOverflowExecutionPublishesSecondSlotDiagnostics() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("embedded process pool overflow slot")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                console.log("process.pool.overflow=" + process.version);
                return "overflow-ok";
            """.trimIndent(),
            sourceName = "<process-pool-overflow.js>",
            timeoutMs = 10_000L,
            processPoolSlot = NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID,
        )

        NodeEmbeddedTestHarness.assertSucceeded("embedded process pool overflow slot", result)
        assertEquals("overflow-ok", result.resultText)
        assertEquals("true", result.nativeValues["embedded_script.process_pool.enabled"])
        assertEquals(NodeEmbeddedProcessPoolPolicy.MODE_EXPLICIT_TWO_SLOT, result.nativeValues["embedded_script.process_pool.mode"])
        assertEquals(NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID, result.nativeValues["embedded_script.process_pool.requested_slot_id"])
        assertEquals(NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID, result.nativeValues["embedded_script.process_pool.selected_slot_id"])
        assertEquals(
            NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_EXPLICIT_REQUEST,
            result.nativeValues["embedded_script.process_pool.slot_selection_source"],
        )
        assertEquals(":nodejs_embedded_2", result.nativeValues["embedded_script.process_pool.selected_process_suffix"])
        assertEquals("2", result.nativeValues["embedded_script.process_pool.effective_max_active_slots"])
        assertTrue(result.processName.endsWith(":nodejs_embedded_2"))
    }

    @Test
    fun explicitPrimaryAndOverflowSlotsRunInDifferentProcesses() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("embedded process pool two-slot execution")

        val startGate = CountDownLatch(1)
        val primary = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult>()
        val overflow = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult>()
        val primaryError = AtomicReference<Throwable>()
        val overflowError = AtomicReference<Throwable>()
        val primaryThread = Thread {
            runCatching {
                startGate.await(5, TimeUnit.SECONDS)
                primary.set(runSlotScript("primary", NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID))
            }.onFailure { primaryError.set(it) }
        }
        val overflowThread = Thread {
            runCatching {
                startGate.await(5, TimeUnit.SECONDS)
                overflow.set(runSlotScript("overflow", NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID))
            }.onFailure { overflowError.set(it) }
        }

        primaryThread.start()
        overflowThread.start()
        startGate.countDown()
        primaryThread.join(30_000L)
        overflowThread.join(30_000L)

        primaryError.get()?.let { throw AssertionError("primary slot thread failed", it) }
        overflowError.get()?.let { throw AssertionError("overflow slot thread failed", it) }
        val primaryResult = requireNotNull(primary.get()) { "primary slot result missing" }
        val overflowResult = requireNotNull(overflow.get()) { "overflow slot result missing" }
        NodeEmbeddedTestHarness.assertSucceeded("primary slot", primaryResult)
        NodeEmbeddedTestHarness.assertSucceeded("overflow slot", overflowResult)
        assertEquals(NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID, primaryResult.nativeValues["embedded_script.process_pool.selected_slot_id"])
        assertEquals(NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID, overflowResult.nativeValues["embedded_script.process_pool.selected_slot_id"])
        assertTrue(primaryResult.processName.endsWith(":nodejs_embedded"))
        assertTrue(overflowResult.processName.endsWith(":nodejs_embedded_2"))
        assertTrue("expected different service processes", primaryResult.pid != overflowResult.pid)
    }

    @Test
    fun defaultSchedulerRoutesConcurrentOneShotScriptsToDifferentProcesses() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("embedded process pool default scheduler")
        assumeTrue(
            "Default process-pool scheduler requires a device profile with at least two recommended slots.",
            NodeEmbeddedProcessPoolPolicy.defaultSchedulerEligible(context),
        )

        val startGate = CountDownLatch(1)
        val first = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult>()
        val second = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult>()
        val firstError = AtomicReference<Throwable>()
        val secondError = AtomicReference<Throwable>()
        val firstThread = Thread {
            runCatching {
                startGate.await(5, TimeUnit.SECONDS)
                first.set(runDefaultSchedulerScript("default-a"))
            }.onFailure { firstError.set(it) }
        }
        val secondThread = Thread {
            runCatching {
                startGate.await(5, TimeUnit.SECONDS)
                second.set(runDefaultSchedulerScript("default-b"))
            }.onFailure { secondError.set(it) }
        }

        firstThread.start()
        secondThread.start()
        startGate.countDown()
        firstThread.join(30_000L)
        secondThread.join(30_000L)

        firstError.get()?.let { throw AssertionError("first default scheduler thread failed", it) }
        secondError.get()?.let { throw AssertionError("second default scheduler thread failed", it) }
        val firstResult = requireNotNull(first.get()) { "first default scheduler result missing" }
        val secondResult = requireNotNull(second.get()) { "second default scheduler result missing" }
        NodeEmbeddedTestHarness.assertSucceeded("first default scheduler slot", firstResult)
        NodeEmbeddedTestHarness.assertSucceeded("second default scheduler slot", secondResult)
        val selectedSlots = setOf(
            firstResult.nativeValues["embedded_script.process_pool.selected_slot_id"],
            secondResult.nativeValues["embedded_script.process_pool.selected_slot_id"],
        )
        assertEquals(
            setOf(NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID, NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID),
            selectedSlots,
        )
        assertEquals(NodeEmbeddedProcessPoolPolicy.MODE_DEFAULT_TWO_SLOT, firstResult.nativeValues["embedded_script.process_pool.mode"])
        assertEquals(NodeEmbeddedProcessPoolPolicy.MODE_DEFAULT_TWO_SLOT, secondResult.nativeValues["embedded_script.process_pool.mode"])
        assertEquals(
            NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_DEFAULT_SCHEDULER,
            firstResult.nativeValues["embedded_script.process_pool.slot_selection_source"],
        )
        assertEquals(
            NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_DEFAULT_SCHEDULER,
            secondResult.nativeValues["embedded_script.process_pool.slot_selection_source"],
        )
        assertTrue("expected different default scheduler service processes", firstResult.pid != secondResult.pid)
    }

    @Test
    fun defaultSchedulerQueuesWhenBothDefaultSlotsAreBusy() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("embedded process pool default scheduler queue")
        assumeTrue(
            "Default process-pool scheduler requires a device profile with at least two recommended slots.",
            NodeEmbeddedProcessPoolPolicy.defaultSchedulerEligible(context),
        )

        val root = File(context.cacheDir, "node-process-pool-default-queue-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create process-pool default queue directory: $absolutePath" }
        }
        val startGate = CountDownLatch(1)
        val first = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult>()
        val second = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult>()
        val third = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult>()
        val firstError = AtomicReference<Throwable>()
        val secondError = AtomicReference<Throwable>()
        val thirdError = AtomicReference<Throwable>()
        val firstThread = Thread {
            runCatching {
                startGate.await(5, TimeUnit.SECONDS)
                first.set(runDefaultSchedulerQueueScript("first", root, 3_000L))
            }.onFailure { firstError.set(it) }
        }
        val secondThread = Thread {
            runCatching {
                startGate.await(5, TimeUnit.SECONDS)
                second.set(runDefaultSchedulerQueueScript("second", root, 3_000L))
            }.onFailure { secondError.set(it) }
        }
        val thirdThread = Thread {
            runCatching {
                third.set(runDefaultSchedulerQueueScript("third", root, 0L))
            }.onFailure { thirdError.set(it) }
        }

        try {
            firstThread.start()
            secondThread.start()
            startGate.countDown()
            assertTrue(
                "first two default scheduler scripts did not both start before timeout.",
                waitUntil(10_000L) {
                    File(root, "first-started.txt").isFile && File(root, "second-started.txt").isFile
                },
            )
            thirdThread.start()

            firstThread.join(30_000L)
            secondThread.join(30_000L)
            thirdThread.join(30_000L)
            firstError.get()?.let { throw AssertionError("first default scheduler queue thread failed", it) }
            secondError.get()?.let { throw AssertionError("second default scheduler queue thread failed", it) }
            thirdError.get()?.let { throw AssertionError("third default scheduler queue thread failed", it) }
            assertFalse("first default scheduler queue thread did not finish", firstThread.isAlive)
            assertFalse("second default scheduler queue thread did not finish", secondThread.isAlive)
            assertFalse("third default scheduler queue thread did not finish", thirdThread.isAlive)

            val firstResult = requireNotNull(first.get()) { "first default scheduler queue result missing" }
            val secondResult = requireNotNull(second.get()) { "second default scheduler queue result missing" }
            val thirdResult = requireNotNull(third.get()) { "third default scheduler queue result missing" }
            NodeEmbeddedTestHarness.assertSucceeded("first default scheduler queue", firstResult)
            NodeEmbeddedTestHarness.assertSucceeded("second default scheduler queue", secondResult)
            NodeEmbeddedTestHarness.assertSucceeded("third default scheduler queue", thirdResult)
            assertEquals("true", thirdResult.nativeValues["embedded_script.process_pool.queue_queued"])
            assertEquals("dequeued", thirdResult.nativeValues["embedded_script.process_pool.queue_status"])
            assertTrue(
                thirdResult.nativeValues["embedded_script.process_pool.queue_wait_ms"].orEmpty(),
                thirdResult.nativeValues["embedded_script.process_pool.queue_wait_ms"].orEmpty().toLongOrNull() ?: 0L > 0L,
            )
            assertFalse(
                "default process-pool queued run must not report engine busy.",
                thirdResult.errorCode == NodeRuntimeErrorCodes.NODE_ENGINE_BUSY,
            )
            assertEquals(NodeEmbeddedProcessPoolPolicy.MODE_DEFAULT_TWO_SLOT, thirdResult.nativeValues["embedded_script.process_pool.mode"])
            assertEquals(
                NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_DEFAULT_SCHEDULER,
                thirdResult.nativeValues["embedded_script.process_pool.slot_selection_source"],
            )

            val orderText = File(root, "order.txt").readText(Charsets.UTF_8)
            val thirdStart = orderText.indexOf("third-start")
            val firstDone = orderText.indexOf("first-done")
            val secondDone = orderText.indexOf("second-done")
            assertTrue(orderText, thirdStart >= 0)
            assertTrue(orderText, firstDone >= 0 || secondDone >= 0)
            assertTrue(orderText, thirdStart > listOf(firstDone, secondDone).filter { it >= 0 }.minOrNull()!!)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun concurrentSlotsStressSharedResourcesRemainIsolated() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("embedded process pool shared resource stress")

        val root = File(context.cacheDir, "node-process-pool-resource-stress-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create process-pool resource stress directory: $absolutePath" }
        }
        val primaryDir = prepareSlotStressDirectory(root, "primary")
        val overflowDir = prepareSlotStressDirectory(root, "overflow")
        try {
            val startGate = CountDownLatch(1)
            val primary = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult>()
            val overflow = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult>()
            val primaryError = AtomicReference<Throwable>()
            val overflowError = AtomicReference<Throwable>()
            val primaryThread = Thread {
                runCatching {
                    startGate.await(5, TimeUnit.SECONDS)
                    primary.set(
                        runSharedResourceStressScript(
                            label = "primary",
                            slotId = NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID,
                            workDir = primaryDir,
                        ),
                    )
                }.onFailure { primaryError.set(it) }
            }
            val overflowThread = Thread {
                runCatching {
                    startGate.await(5, TimeUnit.SECONDS)
                    overflow.set(
                        runSharedResourceStressScript(
                            label = "overflow",
                            slotId = NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID,
                            workDir = overflowDir,
                        ),
                    )
                }.onFailure { overflowError.set(it) }
            }

            primaryThread.start()
            overflowThread.start()
            startGate.countDown()
            primaryThread.join(45_000L)
            overflowThread.join(45_000L)

            primaryError.get()?.let { throw AssertionError("primary resource stress thread failed", it) }
            overflowError.get()?.let { throw AssertionError("overflow resource stress thread failed", it) }
            val primaryResult = requireNotNull(primary.get()) { "primary resource stress result missing" }
            val overflowResult = requireNotNull(overflow.get()) { "overflow resource stress result missing" }

            assertSharedResourceStressResult(
                label = "primary",
                slotId = NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID,
                workDir = primaryDir,
                result = primaryResult,
            )
            assertSharedResourceStressResult(
                label = "overflow",
                slotId = NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID,
                workDir = overflowDir,
                result = overflowResult,
            )
            assertTrue("expected isolated stress service processes", primaryResult.pid != overflowResult.pid)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun longRunningSlotIsReservedUntilPromotion() {
        val plan = NodeEmbeddedProcessPoolPolicy.plan(
            context = context,
            executionMode = NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
            launchSurface = NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
        )

        assertEquals(NodeEmbeddedProcessPoolPolicy.primarySlot, plan.selectedSlot)
        assertEquals(NodeEmbeddedProcessPoolPolicy.longRunningSlot.id, plan.longRunningDedicatedSlotId)
        assertFalse(plan.longRunningDedicatedSlotEnabled)
        assertTrue(plan.reason.contains("dedicated_long_running_slot_reserved"))
    }

    private fun prepareSlotStressDirectory(root: File, label: String): File {
        return File(root, label).apply {
            check(mkdirs()) { "Failed to create $label stress directory: $absolutePath" }
            File(this, "slot-state.cjs").writeText(
                """module.exports = { label: "$label", count: 0 };""",
                Charsets.UTF_8,
            )
        }
    }

    private fun runSharedResourceStressScript(
        label: String,
        slotId: String,
        workDir: File,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                return (async () => {
                  const fs = require("fs");
                  const bridge = require("autojs6:bridge");
                  const storage = require("storage");
                  const label = process.env.P13_POOL_SLOT_LABEL;
                  const secret = process.env.P13_POOL_SLOT_SECRET;
                  const resolved = require.resolve("./slot-state.cjs");
                  const cacheBefore = Boolean(require.cache[resolved]);
                  const state = require("./slot-state.cjs");
                  state.count += 1;
                  const cacheAfter = Boolean(require.cache[resolved]);
                  const cacheCount = Object.keys(require.cache)
                    .filter((filename) => filename.endsWith("slot-state.cjs"))
                    .length;
                  const globalBefore = typeof globalThis.__p13PoolResourceStress;
                  globalThis.__p13PoolResourceStress = label;

                  const buckets = Object.create(null);
                  const requests = [];
                  bridge.__test.setTransport({
                    postMessage(message) {
                      const request = JSON.parse(message);
                      requests.push(request);
                      let result = null;
                      if (request.module === "storage") {
                        const storeName = request.args[0];
                        const key = request.args[1];
                        const bucket = buckets[storeName] || (buckets[storeName] = Object.create(null));
                        if (request.method === "put") {
                          bucket[key] = request.args[2];
                        } else if (request.method === "get") {
                          result = Object.prototype.hasOwnProperty.call(bucket, key)
                            ? { found: true, value: bucket[key] }
                            : { found: false };
                        } else if (request.method === "clear") {
                          Object.keys(bucket).forEach((entry) => delete bucket[entry]);
                        }
                      }
                      setImmediate(() => {
                        bridge.__test.receiveMessage(JSON.stringify({
                          id: request.id,
                          ok: true,
                          result
                        }));
                      });
                    }
                  });

                  const store = storage.create("pool-" + label);
                  await store.put("slot", label, { timeoutMs: 1000 });
                  const stored = await store.get("slot", null, { timeoutMs: 1000 });
                  await store.clear({ timeoutMs: 1000 });

                  fs.writeFileSync("./watch-target.txt", "before-" + label, "utf8");
                  let watchEventType = null;
                  let watchClosed = false;
                  const watcher = fs.watch("./watch-target.txt", { persistent: false }, (eventType) => {
                    watchEventType = eventType;
                  });
                  fs.writeFileSync("./watch-target.txt", "after-" + label, "utf8");
                  await new Promise((resolve) => setTimeout(resolve, 100));
                  watcher.close();
                  watchClosed = true;
                  await new Promise((resolve) => setImmediate(resolve));

                  const fd = fs.openSync("./file-handle.txt", "w+");
                  try {
                    fs.writeSync(fd, "handle-" + label);
                  } finally {
                    fs.closeSync(fd);
                  }
                  const fileText = fs.readFileSync("./file-handle.txt", "utf8");

                  const timerOrder = [];
                  await new Promise((resolve) => {
                    setImmediate(() => timerOrder.push("immediate"));
                    setTimeout(() => {
                      timerOrder.push("timeout");
                      resolve();
                    }, 25);
                  });

                  process.stdout.write("p13.pool.resource.raw_stdout." + label + "=" + process.pid + "\n");
                  process.stderr.write("p13.pool.resource.raw_stderr." + label + "=" + process.pid + "\n");
                  console.log("p13.pool.resource.stdout." + label + "=" + state.label + "|" + process.cwd());

                  const summary = {
                    label,
                    pid: process.pid,
                    cwdOk: process.cwd().replace(/\\/g, "/").endsWith("/" + label),
                    envOk: secret === label + "-secret" && process.env.P13_POOL_SLOT_OTHER_SECRET === undefined,
                    moduleOk: !cacheBefore && cacheAfter && cacheCount === 1 && state.label === label && state.count === 1,
                    globalOk: globalBefore === "undefined",
                    timerOk: timerOrder.join("|") === "immediate|timeout",
                    watchOk: watchClosed && (watchEventType === null || watchEventType === "change" || watchEventType === "rename"),
                    fileHandleOk: fileText === "handle-" + label,
                    bridgeOk: stored === label && bridge.__test.pendingCount() === 0,
                    bridgeRequestCount: requests.length
                  };
                  summary.allOk = summary.cwdOk &&
                    summary.envOk &&
                    summary.moduleOk &&
                    summary.globalOk &&
                    summary.timerOk &&
                    summary.watchOk &&
                    summary.fileHandleOk &&
                    summary.bridgeOk;
                  console.log("p13.pool.resource.summary." + label + "=" + JSON.stringify(summary));
                  return JSON.stringify(summary);
                })().catch((error) => {
                  console.error("p13.pool.resource.error." + process.env.P13_POOL_SLOT_LABEL + "=" + (error && (error.stack || error.message) || error));
                  throw error;
                });
            """.trimIndent(),
            sourceName = "<process-pool-resource-stress-$label.js>",
            workingDirectory = workDir,
            timeoutMs = 20_000L,
            env = mapOf(
                "P13_POOL_SLOT_LABEL" to label,
                "P13_POOL_SLOT_SECRET" to "$label-secret",
            ),
            processPoolSlot = slotId,
        )
    }

    private fun assertSharedResourceStressResult(
        label: String,
        slotId: String,
        workDir: File,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        NodeEmbeddedTestHarness.assertSucceeded("$label shared resource stress", result)
        assertEquals(slotId, result.nativeValues["embedded_script.process_pool.selected_slot_id"])
        assertEquals(NodeEmbeddedProcessPoolPolicy.MODE_EXPLICIT_TWO_SLOT, result.nativeValues["embedded_script.process_pool.mode"])
        assertEquals(
            NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_EXPLICIT_REQUEST,
            result.nativeValues["embedded_script.process_pool.slot_selection_source"],
        )
        assertTrue(
            "workingDirectory should point at $label slot work dir: ${result.workingDirectory}",
            result.workingDirectory.orEmpty().replace("\\", "/").endsWith("/$label"),
        )
        assertTrue(result.stdout, result.stdout.contains("p13.pool.resource.raw_stdout.$label="))
        assertTrue(result.stdout, result.stdout.contains("p13.pool.resource.stdout.$label="))
        assertTrue(result.stdout, result.stdout.contains("p13.pool.resource.summary.$label="))
        assertTrue(result.stderr, result.stderr.contains("p13.pool.resource.raw_stderr.$label="))
        assertEquals(false, result.pendingTimers)
        assertEquals(0, result.pendingTimerCount ?: -1)
        assertEquals(0, result.pendingScopedFsCallbacks ?: -1)
        assertEquals(false, result.pendingScopedFsStreams)
        assertEquals(0, result.pendingScopedFsStreamCount ?: -1)
        assertEquals(0, result.pendingAsyncCallbacks ?: -1)
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_resource.active_count"])

        val summary = JSONObject(result.resultText.orEmpty())
        val summaryText = summary.toString()
        assertEquals(label, summary.getString("label"))
        assertTrue("cwdOk summary=$summaryText", summary.getBoolean("cwdOk"))
        assertTrue("envOk summary=$summaryText", summary.getBoolean("envOk"))
        assertTrue("moduleOk summary=$summaryText", summary.getBoolean("moduleOk"))
        assertTrue("globalOk summary=$summaryText", summary.getBoolean("globalOk"))
        assertTrue("timerOk summary=$summaryText", summary.getBoolean("timerOk"))
        assertTrue("watchOk summary=$summaryText", summary.getBoolean("watchOk"))
        assertTrue("fileHandleOk summary=$summaryText", summary.getBoolean("fileHandleOk"))
        assertTrue("bridgeOk summary=$summaryText", summary.getBoolean("bridgeOk"))
        assertTrue("allOk summary=$summaryText", summary.getBoolean("allOk"))
        assertEquals(3, summary.getInt("bridgeRequestCount"))

        val markerFile = File(result.nativeValues["embedded_script.crash_evidence.marker_path"].orEmpty())
        assertTrue("missing crash marker for $label: ${markerFile.absolutePath}", markerFile.isFile)
        val marker = JSONObject(markerFile.readText(Charsets.UTF_8))
        assertEquals(NodeCrashEvidenceStore.STATE_CLEAN_EXIT, marker.getString("state"))
        assertEquals(slotId, marker.getString("runtimeSlotId"))
        assertEquals(result.nativeValues["embedded_script.execution_id"], marker.getString("executionId"))
        assertEquals("embedded_script_service", marker.getString("scenario"))
        assertEquals(true, marker.getJSONObject("resourceSnapshot").getBoolean("implemented"))
        markerFile.delete()
    }

    private fun runSlotScript(
        label: String,
        slotId: String,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                return new Promise((resolve) => {
                  setTimeout(() => {
                    console.log("slot.label=$label");
                    console.log("slot.pid=" + process.pid);
                    resolve("$label-ok");
                  }, 500);
                });
            """.trimIndent(),
            sourceName = "<process-pool-$label.js>",
            timeoutMs = 15_000L,
            processPoolSlot = slotId,
        )
    }

    private fun runDefaultSchedulerScript(
        label: String,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                return new Promise((resolve) => {
                  setTimeout(() => {
                    console.log("slot.label=$label");
                    console.log("slot.pid=" + process.pid);
                    resolve("$label-ok");
                  }, 500);
                });
            """.trimIndent(),
            sourceName = "<process-pool-default-$label.js>",
            timeoutMs = 15_000L,
        )
    }

    private fun runDefaultSchedulerQueueScript(
        label: String,
        workDir: File,
        holdMs: Long,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const fs = require("fs");
                fs.writeFileSync("./$label-started.txt", "started");
                fs.appendFileSync("./order.txt", "$label-start\n");
                return new Promise((resolve) => {
                  setTimeout(() => {
                    fs.appendFileSync("./order.txt", "$label-done\n");
                    console.log("slot.queue.$label=" + process.pid);
                    resolve("$label-ok");
                  }, $holdMs);
                });
            """.trimIndent(),
            sourceName = "<process-pool-default-queue-$label.js>",
            workingDirectory = workDir,
            timeoutMs = 20_000L,
            queueTimeoutMs = 10_000L,
        )
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) {
                return true
            }
            SystemClock.sleep(50L)
        }
        return condition()
    }

    @Suppress("DEPRECATION")
    private fun serviceInfo(slot: NodeEmbeddedProcessPoolPolicy.ProcessSlot): ServiceInfo {
        val component = slot.componentName(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getServiceInfo(
                component,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        }
    }
}
