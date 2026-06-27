package org.autojs.autojs.engine

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NodeHardenedIsolatedRuntimePocInstrumentationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nodeSandboxedModeIsRecognizedButDesignGated() {
        val definition = NodeExecutionModePolicy.definition("nodeSandboxed")

        assertEquals(NodeExecutionModePolicy.MODE_NODE_SANDBOXED, definition.mode)
        assertEquals("design_gated", definition.status)
        assertFalse(definition.supported)
        assertTrue(definition.supportedLaunchSurfaces.isEmpty())
        assertTrue(definition.allowedProviders.isEmpty())
        assertEquals("broker_startup_and_script_timeout", definition.timeoutPolicy)
        assertEquals("host_owned_visibility_required", definition.foregroundRequirement)
        assertEquals("host_owned_stop_action_required", definition.notificationRequirement)
        assertEquals("never", definition.restartPolicy)
        assertEquals("hardened_sandbox_broker_only", definition.resourceQuotaProfile)
        assertEquals("broker_stop_then_isolated_process_destroy", definition.stopSemantics)
        assertTrue(definition.unsupportedReason.orEmpty().contains("Binder capability broker"))

        val nativeValues = NodeExecutionModePolicy.nativeValues("sandbox")
        assertEquals(NodeExecutionModePolicy.MODE_NODE_SANDBOXED, nativeValues["embedded_script.execution_mode"])
        assertEquals("true", nativeValues["embedded_script.sandboxed_runtime_requested"])
        assertEquals("false", nativeValues["embedded_script.sandboxed_runtime_supported"])
        assertEquals("none", nativeValues["embedded_script.execution_mode_allowed_providers"])
    }

    @Test
    fun sandboxedServiceManifestIsIsolatedAndNotExported() {
        val serviceInfo = serviceInfo(
            ComponentName(context, NativeNodeSandboxedScriptService::class.java),
        )

        assertNotNull("NativeNodeSandboxedScriptService must be present in the manifest", serviceInfo)
        serviceInfo!!
        assertFalse("Sandboxed Node service must not be exported", serviceInfo.exported)
        assertTrue(
            "Sandboxed Node service must use android:isolatedProcess=true; flags=${serviceInfo.flags}",
            serviceInfo.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0,
        )
        assertTrue(
            "Sandboxed Node service process should end with ${NativeNodeSandboxedScriptService.PROCESS_SUFFIX}: ${serviceInfo.processName}",
            serviceInfo.processName.endsWith(NativeNodeSandboxedScriptService.PROCESS_SUFFIX),
        )
        assertTrue("Sandboxed Node service must not require a caller permission", serviceInfo.permission.isNullOrBlank())
    }

    @Test
    fun directScriptNodeSandboxedRequestRejectsBeforeUserCode() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node sandboxed runtime POC")

        val workDir = File(context.cacheDir, "node-sandboxed-poc-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create sandboxed POC work dir: $absolutePath" }
        }
        val marker = File(workDir, "should-not-run.txt")

        try {
            val result = NativeNodeEmbeddedScriptService.runScript(
                context,
                NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                    source = """
                        "nodejs";
                        require("fs").writeFileSync("./should-not-run.txt", "ran", "utf8");
                    """.trimIndent(),
                    sourceName = File(workDir, "main.js").absolutePath,
                    workingDirectory = workDir.absolutePath,
                    timeoutMs = 10_000L,
                    executionMode = "nodeSandboxed",
                ),
            )

            assertFalse(NodeEmbeddedTestHarness.describeResult("Node sandboxed direct script", result), result.succeeded)
            assertFalse("Sandboxed policy rejection should not be a timeout", result.timedOut)
            assertEquals(NodeRuntimeErrorCodes.EMBEDDED_NODE_UNSUPPORTED_OPTION, result.errorCode)
            assertEquals(NodeExecutionModePolicy.MODE_NODE_SANDBOXED, result.nativeValues["embedded_script.execution_mode"])
            assertEquals("design_gated", result.nativeValues["embedded_script.execution_mode_status"])
            assertEquals("false", result.nativeValues["embedded_script.execution_mode_supported"])
            assertEquals("true", result.nativeValues["embedded_script.sandboxed_runtime_requested"])
            assertEquals("false", result.nativeValues["embedded_script.sandboxed_runtime_supported"])
            assertEquals("true", result.nativeValues["embedded_script.execution_mode_rejected"])
            assertTrue(result.errorMessage.orEmpty(), result.errorMessage.orEmpty().contains("Binder capability broker"))
            assertFalse("nodeSandboxed rejection must happen before user code", marker.exists())
        } finally {
            workDir.deleteRecursively()
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        }
    }

    private fun serviceInfo(componentName: ComponentName): ServiceInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getServiceInfo(
                    componentName,
                    PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getServiceInfo(componentName, PackageManager.GET_META_DATA)
            }
        }.getOrNull()
    }
}
