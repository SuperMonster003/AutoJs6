package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NodeCrashEvidenceStoreInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun markerRecordsBridgeProviderCrashBundleFields() {
        val executionId = "p13-crash-evidence-${System.nanoTime()}"
        val marker = NodeCrashEvidenceStore.begin(
            context = context,
            executionId = executionId,
            sourceName = "bridge-provider-crash.js",
            workingDirectory = context.cacheDir.absolutePath,
            executionMode = NodeExecutionModePolicy.MODE_ONE_SHOT,
            launchSurface = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
            timeoutMs = 5_000L,
            runtimeAdapter = "adapter_v1",
            runtimeSlotId = "instrumentation",
            scenario = "bridge_provider_crash",
        )
        assertNotNull(marker)
        marker ?: return
        val nextLaunchBundle = File(
            File(context.filesDir, "nodejs/crash-evidence/next-launch"),
            "${marker.id}.json",
        )
        try {
            val initial = JSONObject(marker.file.readText(Charsets.UTF_8))
            assertEquals(NodeCrashEvidenceStore.MARKER_SCHEMA, initial.getString("schema"))
            assertEquals(executionId, initial.getString("executionId"))
            assertTrue(initial.getString("runtimeSlot").isNotBlank())
            assertTrue(initial.getString("abi").isNotBlank())
            assertEquals(NodeRuntimeCapabilities.PROFILE_NAME, initial.getString("profile"))
            assertTrue(initial.getJSONArray("featureFlags").length() >= NodeRuntimeCapabilities.featureFlags.size)

            val timeline = JSONArray().put(JSONObject().apply {
                put("name", "plugin_crash")
                put("details", JSONObject().put("executionId", executionId))
            })
            val nativeValues = mapOf(
                "plugin_crash.has_crash_event" to "true",
                "plugin_crash.error_code" to "ERR_AUTOJS6_PLUGIN_CRASH",
                "plugin_crash.error_message_contains_crash" to "true",
                "embedded_script.bridge_resource.active_count" to "0",
                "embedded_script.timeline_json" to timeline.toString(),
            )
            NodeCrashEvidenceStore.markEmbeddedResult(
                context,
                marker,
                NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
                    succeeded = false,
                    exitCode = 1,
                    resultText = null,
                    stdout = "",
                    stderr = "",
                    errorName = "PluginCrashError",
                    errorMessage = "Runtime crash detected",
                    errorStack = null,
                    elapsedMs = 25L,
                    processName = "instrumentation",
                    pid = 1,
                    sourceName = "bridge-provider-crash.js",
                    nativeValues = nativeValues,
                    workingDirectory = context.cacheDir.absolutePath,
                ),
            )

            val terminal = JSONObject(marker.file.readText(Charsets.UTF_8))
            assertEquals(NodeCrashEvidenceStore.STATE_FAILED, terminal.getString("state"))
            assertEquals("autojs6-node-final-crash-resource-snapshot-v1", terminal.getJSONObject("resourceSnapshot").getString("schema"))
            assertTrue(terminal.getJSONObject("resourceSnapshot").getBoolean("implemented"))
            assertEquals("autojs6-node-final-crash-timeline-tail-v1", terminal.getJSONObject("timelineTail").getString("schema"))
            assertEquals(1, terminal.getJSONObject("timelineTail").getInt("eventCount"))

            val collected = NodeCrashEvidenceStore.collectPending(context, "instrumentation.bridge_provider_crash")
            assertTrue(
                "next-launch collection should include ${marker.id}: $collected",
                (0 until collected.length()).any { index ->
                    collected.getJSONObject(index).getString("markerId") == marker.id
                },
            )
            assertTrue(nextLaunchBundle.isFile)
            val bundle = JSONObject(nextLaunchBundle.readText(Charsets.UTF_8))
            assertEquals(NodeCrashEvidenceStore.NEXT_LAUNCH_BUNDLE_SCHEMA, bundle.getString("schema"))
            assertEquals(marker.id, bundle.getString("markerId"))
        } finally {
            marker.file.delete()
            nextLaunchBundle.delete()
        }
    }
}
