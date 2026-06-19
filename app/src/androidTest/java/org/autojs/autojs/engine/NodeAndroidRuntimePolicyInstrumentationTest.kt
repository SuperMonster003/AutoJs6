package org.autojs.autojs.engine

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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeAndroidRuntimePolicyInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var activeExecutionId: String? = null

    @Before
    fun setUp() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Android runtime policy tests")
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
    fun embeddedNodeServiceManifestDeclaresExpectedForegroundPolicy() {
        val diagnostics = NodeAndroidRuntimePolicy.embeddedServiceDiagnostics(
            context,
            NativeNodeEmbeddedScriptService::class.java,
            requestedForegroundServiceType(),
        )
        assertTrue(diagnostics.toString(), diagnostics.getBoolean("manifestServiceFound"))
        assertTrue(diagnostics.toString(), diagnostics.getBoolean("foregroundServiceTypeValid"))
        assertTrue(diagnostics.toString(), diagnostics.getBoolean("declaresForegroundServicePermission"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            assertTrue(diagnostics.toString(), diagnostics.getBoolean("declaresForegroundServiceSpecialUsePermission"))
            assertTrue(diagnostics.getString("manifestForegroundServiceTypeNames").contains("specialUse"))
        }

        val table = NodeAndroidRuntimePolicy.apiLevelPolicyTable()
        val ids = (0 until table.length()).map { table.getJSONObject(it).getString("id") }.toSet()
        assertTrue(ids.contains("foreground_service_on_timeout"))
        assertTrue(ids.contains("workmanager_long_running_job_quota"))
    }

    @Test
    fun foregroundServiceStartAndTypeDiagnosticsAreStable() {
        val backgroundDenied = NodeAndroidRuntimePolicy.serviceStartFailureDiagnosticsForClass(
            className = "android.app.ForegroundServiceStartNotAllowedException",
            message = "background start denied",
            serviceClassName = NativeNodeEmbeddedScriptService::class.java.name,
            sdkInt = Build.VERSION_CODES.S,
        )
        assertEquals(
            NodeRuntimeErrorCodes.FOREGROUND_SERVICE_START_NOT_ALLOWED,
            backgroundDenied.getString("errorCode"),
        )
        assertEquals(NodeAndroidRuntimePolicy.START_FAILURE_BACKGROUND_RESTRICTED, backgroundDenied.getString("reason"))

        val missing = NodeAndroidRuntimePolicy.validateForegroundServiceType(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            manifestType = 0,
            requestedType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        assertFalse(missing.valid)
        assertEquals(NodeAndroidRuntimePolicy.START_FAILURE_MISSING_TYPE, missing.issue)

        val invalid = NodeAndroidRuntimePolicy.validateForegroundServiceType(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            manifestType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            requestedType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        assertFalse(invalid.valid)
        assertEquals(NodeAndroidRuntimePolicy.START_FAILURE_INVALID_TYPE, invalid.issue)
    }

    @Test
    fun workManagerQuotaDiagnosticsExposeAndroid16ExhaustionPath() {
        val current = NodeAndroidRuntimePolicy.workManagerQuotaDiagnostics()
        assertEquals(NodeWorkManagerBridgeScheduler.SCHEDULER_NAME, current.getString("scheduler"))
        assertTrue(current.getBoolean("longRunningWorkerUsesForegroundService"))
        assertTrue(current.has("quotaState"))

        val exhausted = NodeAndroidRuntimePolicy.workManagerQuotaDiagnostics(
            sdkInt = 36,
            quotaExhausted = true,
        )
        assertEquals(NodeAndroidRuntimePolicy.QUOTA_STATE_EXHAUSTED, exhausted.getString("quotaState"))
        assertEquals(NodeRuntimeErrorCodes.WORK_MANAGER_QUOTA_EXHAUSTED, exhausted.getString("errorCode"))
        assertTrue(exhausted.getString("recommendedAction").contains("foreground runtime"))
    }

    @Test
    fun simulatedForegroundServiceTimeoutStopsInteractiveRuntime() {
        val workingDirectory = File(context.cacheDir, "node-android-fgs-timeout-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Android FGS timeout directory: $absolutePath" }
        }
        val source = """
            const fs = require("fs");
            fs.writeFileSync("./fgs-timeout-alive.txt", "running");
            setInterval(() => {}, 1000);
        """.trimIndent()
        val session = NativeNodeEmbeddedScriptService.startInteractiveLongRunning(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = source,
                sourceName = "interactive-foreground-timeout.js",
                workingDirectory = workingDirectory.absolutePath,
                timeoutMs = 60_000L,
            ),
        )
        activeExecutionId = session.executionId
        assertTrue("interactive session failed to start: ${session.error}", session.started)
        waitForStatus(session.executionId) {
            it.optString("state") == NodeInteractiveLongRunningStatusStore.STATE_RUNNING
        }

        assertTrue(
            NativeNodeEmbeddedScriptService.simulateForegroundServiceTimeoutForTest(
                context,
                session.executionId,
                requestedForegroundServiceType(),
            ),
        )
        val timedOut = waitForStatus(session.executionId) {
            it.optString("state") == NodeInteractiveLongRunningStatusStore.STATE_TIMED_OUT
        }
        assertTrue(timedOut.optBoolean("stopRequested"))
        assertTrue(timedOut.optString("message").contains("foreground service timeout"))
        assertEquals("true", timedOut.getJSONObject("nativeValues").getString("embedded_script.foreground_service_timeout"))
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        activeExecutionId = null

        val recovery = runRecoveryAfterTimeout(workingDirectory)
        assertTrue(
            "recovery script failed stdout=${recovery.stdout} stderr=${recovery.stderr} error=${recovery.errorName}: ${recovery.errorMessage}",
            recovery.succeeded,
        )
    }

    private fun runRecoveryAfterTimeout(
        workingDirectory: File,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        var last: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult? = null
        waitUntil("foreground timeout releases execution slot", timeoutMs = 20_000L) {
            val result = NativeNodeEmbeddedScriptService.runScript(
                context,
                NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                    source = "console.log('fgs timeout recovery ok');",
                    sourceName = "foreground-timeout-recovery.js",
                    workingDirectory = workingDirectory.absolutePath,
                    timeoutMs = 10_000L,
                ),
            )
            last = result
            result.succeeded || result.errorName != "EmbeddedNodeEngineBusyError"
        }
        return last ?: fail("recovery result not available").let {
            throw AssertionError("recovery result not available")
        }
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
        return last ?: fail("status not available for $executionId").let {
            throw AssertionError("status not available for $executionId")
        }
    }

    private fun waitUntil(
        label: String,
        timeoutMs: Long = 15_000L,
        predicate: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) {
                return
            }
            SystemClock.sleep(100L)
        }
        fail("Timed out waiting for $label")
    }

    private fun requestedForegroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            org.autojs.autojs.util.ForegroundServiceUtils.FOREGROUND_SERVICE_TYPE_UNKNOWN
        }
    }
}
