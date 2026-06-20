package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeExecutionModePolicyInstrumentationTest {

    @Test
    fun phase7StateMachineDefinesAllModePolicies() {
        assertEquals(
            listOf(
                NodeExecutionModePolicy.MODE_ONE_SHOT,
                NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
                NodeExecutionModePolicy.MODE_SCHEDULED,
                NodeExecutionModePolicy.MODE_WORKER_COMPUTATION,
                NodeExecutionModePolicy.MODE_NODE_SANDBOXED,
            ),
            NodeExecutionModePolicy.canonicalModes(),
        )

        NodeExecutionModePolicy.definitions().forEach { definition ->
            assertTrue("${definition.mode} missing timeout policy", definition.timeoutPolicy.isNotBlank())
            assertTrue("${definition.mode} missing foreground requirement", definition.foregroundRequirement.isNotBlank())
            assertTrue("${definition.mode} missing notification requirement", definition.notificationRequirement.isNotBlank())
            assertTrue("${definition.mode} missing restart policy", definition.restartPolicy.isNotBlank())
            assertTrue("${definition.mode} missing quota profile", definition.resourceQuotaProfile.isNotBlank())
            assertTrue("${definition.mode} missing stop semantics", definition.stopSemantics.isNotBlank())
        }

        val oneShot = NodeExecutionModePolicy.definition(NodeExecutionModePolicy.MODE_ONE_SHOT)
        assertTrue(oneShot.supported)
        assertTrue(NodeExecutionModePolicy.isSupported(NodeExecutionModePolicy.MODE_ONE_SHOT))
        assertEquals("required_positive_timeout", oneShot.timeoutPolicy)
        assertEquals("never", oneShot.restartPolicy)
        assertTrue(oneShot.allowedProviders.contains("work_manager"))

        val interactive = NodeExecutionModePolicy.definition(NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING)
        assertTrue(interactive.supported)
        assertFalse(NodeExecutionModePolicy.isSupported(NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING))
        assertTrue(
            NodeExecutionModePolicy.isSupported(
                NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
                NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
            ),
        )
        assertEquals("required", interactive.foregroundRequirement)
        assertEquals("persistent_stop_action_required", interactive.notificationRequirement)
        assertTrue(interactive.unsupportedReason.orEmpty().contains("explicit interactive session"))

        val scheduled = NodeExecutionModePolicy.definition(NodeExecutionModePolicy.MODE_SCHEDULED)
        assertTrue(scheduled.supported)
        assertFalse(NodeExecutionModePolicy.isSupported(NodeExecutionModePolicy.MODE_SCHEDULED))
        assertTrue(
            NodeExecutionModePolicy.isSupported(
                NodeExecutionModePolicy.MODE_SCHEDULED,
                NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
            ),
        )
        assertEquals("never_no_implicit_retry", scheduled.restartPolicy)
        assertTrue(scheduled.allowedProviders.contains("work_manager"))

        val worker = NodeExecutionModePolicy.definition(NodeExecutionModePolicy.MODE_WORKER_COMPUTATION)
        assertFalse(worker.supported)
        assertEquals("not_applicable", worker.foregroundRequirement)
        assertTrue(worker.allowedProviders.isEmpty())

        val sandboxed = NodeExecutionModePolicy.definition(NodeExecutionModePolicy.MODE_NODE_SANDBOXED)
        assertFalse(sandboxed.supported)
        assertEquals("design_gated", sandboxed.status)
        assertEquals("host_owned_visibility_required", sandboxed.foregroundRequirement)
        assertEquals("hardened_sandbox_broker_only", sandboxed.resourceQuotaProfile)
        assertTrue(sandboxed.allowedProviders.isEmpty())
    }

    @Test
    fun executionModeAliasesNormalizeToCanonicalModes() {
        assertEquals(NodeExecutionModePolicy.MODE_ONE_SHOT, NodeExecutionModePolicy.normalize(null))
        assertEquals(NodeExecutionModePolicy.MODE_ONE_SHOT, NodeExecutionModePolicy.normalize("oneShot"))
        assertEquals(NodeExecutionModePolicy.MODE_ONE_SHOT, NodeExecutionModePolicy.normalize("one-shot"))

        assertEquals(
            NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
            NodeExecutionModePolicy.normalize("interactiveLongRunning"),
        )
        assertEquals(
            NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
            NodeExecutionModePolicy.normalize("longRunning"),
        )
        assertEquals(
            NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
            NodeExecutionModePolicy.normalize("service"),
        )

        assertEquals(NodeExecutionModePolicy.MODE_SCHEDULED, NodeExecutionModePolicy.normalize("work_manager"))
        assertEquals(
            NodeExecutionModePolicy.MODE_WORKER_COMPUTATION,
            NodeExecutionModePolicy.normalize("workerComputation"),
        )
        assertEquals(
            NodeExecutionModePolicy.MODE_NODE_SANDBOXED,
            NodeExecutionModePolicy.normalize("nodeSandboxed"),
        )
        assertEquals(
            NodeExecutionModePolicy.MODE_NODE_SANDBOXED,
            NodeExecutionModePolicy.normalize("sandbox"),
        )
    }

    @Test
    fun unsupportedModesReturnStablePolicyMetadata() {
        val interactive = NodeExecutionModePolicy.nativeValues("longRunning")
        assertEquals(NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING, interactive["embedded_script.execution_mode"])
        assertEquals("partial", interactive["embedded_script.execution_mode_status"])
        assertEquals(NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT, interactive["embedded_script.execution_mode_launch_surface"])
        assertEquals("false", interactive["embedded_script.execution_mode_supported"])
        assertEquals(
            NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
            interactive["embedded_script.execution_mode_supported_launch_surfaces"],
        )
        assertEquals("true", interactive["embedded_script.long_running_requested"])
        assertEquals("false", interactive["embedded_script.long_running_supported"])
        assertTrue(
            interactive["embedded_script.execution_mode_allowed_providers"].orEmpty(),
            interactive["embedded_script.execution_mode_allowed_providers"].orEmpty().contains("notifications"),
        )
        val interactiveSession = NodeExecutionModePolicy.nativeValues(
            "longRunning",
            NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
        )
        assertEquals("true", interactiveSession["embedded_script.execution_mode_supported"])
        assertEquals("true", interactiveSession["embedded_script.long_running_supported"])

        val scheduled = NodeExecutionModePolicy.nativeValues("scheduled")
        assertEquals("false", scheduled["embedded_script.execution_mode_supported"])
        assertEquals(
            NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
            scheduled["embedded_script.execution_mode_supported_launch_surfaces"],
        )
        assertEquals("false", scheduled["embedded_script.long_running_requested"])
        assertEquals("required_task_timeout", scheduled["embedded_script.execution_mode_timeout_policy"])
        val scheduledRunner = NodeExecutionModePolicy.nativeValues(
            "scheduled",
            NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
        )
        assertEquals("true", scheduledRunner["embedded_script.execution_mode_supported"])

        val worker = NodeExecutionModePolicy.nativeValues("workerComputation")
        assertEquals("none", worker["embedded_script.execution_mode_allowed_providers"])
        assertEquals("worker_computation_restricted", worker["embedded_script.execution_mode_resource_quota_profile"])

        val sandboxed = NodeExecutionModePolicy.nativeValues("nodeSandboxed")
        assertEquals(NodeExecutionModePolicy.MODE_NODE_SANDBOXED, sandboxed["embedded_script.execution_mode"])
        assertEquals("design_gated", sandboxed["embedded_script.execution_mode_status"])
        assertEquals("none", sandboxed["embedded_script.execution_mode_supported_launch_surfaces"])
        assertEquals("false", sandboxed["embedded_script.execution_mode_supported"])
        assertEquals("true", sandboxed["embedded_script.sandboxed_runtime_requested"])
        assertEquals("false", sandboxed["embedded_script.sandboxed_runtime_supported"])
        assertEquals("hardened_sandbox_broker_only", sandboxed["embedded_script.execution_mode_resource_quota_profile"])

        val unknown = NodeExecutionModePolicy.nativeValues("daemon")
        assertEquals("daemon", unknown["embedded_script.execution_mode"])
        assertEquals("unknown", unknown["embedded_script.execution_mode_status"])
        assertEquals("false", unknown["embedded_script.execution_mode_supported"])
        assertTrue(NodeExecutionModePolicy.unsupportedMessage("daemon").contains("not supported"))
    }
}
