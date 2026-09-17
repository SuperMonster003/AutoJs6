package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeExecutionModePolicyInstrumentationTest {

    @Test
    fun hostLaunchesExactlyThePluginCatalogModes() {
        assertEquals(
            listOf(
                NodeExecutionModePolicy.MODE_ONE_SHOT,
                NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
                NodeExecutionModePolicy.MODE_SCHEDULED,
            ),
            NodeExecutionModePolicy.runnableModes,
        )

        assertEquals(
            NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
            NodeExecutionModePolicy.launchSurfaceFor(NodeExecutionModePolicy.MODE_ONE_SHOT),
        )
        assertEquals(
            NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
            NodeExecutionModePolicy.launchSurfaceFor(NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING),
        )
        assertEquals(
            NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
            NodeExecutionModePolicy.launchSurfaceFor(NodeExecutionModePolicy.MODE_SCHEDULED),
        )

        assertTrue(NodeExecutionModePolicy.isSupported(NodeExecutionModePolicy.MODE_ONE_SHOT))
        assertFalse(NodeExecutionModePolicy.isSupported(NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING))
        assertTrue(
            NodeExecutionModePolicy.isSupported(
                NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
                NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
            ),
        )
        assertFalse(NodeExecutionModePolicy.isSupported(NodeExecutionModePolicy.MODE_SCHEDULED))
        assertTrue(
            NodeExecutionModePolicy.isSupported(
                NodeExecutionModePolicy.MODE_SCHEDULED,
                NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
            ),
        )

        assertTrue(NodeExecutionModePolicy.isLongRunningRequest("service"))
        assertFalse(NodeExecutionModePolicy.isLongRunningRequest("scheduled"))
        assertTrue(NodeExecutionModePolicy.isInteractiveLongRunningLaunchSurface(NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION))
        assertFalse(NodeExecutionModePolicy.isInteractiveLongRunningLaunchSurface("packaged_long_running"))
        assertFalse(NodeExecutionModePolicy.isInteractiveLongRunningLaunchSurface(NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT))
        assertFalse(NodeExecutionModePolicy.isInteractiveLongRunningLaunchSurface(null))
    }

    @Test
    fun executionModeAliasesNormalizeToCanonicalModes() {
        assertEquals(NodeExecutionModePolicy.MODE_ONE_SHOT, NodeExecutionModePolicy.normalize(null))
        assertEquals(NodeExecutionModePolicy.MODE_ONE_SHOT, NodeExecutionModePolicy.normalize(""))
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
            NodeExecutionModePolicy.normalize(NodeExecutionModePolicy.MODE_LONG_RUNNING),
        )
        assertEquals(
            NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
            NodeExecutionModePolicy.normalize(NodeExecutionModePolicy.MODE_SERVICE),
        )

        assertEquals(NodeExecutionModePolicy.MODE_SCHEDULED, NodeExecutionModePolicy.normalize("work_manager"))
        assertEquals(NodeExecutionModePolicy.MODE_SCHEDULED, NodeExecutionModePolicy.normalize("Schedule"))
    }

    @Test
    fun retiredReservedModesAreNotHostLaunchTokens() {
        listOf(
            "workerComputation",
            "worker_computation",
            "worker",
            "nodeSandboxed",
            "node_sandboxed",
            "sandbox",
            "daemon",
        ).forEach { mode ->
            assertFalse(mode, NodeExecutionModePolicy.isRunnable(mode))
            assertNull(mode, NodeExecutionModePolicy.launchSurfaceFor(mode))
            assertFalse(mode, NodeExecutionModePolicy.isSupported(mode))
        }
        // Retired names are no longer canonicalised; they pass through as plain lowercase text.
        assertEquals("workercomputation", NodeExecutionModePolicy.normalize("workerComputation"))
        assertEquals("node_sandboxed", NodeExecutionModePolicy.normalize("node-sandboxed"))
    }

    @Test
    fun nativeValuesCarryOnlyHostLaunchFacts() {
        val expectedKeys = setOf(
            "embedded_script.execution_mode",
            "embedded_script.execution_mode_launch_surface",
            "embedded_script.execution_mode_supported",
            "embedded_script.execution_mode_supported_launch_surfaces",
            "embedded_script.long_running_requested",
            "embedded_script.long_running_supported",
        )

        val interactive = NodeExecutionModePolicy.nativeValues("longRunning")
        assertEquals(expectedKeys, interactive.keys)
        assertEquals(NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING, interactive["embedded_script.execution_mode"])
        assertEquals(NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT, interactive["embedded_script.execution_mode_launch_surface"])
        assertEquals("false", interactive["embedded_script.execution_mode_supported"])
        assertEquals(
            NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
            interactive["embedded_script.execution_mode_supported_launch_surfaces"],
        )
        assertEquals("true", interactive["embedded_script.long_running_requested"])
        assertEquals("false", interactive["embedded_script.long_running_supported"])

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
        val scheduledRunner = NodeExecutionModePolicy.nativeValues(
            "scheduled",
            NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
        )
        assertEquals("true", scheduledRunner["embedded_script.execution_mode_supported"])
        assertEquals("false", scheduledRunner["embedded_script.long_running_supported"])

        val unknown = NodeExecutionModePolicy.nativeValues("daemon")
        assertEquals(expectedKeys, unknown.keys)
        assertEquals("daemon", unknown["embedded_script.execution_mode"])
        assertEquals("false", unknown["embedded_script.execution_mode_supported"])
        assertEquals("none", unknown["embedded_script.execution_mode_supported_launch_surfaces"])
    }
}
