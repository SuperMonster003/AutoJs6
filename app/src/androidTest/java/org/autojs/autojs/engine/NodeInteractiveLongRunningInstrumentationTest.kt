package org.autojs.autojs.engine

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeInteractiveLongRunningInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var activeExecutionId: String? = null

    @Before
    fun setUp() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("interactive long-running foreground runtime tests")
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun tearDown() {
        activeExecutionId?.let { executionId ->
            NativeNodeEmbeddedScriptService.stopInteractiveLongRunning(context, executionId)
        }
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        activeExecutionId = null
    }

    @Test
    fun interactiveForegroundRuntimeReportsStatusAndStopsCleanly() {
        val workingDirectory = File(context.cacheDir, "node-interactive-long-running-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
        val markerFile = File(workingDirectory, "runtime-alive.txt")
        val source = """
            const fs = require("fs");
            fs.writeFileSync("./runtime-alive.txt", "running");
            setInterval(() => {}, 1000);
        """.trimIndent()

        val session = NativeNodeEmbeddedScriptService.startInteractiveLongRunning(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = source,
                sourceName = "interactive-long-running-status.js",
                workingDirectory = workingDirectory.absolutePath,
                timeoutMs = 60_000L,
            ),
        )
        activeExecutionId = session.executionId

        assertTrue("interactive session failed to start: ${session.error}", session.started)
        assertNotNull(session.status)
        val running = waitForRunningStatus(session.executionId)
        waitUntil("marker file is written; status=$running") { markerFile.isFile }
        assertEquals(NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING, running.optString("executionMode"))
        assertEquals(NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION, running.optString("launchSurface"))
        assertEquals("never", running.optString("restartPolicy"))
        assertFalse(running.optBoolean("automaticRestart", true))
        val runningDurationMs = running.optLong("durationMs")
        assertTrue("durationMs=$runningDurationMs", runningDurationMs >= 0L)
        assertNotNull(running.optJSONObject("activeResources"))

        sendAppToBackground()
        val stillRunning = waitForStatus(session.executionId) {
            it.optString("state") == NodeInteractiveLongRunningStatusStore.STATE_RUNNING
        }
        assertTrue("runtime duration should advance", stillRunning.optLong("durationMs") >= runningDurationMs)
        assertTrue("marker should remain after UI leaves foreground", markerFile.isFile)

        assertTrue(NativeNodeEmbeddedScriptService.stopInteractiveLongRunning(context, session.executionId))
        val stopped = waitForStatus(session.executionId) {
            it.optString("state") == NodeInteractiveLongRunningStatusStore.STATE_STOPPED
        }
        assertTrue(stopped.optBoolean("stopRequested"))
        activeExecutionId = null

        val recovery = runRecoveryAfterStop(workingDirectory)
        assertTrue(
            "recovery script failed stdout=${recovery.stdout} stderr=${recovery.stderr} error=${recovery.errorName}: ${recovery.errorMessage}",
            recovery.succeeded,
        )
    }

    @Test
    fun interactiveRuntimeCheckpointsOnStopAndRestoresOnlyWhenExplicitlyRequested() {
        val workingDirectory = File(context.cacheDir, "node-interactive-checkpoint-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
        val aliveFile = File(workingDirectory, "checkpoint-runtime-alive.txt")
        val checkpointStopFile = File(workingDirectory, "checkpoint-stop.txt")
        val restoreFile = File(workingDirectory, "checkpoint-restored.json")

        val source = """
            const fs = require("fs");
            const lifecycle = require("autojs6:lifecycle");
            let ticks = 0;
            const timer = setInterval(() => { ticks += 1; }, 50);
            lifecycle.onStop(async (reason) => {
              await lifecycle.checkpoint({
                ticks,
                reason,
                source: "stop-hook"
              }, { reason: "interactive.stop" });
              fs.writeFileSync("./checkpoint-stop.txt", reason);
              clearInterval(timer);
            });
            fs.writeFileSync("./checkpoint-runtime-alive.txt", "running");
        """.trimIndent()

        val session = NativeNodeEmbeddedScriptService.startInteractiveLongRunning(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = source,
                sourceName = "interactive-checkpoint-stop.js",
                workingDirectory = workingDirectory.absolutePath,
                timeoutMs = 60_000L,
            ),
        )
        activeExecutionId = session.executionId
        assertTrue("interactive checkpoint session failed to start: ${session.error}", session.started)
        waitForRunningStatus(session.executionId)
        waitUntil("checkpoint runtime marker") { aliveFile.isFile }

        assertTrue(NativeNodeEmbeddedScriptService.stopInteractiveLongRunning(context, session.executionId))
        waitUntil("onStop checkpoint marker") { checkpointStopFile.isFile }
        val stopped = waitForStatus(session.executionId) {
            it.optString("state") == NodeInteractiveLongRunningStatusStore.STATE_STOPPED
        }
        assertTrue(stopped.optBoolean("stopRequested"))
        val checkpointStatus = stopped.getJSONObject("checkpoint")
        assertTrue("checkpoint status=$checkpointStatus", checkpointStatus.getBoolean("exists"))
        assertEquals("interactive.stop", checkpointStatus.getString("reason"))
        activeExecutionId = null
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val restoreSource = """
            const fs = require("fs");
            const lifecycle = require("autojs6:lifecycle");
            setTimeout(async () => {
              await lifecycle.onRestore((checkpoint, record) => {
                fs.writeFileSync("./checkpoint-restored.json", JSON.stringify({
                  checkpoint,
                  recordReason: record.reason,
                  executionMode: record.executionMode,
                  launchSurface: record.launchSurface,
                  automaticRestart: lifecycle.policy.automaticRestart,
                  restartPolicy: lifecycle.policy.restartPolicy
                }));
              });
            }, 0);
        """.trimIndent()
        val restoreSession = NativeNodeEmbeddedScriptService.startInteractiveLongRunning(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = restoreSource,
                sourceName = "interactive-checkpoint-restore.js",
                workingDirectory = workingDirectory.absolutePath,
                timeoutMs = 20_000L,
            ),
        )
        activeExecutionId = restoreSession.executionId
        assertTrue("interactive restore session failed to start: ${restoreSession.error}", restoreSession.started)
        waitUntil("explicit restore output") { restoreFile.isFile }
        val restored = JSONObject(restoreFile.readText())
        assertEquals("interactive.stop", restored.getString("recordReason"))
        assertEquals(NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING, restored.getString("executionMode"))
        assertEquals(NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION, restored.getString("launchSurface"))
        assertFalse(restored.getBoolean("automaticRestart"))
        assertEquals("never", restored.getString("restartPolicy"))
        assertEquals("stop-hook", restored.getJSONObject("checkpoint").getString("source"))
        assertEquals("interactive.stop.requested", restored.getJSONObject("checkpoint").getString("reason"))
        val restoreStopped = waitForStatus(restoreSession.executionId) {
            it.optString("state") == NodeInteractiveLongRunningStatusStore.STATE_STOPPED
        }
        assertFalse(restoreStopped.optBoolean("automaticRestart", true))
        activeExecutionId = null
    }

    private fun runRecoveryAfterStop(
        workingDirectory: File,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        var last: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult? = null
        waitUntil("interactive service process releases execution slot") {
            val result = NativeNodeEmbeddedScriptService.runScript(
                context,
                NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                    source = "console.log('interactive recovery ok');",
                    sourceName = "interactive-long-running-recovery.js",
                    workingDirectory = workingDirectory.absolutePath,
                    timeoutMs = 10_000L,
                ),
            )
            last = result
            result.succeeded || result.errorName != "EmbeddedNodeEngineBusyError"
        }
        val result = last
        if (result != null) {
            return result
        }
        fail("recovery result not available")
        throw AssertionError("recovery result not available")
    }

    private fun waitForStatus(
        executionId: String,
        predicate: (JSONObject) -> Boolean,
    ): JSONObject {
        var last: JSONObject? = null
        waitUntil("status predicate for $executionId") {
            last = NativeNodeEmbeddedScriptService.interactiveLongRunningStatus(context, executionId)
            last?.let(predicate) == true
        }
        val result = last
        if (result != null) {
            return result
        }
        fail("status not available for $executionId")
        throw AssertionError("status not available for $executionId")
    }

    private fun waitForRunningStatus(executionId: String): JSONObject {
        var last: JSONObject? = null
        waitUntil("running status for $executionId") {
            last = NativeNodeEmbeddedScriptService.interactiveLongRunningStatus(context, executionId)
            val state = last?.optString("state").orEmpty()
            if (state in TERMINAL_STATES) {
                fail("interactive runtime reached terminal state before running: $last")
            }
            state == NodeInteractiveLongRunningStatusStore.STATE_RUNNING
        }
        val result = last
        if (result != null) {
            return result
        }
        fail("running status not available for $executionId")
        throw AssertionError("running status not available for $executionId")
    }

    private fun waitUntil(label: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + WAIT_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) {
                return
            }
            SystemClock.sleep(WAIT_POLL_MS)
        }
        fail("Timed out waiting for $label")
    }

    private fun sendAppToBackground() {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        SystemClock.sleep(500L)
    }

    private companion object {

        private const val WAIT_TIMEOUT_MS = 15_000L
        private const val WAIT_POLL_MS = 100L
        private val TERMINAL_STATES = setOf(
            NodeInteractiveLongRunningStatusStore.STATE_STOPPED,
            NodeInteractiveLongRunningStatusStore.STATE_FAILED,
            NodeInteractiveLongRunningStatusStore.STATE_TIMED_OUT,
        )
    }
}
