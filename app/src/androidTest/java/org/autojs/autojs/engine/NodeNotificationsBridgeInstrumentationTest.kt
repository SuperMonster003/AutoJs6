package org.autojs.autojs.engine

import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeNotificationsBridgeInstrumentationTest {

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
    fun kotlinNotificationsBridgeRegistryAndManifestAreStable() {
        listOf("notify", "cancel", "cancelAll").forEach { method ->
            assertTrue(NodeBridgeModules.supports("notifications", method))
            assertEquals(
                listOf(NodeBridgePermissionManifest.NOTIFICATIONS),
                NodeBridgePermissionManifest.requiredCapabilitiesFor("notifications", method),
            )
        }
        assertFalse(NodeBridgeModules.supports("notifications", "listen"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.ANDROID_PERMISSION_POST_NOTIFICATIONS),
            NodeBridgePermissionManifest.androidPermissionsForCapabilities(listOf("notifications")),
        )
    }

    @Test
    fun jsNotificationsBridgeUsesFakeTransportAndBlocksShadowing() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node notifications bridge fake transport regression")

        val workDir = File(context.cacheDir, "node-bridge-notifications-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create notifications bridge working directory: $absolutePath" }
        }
        File(workDir, "node_modules/notifications").apply {
            check(mkdirs()) { "Failed to create notifications shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true, notify: () => "shadow" };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  const result = request.method === "notify" ? (request.args[0].id || 7001) : null;
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result
                  })));
                }
              });

              const notifications = require("notifications");
              const id = await notifications.notify({
                id: 7001,
                title: "Node task",
                text: "Running",
                channel: "node-test",
                ongoing: true,
                priority: "low"
              }, { timeoutMs: 1000 });
              await notifications.cancel(id, { timeoutMs: 1000 });
              await notifications.cancelAll({ timeoutMs: 1000 });

              let invalid = "";
              try {
                await notifications.notify("bad");
              } catch (error) {
                invalid = String(error && (error.autojs6Code || error.code || ""));
              }

              console.log("notifications.resolve=" + require.resolve("notifications"));
              console.log("notifications.shadow=" + (notifications.shadow === true));
              console.log("notifications.frozen=" + Object.isFrozen(notifications));
              console.log("notifications.methods=" + [typeof notifications.notify, typeof notifications.cancel, typeof notifications.cancelAll].join("|"));
              console.log("notifications.id=" + id);
              console.log("notifications.invalid=" + invalid);
              console.log("notifications.requests=" + requests.map((request) => request.module + "." + request.method).join("|"));
              console.log("notifications.payload=" + [requests[0].args[0].title, requests[0].args[0].text, requests[0].args[0].channel, requests[0].args[0].ongoing, requests[0].args[0].priority].join("|"));
              console.log("notifications.permissions=" + requests[0].permissions.join("|"));
              console.log("notifications.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-notifications-fake.js>",
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("notifications fake bridge", result)
        assertTrue(result.stdout.contains("notifications.resolve=notifications"))
        assertTrue(result.stdout.contains("notifications.shadow=false"))
        assertTrue(result.stdout.contains("notifications.frozen=true"))
        assertTrue(result.stdout.contains("notifications.methods=function|function|function"))
        assertTrue(result.stdout.contains("notifications.id=7001"))
        assertTrue(result.stdout.contains("notifications.invalid=${NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST}"))
        assertTrue(result.stdout.contains("notifications.requests=notifications.notify|notifications.cancel|notifications.cancelAll"))
        assertTrue(result.stdout.contains("notifications.payload=Node task|Running|node-test|true|low"))
        assertTrue(result.stdout.contains("notifications.permissions=notifications"))
        assertTrue(result.stdout.contains("notifications.pending=0"))
    }

    @Test
    fun jsNotificationsRequiresManifestCapabilityBeforeDispatch() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node notifications manifest capability regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(emptyList())
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  requests.push(JSON.parse(message));
                }
              });
              let code = "";
              let missing = "";
              try {
                await require("notifications").notify({ title: "blocked" });
              } catch (error) {
                code = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
              }
              console.log("notifications.manifest.denied=" + [code, missing, requests.length, bridge.__test.pendingCount()].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-notifications-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodeEmbeddedTestHarness.assertSucceeded("notifications manifest capability denial", result)
        assertTrue(
            result.stdout.contains(
                "notifications.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|notifications|0|0",
            ),
        )
    }

    @Test
    fun androidProviderPostsOrReportsNotificationPermissionAndTracksOwnedResource() {
        val workDir = File(context.cacheDir, "node-bridge-notifications-provider").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create notifications provider directory: $absolutePath" }
            resolve("project.json").writeText(
                """
                {
                  "type": "node",
                  "node": {
                    "permissions": ["notifications"]
                  }
                }
                """.trimIndent(),
            )
        }
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "notifications-provider",
                sourceName = "notifications.cjs",
                cwd = workDir.absolutePath,
            ),
        )

        val notify = dispatch(
            provider,
            method = "notify",
            argsJson = """[{"id":710001,"title":"AutoJs6 Node","text":"Provider smoke","channel":"node-test","ongoing":true}]""",
        )
        if (!notify.ok) {
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, notify.error?.code)
            return
        }
        assertEquals(710001, jsonInt(notify.resultJson.orEmpty()))
        assertEquals("1", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
        assertEquals(1, activeTypeCount(provider.nativeDiagnostics(), NodeBridgeResourceType.NOTIFICATION))

        val cancel = dispatch(
            provider,
            method = "cancel",
            argsJson = "[710001]",
        )
        assertTrue(cancel.toString(), cancel.ok)
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])

        val second = dispatch(
            provider,
            method = "notify",
            argsJson = """[{"id":710002,"title":"AutoJs6 Node","text":"Provider destroy","ongoing":true}]""",
        )
        if (second.ok) {
            provider.destroy(NodeBridgeError.processDead("notifications provider destroy"))
            assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
            assertEquals("1", provider.nativeDiagnostics()["embedded_script.bridge_resource.leak_count"])
        }
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        method: String,
        argsJson: String,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "notifications-${System.nanoTime()}",
                module = "notifications",
                method = method,
                argsJson = argsJson,
                timeoutMs = 5_000L,
                permissions = listOf("notifications"),
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for notifications.$method response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing notifications.$method response" }
    }

    private fun activeTypeCount(diagnostics: Map<String, String>, type: NodeBridgeResourceType): Int {
        return JSONObject(diagnostics["embedded_script.bridge_resource.active_types"].orEmpty()).optInt(type.name, 0)
    }

    private fun jsonInt(raw: String): Int {
        return (JSONTokener(raw).nextValue() as Number).toInt()
    }
}
