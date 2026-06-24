package org.autojs.autojs.engine

import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
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
        assertEquals("false", result.nativeValues["embedded_script.process_pool.queue_when_full_enabled"])
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
        } else {
            assertEquals("false", result.nativeValues["embedded_script.process_pool.enabled"])
            assertEquals(NodeEmbeddedProcessPoolPolicy.MODE_PRIMARY_ONLY, result.nativeValues["embedded_script.process_pool.mode"])
            assertEquals("1", result.nativeValues["embedded_script.process_pool.effective_max_active_slots"])
            assertEquals("false", result.nativeValues["embedded_script.process_pool.default_scheduler_enabled"])
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
        assertTrue(plan.reason.contains("default_scheduler_selected"))
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
