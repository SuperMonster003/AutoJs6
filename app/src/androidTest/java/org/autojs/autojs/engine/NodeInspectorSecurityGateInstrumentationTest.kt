package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

class NodeInspectorSecurityGateInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun releaseAndPackagedMetadataCannotEnableInspector() {
        val releaseAudit = NodeRuntimeCapabilities.auditBuildTimeFeatureFlags(
            requestedFlags = mapOf(
                "inspector" to true,
            ),
            releaseBuild = true,
            source = "release-build",
        )
        assertTrue(releaseAudit.hasRejectedOverrides)
        assertEquals(listOf("inspector"), releaseAudit.rejectedFeatureIds)
        assertTrue(releaseAudit.rejected.all { it.reason == "release_forbidden" })
        assertTrue(releaseAudit.accepted.isEmpty())

        val projectAudit = NodeRuntimeCapabilities.auditProjectJsonFeatureFlags(
            """
            {
              "type": "node",
              "node": {
                "featureFlags": {
                  "inspector": true
                },
                "inspector": {
                  "enabled": true,
                  "host": "0.0.0.0",
                  "port": 9229
                }
              }
            }
            """.trimIndent(),
            source = "packaged-project.json",
        )
        assertTrue(projectAudit.hasRejectedOverrides)
        assertEquals(listOf("inspector"), projectAudit.rejectedFeatureIds)
        assertTrue(projectAudit.rejected.all { it.reason == "project_json_override_denied" })
        assertTrue(projectAudit.accepted.isEmpty())

