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
import org.junit.Before
import org.junit.Test

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
        assertEquals("false", result.nativeValues["embedded_script.process_pool.enabled"])
        assertEquals(NodeEmbeddedProcessPoolPolicy.MODE_PRIMARY_ONLY, result.nativeValues["embedded_script.process_pool.mode"])
        assertEquals(NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID, result.nativeValues["embedded_script.process_pool.selected_slot_id"])
        assertEquals(":nodejs_embedded", result.nativeValues["embedded_script.process_pool.selected_process_suffix"])
        assertEquals("1", result.nativeValues["embedded_script.process_pool.effective_max_active_slots"])
        assertEquals("false", result.nativeValues["embedded_script.process_pool.long_running_dedicated_slot_enabled"])
        assertEquals("false", result.nativeValues["embedded_script.process_pool.queue_when_full_enabled"])
        assertEquals("true", result.nativeValues["embedded_script.process_pool.one_active_environment_per_slot"])
        assertEquals("false", result.nativeValues["embedded_script.process_pool.shared_v8_environment_allowed"])
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
