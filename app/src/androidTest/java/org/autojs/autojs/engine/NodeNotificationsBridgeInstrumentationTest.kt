package org.autojs.autojs.engine

import android.app.NotificationManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeNotificationsBridgeInstrumentationTest {

    private val context = NodePluginTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun kotlinNotificationsBridgeRegistryAndManifestAreStable() {
        listOf("notify", "cancel", "cancelAll", "getPermissionStatus").forEach { method ->
            assertTrue(NodeBridgeModules.supports("notifications", method))
            assertEquals(
                listOf(NodeBridgePermissionManifest.NOTIFICATIONS),
                NodeBridgePermissionManifest.requiredCapabilitiesFor("notifications", method),
            )
        }
        assertTrue(NodeBridgeModules.supports("notifications", "openSettings"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.NOTIFICATIONS, NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("notifications", "openSettings"),
        )
        assertEquals(
            NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS,
            NodeBridgePermissionManifest.normalizeCapability("notifications:settings"),
        )
        assertFalse(NodeBridgeModules.supports("notifications", "listen"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.ANDROID_PERMISSION_POST_NOTIFICATIONS),
            NodeBridgePermissionManifest.androidPermissionsForCapabilities(listOf("notifications")),
        )
    }

    @Test
    fun jsNotificationsBridgeUsesFakeTransportAndBlocksShadowing() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node notifications bridge fake transport regression")

        val workDir = File(context.cacheDir, "node-bridge-notifications-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create notifications bridge working directory: $absolutePath" }
        }
        File(workDir, "node_modules/notifications").apply {
            check(mkdirs()) { "Failed to create notifications shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true, notify: () => "shadow" };""")

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let result = null;
                  if (request.method === "notify") {
                    result = request.args[0].id || 7001;
                  } else if (request.method === "getPermissionStatus") {
                    result = {
                      schema: "autojs6-node-notification-permission-status-v1",
                      sdkInt: 35,
                      notificationsEnabled: true,
                      postNotificationsPermissionRequired: true,
                      postNotificationsPermissionGranted: true,
                      canOpenSettings: true
                    };
                  } else if (request.method === "openSettings") {
                    result = {
                      schema: "autojs6-node-notification-permission-status-v1",
                      sdkInt: 35,
                      notificationsEnabled: true,
                      postNotificationsPermissionRequired: true,
                      postNotificationsPermissionGranted: true,
                      canOpenSettings: true,
                      opened: false,
                      dryRun: request.args[0].dryRun === true,
                      action: "android.settings.APP_NOTIFICATION_SETTINGS",
                      reason: "settings-intent-dry-run"
                    };
                  }
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
              const status = await notifications.getPermissionStatus({ timeoutMs: 1000 });
              const settings = await notifications.openSettings({ dryRun: true, timeoutMs: 1000 });
              const aliasSettings = await notifications.openNotificationSettings({ dryRun: true, timeoutMs: 1000 });

              let invalid = "";
              try {
                await notifications.notify("bad");
              } catch (error) {
                invalid = String(error && (error.autojs6Code || error.code || ""));
              }

              console.log("notifications.resolve=" + require.resolve("notifications"));
              console.log("notifications.shadow=" + (notifications.shadow === true));
              console.log("notifications.frozen=" + Object.isFrozen(notifications));
              console.log("notifications.methods=" + [typeof notifications.notify, typeof notifications.cancel, typeof notifications.cancelAll, typeof notifications.getPermissionStatus, typeof notifications.openSettings, typeof notifications.openNotificationSettings].join("|"));
              console.log("notifications.id=" + id);
              console.log("notifications.status=" + [status.schema, status.notificationsEnabled, status.postNotificationsPermissionGranted].join("|"));
              console.log("notifications.settings=" + [settings.schema, settings.dryRun, aliasSettings.dryRun].join("|"));
              console.log("notifications.invalid=" + invalid);
              console.log("notifications.requests=" + requests.map((request) => request.module + "." + request.method).join("|"));
              console.log("notifications.payload=" + [requests[0].args[0].title, requests[0].args[0].text, requests[0].args[0].channel, requests[0].args[0].ongoing, requests[0].args[0].priority].join("|"));
              console.log("notifications.permissions=" + requests[0].permissions.join("|"));
              console.log("notifications.settings_permissions=" + requests.filter((request) => request.method === "openSettings").every((request) => request.permissions.join("|") === "notifications|notifications.settings"));
              console.log("notifications.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-notifications-fake.js>",
            workingDirectory = workDir,
        )

        NodePluginTestHarness.assertSucceeded("notifications fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.resolve=autojs6:notifications")
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.shadow=false")
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.frozen=true")
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.methods=function|function|function|function|function|function")
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.id=7001")
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.status=autojs6-node-notification-permission-status-v1|true|true")
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.settings=autojs6-node-notification-permission-status-v1|true|true")
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.invalid=${NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST}")
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.requests=notifications.notify|notifications.cancel|notifications.cancelAll|notifications.getPermissionStatus|notifications.openSettings|notifications.openSettings")
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.payload=Node task|Running|node-test|true|low")
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.permissions=notifications")
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.settings_permissions=true")
        NodePluginTestHarness.assertStdoutContains("notifications fake bridge", result, "notifications.pending=0")
    }

    @Test
    fun jsNotificationsRequiresManifestCapabilityBeforeDispatch() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node notifications manifest capability regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(emptyList())
        val result = NodePluginTestHarness.runPluginScript(
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

        NodePluginTestHarness.assertSucceeded("notifications manifest capability denial", result)
        assertTrue(
            result.stdout.contains(
                "notifications.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|notifications|0|0",
            ),
        )
    }

    @Test
    fun jsNotificationsSettingsRequiresExplicitManifestCapabilityBeforeDispatch() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node notifications settings manifest capability regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("notifications"))
        val result = NodePluginTestHarness.runPluginScript(
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
                await require("notifications").openSettings({ dryRun: true, timeoutMs: 1000 });
              } catch (error) {
                code = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
              }
              console.log("notifications.settings.manifest.denied=" + [code, missing, requests.length, bridge.__test.pendingCount()].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-notifications-settings-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("notifications settings manifest capability denial", result)
        assertTrue(
            result.stdout.contains(
                "notifications.settings.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|notifications.settings|0|0",
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
                    "permissions": ["notifications", "notifications.settings"]
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

        val status = dispatch(
            provider,
            method = "getPermissionStatus",
            argsJson = "[]",
        )
        assertTrue(status.toString(), status.ok)
        assertEquals("autojs6-node-notification-permission-status-v1", jsonObject(status.resultJson.orEmpty()).getString("schema"))

        val settings = dispatch(
            provider,
            method = "openSettings",
            argsJson = """[{"dryRun":true}]""",
        )
        assertTrue(settings.toString(), settings.ok)
        val settingsJson = jsonObject(settings.resultJson.orEmpty())
        assertEquals("autojs6-node-notification-permission-status-v1", settingsJson.getString("schema"))
        assertTrue(settingsJson.getBoolean("dryRun"))
        assertFalse(settingsJson.getBoolean("opened"))

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
        val activeDiagnostics = provider.nativeDiagnostics()
        assertEquals("1", activeDiagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals(1, activeTypeCount(activeDiagnostics, NodeBridgeResourceType.NOTIFICATION))
        assertNotificationResource(activeDiagnostics, channel = "node-test", ongoing = "true")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager.getNotificationChannel("node-test")
            assertNotNull(channel)
            assertEquals("node-test", channel.id)
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        }

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
                permissions = if (method == "openSettings") {
                    listOf("notifications", "notifications.settings")
                } else {
                    listOf("notifications")
                },
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

    private fun assertNotificationResource(diagnostics: Map<String, String>, channel: String, ongoing: String) {
        val dump = JSONArray(diagnostics["embedded_script.bridge_resource.dump_json"].orEmpty())
        val record = (0 until dump.length())
            .map { index -> dump.getJSONObject(index) }
            .first { item -> item.getString("type") == NodeBridgeResourceType.NOTIFICATION.name }
        val metadata = record.getJSONObject("metadata")
        assertEquals(channel, metadata.getString("channel"))
        assertEquals(ongoing, metadata.getString("ongoing"))
    }

    private fun jsonInt(raw: String): Int {
        return (JSONTokener(raw).nextValue() as Number).toInt()
    }

    private fun jsonObject(raw: String): JSONObject {
        return JSONTokener(raw).nextValue() as JSONObject
    }
}