        val packageAudit = NodeRuntimeCapabilities.auditPackageJsonFeatureFlags(
            """
            {
              "name": "inspector-security-package",
              "autojs6": {
                "node": {
                  "featureFlags": {
                    "inspector": true
                  },
                  "inspector": {
                    "enabled": true,
                    "host": "0.0.0.0",
                    "port": 9229
                  }
                }
              }
            }
            """.trimIndent(),
            source = "packaged-package.json",
        )
        assertTrue(packageAudit.hasRejectedOverrides)
        assertEquals(listOf("inspector"), packageAudit.rejectedFeatureIds)
        assertTrue(packageAudit.rejected.all { it.reason == "packaged_apk_override_denied" })
        assertTrue(packageAudit.accepted.isEmpty())
    }

    @Test
    fun nonLoopbackInspectorHostsRemainInertAndLeaveNoListeningPort() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node inspector security host gate")

        listOf("0.0.0.0", "192.0.2.10", "::", "[::]").forEach { host ->
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
            val port = reserveLoopbackPort()
            assertLoopbackPortCanBeBound(port)

            val result = runInspectorHostAttempt(
                label = "non-loopback-${sanitizeLabel(host)}",
                nodeOptions = "--inspect=$host:$port",
                port = port,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node inspector non-loopback host $host", result)
            assertTrue(result.stdout, result.stdout.contains("inspector.security.host=true/true/true"))
            assertNoInspectorDiagnostics(result)
            assertLoopbackPortCanBeBound(port)
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        }
    }

    @Test
    fun inspectorPortIsNotLeftListeningAfterScriptStopOrTimeout() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node inspector security port gate")

        val successPort = reserveLoopbackPort()
        val success = runInspectorHostAttempt(
            label = "loopback-success",
            nodeOptions = "--inspect=127.0.0.1:$successPort",
            port = successPort,
        )
        NodeEmbeddedTestHarness.assertSucceeded("Node inspector loopback success", success)
        assertNoInspectorDiagnostics(success)
        assertLoopbackPortCanBeBound(successPort)
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val timeoutPort = reserveLoopbackPort()
        val timeoutWorkDir = freshWorkDir("timeout")
        val timeout = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const fs = require("fs");
                fs.writeFileSync("./timeout-started.txt", "started", "utf8");
                setInterval(() => {}, 100);
                console.log("inspector.security.timeout.started");
            """.trimIndent(),
            sourceName = File(timeoutWorkDir, "main.js").absolutePath,
            workingDirectory = timeoutWorkDir,
            env = mapOf(
                "NODE_OPTIONS" to "--inspect=127.0.0.1:$timeoutPort",
            ),
            timeoutMs = 900L,
        )
        assertFalse(NodeEmbeddedTestHarness.describeResult("Node inspector timeout", timeout), timeout.succeeded)
        assertTrue(NodeEmbeddedTestHarness.describeResult("Node inspector timeout", timeout), timeout.timedOut)
        assertEquals("ERR_AUTOJS6_EMBEDDED_NODE_SCRIPT_TIMEOUT", timeout.errorCode)
        assertTrue("Timeout script should have reached user code before timing out", File(timeoutWorkDir, "timeout-started.txt").isFile)
        assertNoInspectorDiagnostics(timeout)
        assertLoopbackPortCanBeBound(timeoutPort)
    }

    @Test
    fun profileArtifactsRemainAbsentAndDoNotLeakSensitiveMetadata() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node inspector profile redaction gate")

        val secret = "S8_40_PROJECT_SECRET_SHOULD_NOT_LEAK"
        val workDir = freshWorkDir("profile-redaction")
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const secret = "$secret";
                globalThis.__s8_40_secret = secret;
                console.log("inspector.security.profile=done");
            """.trimIndent(),
            sourceName = File(workDir, "main.js").absolutePath,
            workingDirectory = workDir,
            env = mapOf(
                "NODE_OPTIONS" to "--inspect=127.0.0.1:${reserveLoopbackPort()}",
            ),
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("Node inspector profile redaction", result)
        assertFalse(result.stdout, result.stdout.contains(secret))
        assertFalse(result.stderr, result.stderr.contains(secret))
        assertFalse(result.errorMessage.orEmpty(), result.errorMessage.orEmpty().contains(secret))
        assertFalse(result.errorStack.orEmpty(), result.errorStack.orEmpty().contains(secret))
        assertFalse(result.nativeValues.toString(), result.nativeValues.toString().contains(secret))
        assertTrue(result.stdout, result.stdout.contains("inspector.security.profile=done"))
        assertNoInspectorDiagnostics(result)
        assertTrue("Inspector profile artifacts should be absent under ${workDir.absolutePath}", findProfileArtifacts(workDir).isEmpty())
    }

    private fun runInspectorHostAttempt(
        label: String,
        nodeOptions: String,
        port: Int,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val workDir = freshWorkDir(label)
        return NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const checks = [
                  process.env.NODE_OPTIONS === undefined,
                  Array.isArray(process.execArgv) && process.execArgv.length === 0,
                  process.features.inspector === false && process.features.debug === false
                ];
                console.log("inspector.security.host=" + checks.join("/"));
            """.trimIndent(),
            sourceName = File(workDir, "main.js").absolutePath,
            workingDirectory = workDir,
            env = mapOf(
                "NODE_OPTIONS" to nodeOptions,
                "NODE_INSPECT_RESUME_ON_START" to "1",
                "INSPECTOR_SECURITY_GATE_PORT" to port.toString(),
            ),
            timeoutMs = 10_000L,
        )
    }

    private fun freshWorkDir(label: String): File {
        return File(context.cacheDir, "node-inspector-security-${sanitizeLabel(label)}-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create inspector security work dir: $absolutePath" }
        }
    }

    private fun sanitizeLabel(label: String): String {
        return label.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    private fun reserveLoopbackPort(): Int {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
            return socket.localPort
        }
    }

    private fun assertLoopbackPortCanBeBound(port: Int) {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
        }
    }

    private fun assertNoInspectorDiagnostics(result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult) {
        assertFalse(
            "Inspector diagnostics should not appear while the security gate is disabled: ${result.nativeValues}",
            result.nativeValues.keys.any { it.contains("inspector", ignoreCase = true) },
        )
    }

    private fun findProfileArtifacts(root: File): List<File> {
        if (!root.exists()) {
            return emptyList()
        }
        return root.walkTopDown()
            .filter { file ->
                file.isFile && (
                    file.extension == "cpuprofile" ||
                        file.extension == "heapsnapshot" ||
                        file.name.endsWith(".allocation.json") ||
                        file.name.endsWith(".profile-summary.json")
                    )
            }
            .toList()
    }
}
