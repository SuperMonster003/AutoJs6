package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeBridgePermissionManifestInstrumentationTest {

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
    fun kotlinManifestReadsProjectAndPackageCapabilitiesAndMapsAndroidPermissions() {
        val workDir = File(context.cacheDir, "node-bridge-permission-manifest").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge permission manifest test directory: $absolutePath" }
        }
        File(workDir, "project.json").writeText(
            """
            {
              "type": "node",
              "node": {
                "permissions": ["toast", "screen_capture", "app.launch", "unknown.future"]
              }
            }
            """.trimIndent(),
        )
        File(workDir, "package.json").writeText(
            """
            {
              "name": "bridge-permission-manifest",
              "autojs6": {
                "node": {
                  "permissions": ["network", "media_projection"]
                }
              }
            }
            """.trimIndent(),
        )

        val manifest = NodeBridgePermissionManifest.fromWorkingDirectory(workDir.absolutePath)

        assertTrue(manifest.enforced)
        assertEquals(
            listOf("app.launch", "network", "screen_capture", "toast", "unknown.future"),
            manifest.permissions,
        )
        assertEquals(listOf("unknown.future"), manifest.unknownPermissions)
        assertTrue(manifest.sources.contains("project.json:node.permissions"))
        assertTrue(manifest.sources.contains("package.json:autojs6.node.permissions"))
        assertEquals(
            listOf(
                NodeBridgePermissionManifest.ANDROID_PERMISSION_INTERNET,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
            ),
            manifest.androidPermissions,
        )
    }

    @Test
    fun jsManifestAllowsDeclaredToastRejectsUndeclaredAccessibilityAndReportsCapabilities() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node bridge permission manifest focused regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("toast"))
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const profile = require("autojs6:profile");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result: null
                  })));
                }
              });

              await require("toast").showToast("declared", { timeoutMs: 1000 });

              let deniedCode = "";
              let missing = "";
              try {
                await require("accessibility").isEnabled({ timeoutMs: 1000 });
              } catch (error) {
                deniedCode = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities)
                  ? error.missingCapabilities.join("|")
                  : "";
              }

              const report = bridge.permissions();
              console.log("bridge.perm.toast.requests=" + requests.length + "/" + requests[0].permissions.join("|"));
              console.log("bridge.perm.denied=" + deniedCode + "/" + missing + "/" + requests.length);
              console.log("bridge.perm.report=" + report.enforced + "/" + report.normalizedPermissions.join("|") + "/" + profile.bridgePermissions.enforced);
              console.log("bridge.perm.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-bridge-permission-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodeEmbeddedTestHarness.assertSucceeded("bridge permission manifest JS gate", result)
        assertTrue(result.stdout.contains("bridge.perm.toast.requests=1/toast"))
        assertTrue(
            result.stdout.contains(
                "bridge.perm.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}/accessibility/1",
            ),
        )
        assertTrue(result.stdout.contains("bridge.perm.report=true/toast/true"))
        assertTrue(result.stdout.contains("bridge.perm.pending=0"))
    }
}
