package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeWebSocketDesignGateInstrumentationTest {

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
    fun websocketRemainsPlannedAndProviderBackedOnly() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("websocket design gate")
        val websocketFlag = NodeRuntimeCapabilities.featureFlagsById["websocket"]
        assertEquals("planned", websocketFlag?.status)
        assertEquals("high", websocketFlag?.securityLevel)
        assertFalse(websocketFlag?.defaultEnabled ?: true)
        assertFalse(websocketFlag?.buildTimeOverrideAllowed ?: true)
        assertFalse(websocketFlag?.projectJsonOverrideAllowed ?: true)
        assertFalse(websocketFlag?.packagedApkOverrideAllowed ?: true)

        val emptyWorkDir = File(context.cacheDir, "node-websocket-design-empty-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create websocket design empty work dir: $absolutePath" }
        }
        val packageWorkDir = File(context.cacheDir, "node-websocket-design-package-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create websocket design package work dir: $absolutePath" }
        }

        try {
            val missingResult = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const profile = require("autojs6:profile");
                    function codeOf(error) {
                      return String(error && (error.autojs6Code || error.code || error.legacyCode || error.name || ""));
                    }
                    function expectMissing(label, action) {
                      try {
                        action();
                      } catch (error) {
                        const code = codeOf(error);
                        if (code !== "MODULE_NOT_FOUND") {
                          throw new Error(label + " expected MODULE_NOT_FOUND but got " + code);
                        }
                        console.log("websocket." + label + "=" + code);
                        return;
                      }
                      throw new Error(label + " was unexpectedly allowed");
                    }

                    console.log("websocket.profile=" + [
                      profile.featureFlags.websocket.enabled,
                      profile.featureFlags.websocket.defaultEnabled,
                      profile.featureFlags.websocket.status,
                      profile.featureFlags.websocket.buildTimeOverrideAllowed,
                      profile.featureFlags.websocket.projectJsonOverrideAllowed,
                      profile.featureFlags.websocket.packagedApkOverrideAllowed,
                      profile.highRiskCapabilities.webSocket
                    ].join("/"));
                    console.log("websocket.profileFrozen=" + Object.isFrozen(profile.featureFlags.websocket));

                    expectMissing("requireBare", () => require("websocket"));
                    expectMissing("resolveBare", () => require.resolve("websocket"));
                    console.log("websocket.missing.ready=true");
                """.trimIndent(),
                sourceName = "<node-websocket-design-gate-missing.js>",
                workingDirectory = emptyWorkDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("websocket missing design gate", missingResult)
            assertTrue(
                missingResult.stdout,
                missingResult.stdout.contains("websocket.profile=false/false/planned/false/false/false/false"),
            )
            assertTrue(missingResult.stdout, missingResult.stdout.contains("websocket.profileFrozen=true"))
            assertTrue(missingResult.stdout, missingResult.stdout.contains("websocket.requireBare=MODULE_NOT_FOUND"))
            assertTrue(missingResult.stdout, missingResult.stdout.contains("websocket.resolveBare=MODULE_NOT_FOUND"))
            assertTrue(missingResult.stdout, missingResult.stdout.contains("websocket.missing.ready=true"))

            val websocketPackage = File(packageWorkDir, "node_modules/websocket").apply {
                mkdirs()
            }
            File(websocketPackage, "index.js").writeText(
                """
                    function codeOf(error) {
                      return String(error && (error.autojs6Code || error.code || error.legacyCode || error.name || ""));
                    }
                    exports.local = true;
                    exports.netCode = function() {
                      try {
                        require("net").connect(80, "127.0.0.1");
                      } catch (error) {
                        return codeOf(error);
                      }
                      return "allowed";
                    };
                    exports.tlsCode = function() {
                      try {
                        require("tls").connect(443, "127.0.0.1");
                      } catch (error) {
                        return codeOf(error);
                      }
                      return "allowed";
                    };
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )

            val localPackageResult = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const websocket = require("websocket");
                    console.log("websocket.local=" + websocket.local);
                    console.log("websocket.local.net=" + websocket.netCode());
                    console.log("websocket.local.tls=" + websocket.tlsCode());
                    console.log("websocket.local.ready=true");
                """.trimIndent(),
                sourceName = "<node-websocket-design-gate-local-package.js>",
                workingDirectory = packageWorkDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("websocket local package design gate", localPackageResult)
            assertTrue(localPackageResult.stdout, localPackageResult.stdout.contains("websocket.local=true"))
            assertTrue(localPackageResult.stdout, localPackageResult.stdout.contains("websocket.local.net=${NodeRuntimeErrorCodes.NETWORK_DISABLED}"))
            assertTrue(localPackageResult.stdout, localPackageResult.stdout.contains("websocket.local.tls=${NodeRuntimeErrorCodes.NETWORK_DISABLED}"))
            assertTrue(localPackageResult.stdout, localPackageResult.stdout.contains("websocket.local.ready=true"))
        } finally {
            emptyWorkDir.deleteRecursively()
            packageWorkDir.deleteRecursively()
        }
    }
}
