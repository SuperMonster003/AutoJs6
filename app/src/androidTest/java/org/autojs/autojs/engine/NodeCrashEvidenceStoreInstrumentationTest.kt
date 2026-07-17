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
    fun collectsRunningMarkerAfterSimulatedNativeFatalProcessDeath() {
        val executionId = "p13-native-fatal-marker-${System.nanoTime()}"
        val marker = NodeCrashEvidenceStore.begin(
            context = context,
            executionId = executionId,
            sourceName = "native-fatal-chaos.js",
            workingDirectory = context.cacheDir.absolutePath,
            executionMode = "native_process",
            launchSurface = "native_process_service",
            timeoutMs = null,
            runtimeAdapter = "desktop_node_main",
            runtimeSlotId = "fatal-chaos-simulated",
            scenario = "native_fatal_running_marker",
        )
        assertNotNull(marker)
        marker ?: return
        val nextLaunchBundle = File(
            File(context.filesDir, "nodejs/crash-evidence/next-launch"),
            "${marker.id}.json",
        )
        try {
            val running = JSONObject(marker.file.readText(Charsets.UTF_8))
            assertEquals(NodeCrashEvidenceStore.STATE_RUNNING, running.getString("state"))
            assertEquals("native_fatal_running_marker", running.getString("scenario"))
            assertEquals("fatal-chaos-simulated", running.getString("runtimeSlotId"))

            val collected = NodeCrashEvidenceStore.collectPending(context, "instrumentation.native_fatal_simulated")
            assertTrue(
                "next-launch collection should include running marker ${marker.id}: $collected",
                (0 until collected.length()).any { index ->
                    collected.getJSONObject(index).getString("markerId") == marker.id
                },
            )

            val collectedMarker = JSONObject(marker.file.readText(Charsets.UTF_8))
            assertEquals(NodeCrashEvidenceStore.STATE_COLLECTED_AFTER_RESTART, collectedMarker.getString("state"))
            assertEquals("instrumentation.native_fatal_simulated", collectedMarker.getString("collectionReason"))
            assertTrue(collectedMarker.getBoolean("nextLaunchCollection"))
            assertTrue(nextLaunchBundle.isFile)
            val bundle = JSONObject(nextLaunchBundle.readText(Charsets.UTF_8))
            assertEquals(NodeCrashEvidenceStore.NEXT_LAUNCH_BUNDLE_SCHEMA, bundle.getString("schema"))
            assertEquals(NodeCrashEvidenceStore.STATE_COLLECTED_AFTER_RESTART, bundle.getJSONObject("marker").getString("state"))
        } finally {
            marker.file.delete()
            nextLaunchBundle.delete()
        }
    }

    @Test
    fun collectsRunningMarkerAfterRealFatalNativeChaosProbeDeath() {
        val executionId = "p13-native-fatal-chaos-${System.nanoTime()}"
        val result = NativeNodeEmbeddedProbeService.runFatalCrashEvidenceProbe(
            context = context,
            executionId = executionId,
            timeoutMs = 3_000L,
        )
        assertTrue(
            "fatal crash probe should report process death: ${result.formattedLog}",
            !result.succeeded,
        )
        assertTrue(result.errorMessage.orEmpty().contains("crashed or timed out"))

        val collected = NodeCrashEvidenceStore.collectPending(context, "instrumentation.native_fatal_chaos_probe")
        val collectedMarker = (0 until collected.length())
            .asSequence()
            .map { index -> collected.getJSONObject(index) }
            .firstOrNull { item -> item.optString("executionId") == executionId }
        assertNotNull(
            "next-launch collection should include fatal chaos execution $executionId: $collected",
            collectedMarker,
        )
        collectedMarker ?: return

        val markerId = collectedMarker.getString("markerId")
        val markerFile = File(
            File(context.filesDir, "nodejs/crash-evidence/markers"),
            "$markerId.json",
        )
        val nextLaunchBundle = File(
            File(context.filesDir, "nodejs/crash-evidence/next-launch"),
            "$markerId.json",
        )
        try {
            assertTrue(markerFile.isFile)
            assertTrue(nextLaunchBundle.isFile)
            val marker = JSONObject(markerFile.readText(Charsets.UTF_8))
            assertEquals(NodeCrashEvidenceStore.STATE_COLLECTED_AFTER_RESTART, marker.getString("state"))
            assertEquals("instrumentation.native_fatal_chaos_probe", marker.getString("collectionReason"))
            assertEquals(executionId, marker.getString("executionId"))
            assertEquals("native_fatal_chaos_injection", marker.getString("scenario"))
            assertEquals("native_process", marker.getString("executionMode"))
            assertEquals("embedded_probe_service", marker.getString("launchSurface"))
            assertEquals(":nodejs_probe", marker.getString("runtimeSlotId"))
            assertTrue(marker.getString("sourceName").contains("sigkill"))
            val bundle = JSONObject(nextLaunchBundle.readText(Charsets.UTF_8))
            assertEquals(NodeCrashEvidenceStore.NEXT_LAUNCH_BUNDLE_SCHEMA, bundle.getString("schema"))
            assertEquals(markerId, bundle.getString("markerId"))
        } finally {
            markerFile.delete()
            nextLaunchBundle.delete()
        }
    }

    @Test
    fun markerRecordsPackagedLaunchSurfaceForFutureCrashSmoke() {
        val executionId = "p13-packaged-crash-marker-${System.nanoTime()}"
        val marker = NodeCrashEvidenceStore.begin(
            context = context,
            executionId = executionId,
            sourceName = "packaged-long-running.js",
            workingDirectory = context.cacheDir.absolutePath,
            executionMode = NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
            launchSurface = NodeExecutionModePolicy.LAUNCH_SURFACE_PACKAGED_LONG_RUNNING,
            timeoutMs = 30_000L,
            runtimeAdapter = "adapter_v1",
            runtimeSlotId = "packaged-smoke-contract",
            scenario = "packaged_apk_crash_smoke_contract",
        )
        assertNotNull(marker)
        marker ?: return
        try {
            val json = JSONObject(marker.file.readText(Charsets.UTF_8))
            assertEquals(NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING, json.getString("executionMode"))
            assertEquals(NodeExecutionModePolicy.LAUNCH_SURFACE_PACKAGED_LONG_RUNNING, json.getString("launchSurface"))
            assertEquals("packaged_apk_crash_smoke_contract", json.getString("scenario"))
            assertEquals("packaged-smoke-contract", json.getString("runtimeSlotId"))
        } finally {
            marker.file.delete()
        }
    }

    @Test
    fun markerWritesSignalSafeFixedSizeNativeRecord() {
        val executionId = "p13-signal-marker-native-${System.nanoTime()}"
        val marker = NodeCrashEvidenceStore.begin(
            context = context,
            executionId = executionId,
            sourceName = "signal-safe-marker-native.js",
            workingDirectory = context.cacheDir.absolutePath,
            executionMode = NodeExecutionModePolicy.MODE_ONE_SHOT,
            launchSurface = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
            timeoutMs = 5_000L,
            runtimeAdapter = "adapter_v1",
            runtimeSlotId = "signal-safe-native",
            scenario = "signal_safe_fixed_size_marker",
        )
        assertNotNull(marker)
        marker ?: return
        var proof: NodeCrashEvidenceStore.SignalSafeMarkerProof? = null
        try {
            proof = NodeCrashEvidenceStore.writeSignalSafeNativeMarker(
                context = context,
                marker = marker,
                reason = "instrumentation.signal_safe_fixed_size_marker",
            )
            assertNotNull(proof)
            proof ?: return
            assertTrue("native writer should succeed: ${proof.result}", proof.result.succeeded)
            assertEquals(NodeCrashEvidenceStore.SAFE_SIGNAL_MARKER_RECORD_BYTES, proof.result.recordBytes)
            assertEquals(NodeCrashEvidenceStore.SAFE_SIGNAL_MARKER_RECORD_BYTES, proof.result.bytesWritten)
            assertTrue(proof.file.isFile)
            val recordBytes = proof.file.readBytes()
            assertEquals(NodeCrashEvidenceStore.SAFE_SIGNAL_MARKER_RECORD_BYTES, recordBytes.size)
            val record = recordBytes.toString(Charsets.UTF_8)
            assertTrue(record.contains("AJ6SIG13"))
            assertTrue(record.contains(NodeCrashEvidenceStore.SAFE_SIGNAL_MARKER_SCHEMA))
            assertTrue(record.contains("native_fixed_size_signal_safe_writer"))
            assertTrue(record.contains("signal_safe_fixed_size_marker"))

            val json = JSONObject(marker.file.readText(Charsets.UTF_8))
            val signalMarker = json.getJSONObject("safeSignalMarker")
            assertEquals(true, signalMarker.getBoolean("implemented"))
            assertEquals(NodeCrashEvidenceStore.SAFE_SIGNAL_MARKER_SCHEMA, signalMarker.getString("schema"))
            assertEquals(NodeCrashEvidenceStore.SAFE_SIGNAL_MARKER_RECORD_BYTES, signalMarker.getInt("recordBytes"))
            assertEquals(NodeCrashEvidenceStore.SAFE_SIGNAL_MARKER_RECORD_BYTES, signalMarker.getInt("bytesWritten"))
            assertEquals("native_fixed_size_signal_safe_writer", signalMarker.getString("writer"))
            assertEquals("written", signalMarker.getString("status"))
            assertEquals("open_write_close_fixed_buffer", signalMarker.getString("nativeCore"))
            assertEquals("open|write|close", signalMarker.getString("signalSafeSyscalls"))
        } finally {
            marker.file.delete()
            proof?.file?.delete()
        }
    }

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
