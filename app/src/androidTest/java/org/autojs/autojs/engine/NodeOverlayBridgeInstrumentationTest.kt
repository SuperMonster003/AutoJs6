package org.autojs.autojs.engine

import android.provider.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeOverlayBridgeInstrumentationTest {

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
    fun kotlinOverlayRegistryManifestAndProviderPocAreStable() {
        listOf("show", "update", "drainEvents", "close", "closeAll", "hasPermission", "openPermissionSettings").forEach { method ->
            assertTrue(NodeBridgeModules.supports("ui.overlay", method))
        }
        assertFalse(NodeBridgeModules.supports("ui.overlay", "rawWindow"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.UI_OVERLAY),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("ui.overlay", "show"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.UI_OVERLAY),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("ui.overlay", "closeAll"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.UI_OVERLAY_PERMISSION),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("ui.overlay", "hasPermission"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.UI_OVERLAY_PERMISSION),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("ui.overlay", "openPermissionSettings"),
        )
        assertTrue(NodeBridgePermissionManifest.definedCapabilities.contains(NodeBridgePermissionManifest.UI_OVERLAY_FOREGROUND))
        assertTrue(NodeBridgePermissionManifest.definedCapabilities.contains(NodeBridgePermissionManifest.UI_OVERLAY_PACKAGED))
        assertTrue(NodeBridgePermissionManifest.definedCapabilities.contains(NodeBridgePermissionManifest.FLOATY_RAW))
        assertTrue(NodeBridgePermissionManifest.definedCapabilities.contains(NodeBridgePermissionManifest.FLOATY_TOUCH))
        assertEquals(emptyList<String>(), NodeBridgePermissionManifest.androidPermissionsForCapabilities(listOf(NodeBridgePermissionManifest.UI_OVERLAY)))

        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val overlayHealth = NodeBridgeProviderRegistry.androidHealth(context)
            .first { it.definition.id == "ui.overlay" }
        assertEquals(NodeBridgeProviderStatus.DEGRADED, overlayHealth.status)
        assertEquals(0, overlayHealth.activeResourceCount)
        assertEquals("hasPermission,openPermissionSettings,closeAll", overlayHealth.details["liveGateMethods"])
        assertEquals("show,update,drainEvents,close", overlayHealth.details["p14LiveMethods"])
        assertEquals("provider_poc", overlayHealth.details["p14ProviderState"])
        assertEquals("show,update,drainEvents,close", overlayHealth.details["blockedLiveMethods"])
        assertEquals("android.permission.SYSTEM_ALERT_WINDOW", overlayHealth.details["packagedManifestPermission"])
        assertEquals("required_before_packaged_promotion", overlayHealth.details["foregroundDisclosure"])
        assertTrue(overlayHealth.details["rawWindowLeakPolicy"].orEmpty().contains("WindowManager"))

        val permission = dispatch(
            provider,
            method = "hasPermission",
            permissions = listOf(NodeBridgePermissionManifest.UI_OVERLAY_PERMISSION),
        )
        assertTrue(permission.toString(), permission.ok)
        val permissionJson = JSONObject(permission.resultJson.orEmpty())
        assertEquals("p14_08_provider_poc", permissionJson.getString("providerState"))
        assertEquals("android.permission.SYSTEM_ALERT_WINDOW", permissionJson.getString("packagedManifestPermission"))
        assertEquals("required_before_packaged_promotion", permissionJson.getString("foregroundDisclosure"))
        assertEquals(0, permissionJson.getInt("activeOverlayCount"))

        val settings = dispatch(
            provider,
            method = "openPermissionSettings",
            argsJson = """[{"dryRun":true}]""",
            permissions = listOf(NodeBridgePermissionManifest.UI_OVERLAY_PERMISSION),
        )
        assertTrue(settings.toString(), settings.ok)
        val settingsJson = JSONObject(settings.resultJson.orEmpty())
        assertFalse(settingsJson.getBoolean("opened"))
        assertTrue(settingsJson.getBoolean("dryRun"))
        assertEquals("settings-intent-dry-run", settingsJson.getString("reason"))

        val closeAll = dispatch(
            provider,
            method = "closeAll",
            permissions = listOf(NodeBridgePermissionManifest.UI_OVERLAY),
        )
        assertTrue(closeAll.toString(), closeAll.ok)
        val closeAllJson = JSONObject(closeAll.resultJson.orEmpty())
        assertEquals(0, closeAllJson.getInt("closedCount"))
        assertEquals(0, closeAllJson.getInt("remainingCount"))
        assertFalse(closeAllJson.getBoolean("rawWindowLeaked"))

        val show = dispatch(
            provider,
            method = "show",
            argsJson = """[{"content":{"type":"Column","children":[{"id":"status","type":"Text","text":"Overlay"}]},"window":{"width":240,"height":120,"x":24,"y":96,"touchable":true,"focusable":false},"disclosure":{"title":"AutoJs6 overlay","text":"Script overlay is visible"}}]""",
            permissions = listOf(NodeBridgePermissionManifest.UI_OVERLAY),
        )
        // P13 historical verifier token: ui.overlay live creation must remain fail-closed after the P12-14 live gate
        if (Settings.canDrawOverlays(context)) {
            // P14-08 conditional positive path: show/update/drain/close.
            assertTrue(show.toString(), show.ok)
            val showJson = JSONObject(show.resultJson.orEmpty())
            val overlayId = showJson.getString("id")
            assertTrue(overlayId.startsWith("overlay-"))
            assertEquals("overlay", showJson.getString("type"))
            assertFalse(showJson.getBoolean("closed"))
            assertTrue(showJson.getBoolean("visible"))
            assertEquals("1", provider.nativeDiagnostics().getValue("embedded_script.overlay_active_session_count"))

            val update = dispatch(
                provider,
                method = "update",
                argsJson = """["$overlayId",{"id":"status","text":"Done"}]""",
                permissions = listOf(NodeBridgePermissionManifest.UI_OVERLAY),
            )
            assertTrue(update.toString(), update.ok)
            val events = dispatch(
                provider,
                method = "drainEvents",
                argsJson = """["$overlayId",{"maxEvents":8}]""",
                permissions = listOf(NodeBridgePermissionManifest.UI_OVERLAY),
            )
            assertTrue(events.toString(), events.ok)
            assertTrue(events.resultJson.orEmpty().contains("shown"))
            assertTrue(events.resultJson.orEmpty().contains("update"))
            val close = dispatch(
                provider,
                method = "close",
                argsJson = """["$overlayId",{"reason":"test"}]""",
                permissions = listOf(NodeBridgePermissionManifest.UI_OVERLAY),
            )
            assertTrue(close.toString(), close.ok)
            assertEquals("true", close.resultJson)
            assertEquals("0", provider.nativeDiagnostics().getValue("embedded_script.overlay_active_session_count"))
        } else {
            assertFalse("ui.overlay live creation must fail closed without SYSTEM_ALERT_WINDOW", show.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, show.error?.code)
            assertEquals(NodeBridgeErrorCategory.PERMISSION_DENIED, show.error?.category)
            assertTrue(show.error?.message.orEmpty().contains("SYSTEM_ALERT_WINDOW"))
            assertEquals("0", provider.nativeDiagnostics().getValue("embedded_script.overlay_active_session_count"))
        }

        listOf(
            NodeExecutionModePolicy.nativeValues(NodeExecutionModePolicy.MODE_ONE_SHOT),
            NodeExecutionModePolicy.nativeValues(
                NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
                NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
            ),
            NodeExecutionModePolicy.nativeValues(
                NodeExecutionModePolicy.MODE_SCHEDULED,
                NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
            ),
        ).forEach { nativeValues ->
            assertFalse(
                nativeValues.getValue("embedded_script.execution_mode_allowed_providers"),
                nativeValues.getValue("embedded_script.execution_mode_allowed_providers").split(",").contains("ui.overlay"),
            )
        }
    }

    @Test
    fun jsOverlayUsesFakeTransportAndBlocksShadowing() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node ui.overlay fake transport regression")

        val workDir = File(context.cacheDir, "node-bridge-ui-overlay-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create ui.overlay bridge working directory: $absolutePath" }
        }
        File(workDir, "node_modules/ui.overlay").apply {
            check(mkdirs()) { "Failed to create ui.overlay shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true, show: () => "shadow" };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              let drained = false;
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let result = null;
                  if (request.method === "show") {
                    result = {
                      id: "overlay-fake-1",
                      type: "overlay",
                      closed: false,
                      visible: true,
                      createdAt: 123,
                      bounds: { x: 24, y: 96, width: 240, height: 120 },
                      disclosure: { title: request.args[0].disclosure.title },
                      droppedCount: 0
                    };
                  } else if (request.method === "update") {
                    result = {
                      id: request.args[0],
                      type: "overlay",
                      closed: false,
                      visible: true,
                      createdAt: 123,
                      bounds: { x: 24, y: 96, width: 240, height: 120 }
                    };
                  } else if (request.method === "drainEvents") {
                    result = drained ? [] : [{
                      type: "click",
                      overlayId: request.args[0],
                      targetId: "stop",
                      eventTime: 456
                    }];
                    drained = true;
                  } else if (request.method === "close") {
                    result = true;
                  } else if (request.method === "closeAll") {
                    result = { closedCount: 0, remainingCount: 0 };
                  } else if (request.method === "hasPermission") {
                    result = { granted: false, canOpenSettings: true, reason: "fake" };
                  } else if (request.method === "openPermissionSettings") {
                    result = { opened: true, reason: "fake" };
                  }
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result
                  })));
                }
              });

              const overlay = require("ui.overlay");
              const handle = await overlay.show({
                content: {
                  type: "Column",
                  children: [
                    { id: "status", type: "Text", text: "Running" },
                    { id: "stop", type: "Button", text: "Stop" }
                  ]
                },
                window: { width: 240, height: 120, x: 24, y: 96, touchable: true, focusable: false, visible: true },
                disclosure: { title: "AutoJs6 overlay", text: "Script overlay is visible" }
              }, { timeoutMs: 1000 });
              const updated = await handle.update({ id: "status", text: "Done" }, { timeoutMs: 1000 });
              const events = await handle.drainEvents({ maxEvents: 8, timeoutMs: 1000 });
              const closed = await handle.close({ timeoutMs: 1000 });
              const closedAgain = await handle.close({ timeoutMs: 1000 });
              const closeAll = await overlay.closeAll({ timeoutMs: 1000 });
              const permission = await overlay.hasPermission({ timeoutMs: 1000 });
              const settings = await overlay.openPermissionSettings({ timeoutMs: 1000 });

              let invalid = "";
              try {
                await overlay.show({
                  content: { type: "Text", text: "Hidden" },
                  window: { visible: false },
                  disclosure: { title: "Hidden", text: "Hidden overlay" }
                });
              } catch (error) {
                invalid = String(error && (error.autojs6Code || error.code || ""));
              }

              console.log("overlay.resolve=" + require.resolve("ui.overlay"));
              console.log("overlay.shadow=" + (overlay.shadow === true));
              console.log("overlay.frozen=" + Object.isFrozen(overlay));
              console.log("overlay.handle=" + [handle.id, handle.type, handle.closed, Object.isFrozen(handle)].join("|"));
              console.log("overlay.updated=" + [updated.id, updated.visible, Object.isFrozen(updated)].join("|"));
              console.log("overlay.events=" + [events[0].type, events[0].targetId, Object.isFrozen(events), Object.isFrozen(events[0])].join("|"));
              console.log("overlay.close=" + [closed, closedAgain, handle.closed].join("|"));
              console.log("overlay.closeAll=" + [closeAll.closedCount, closeAll.remainingCount, Object.isFrozen(closeAll)].join("|"));
              console.log("overlay.permission=" + [permission.granted, permission.canOpenSettings, Object.isFrozen(permission)].join("|"));
              console.log("overlay.settings=" + [settings.opened, Object.isFrozen(settings)].join("|"));
              console.log("overlay.invalid=" + invalid);
              console.log("overlay.requests=" + requests.map((request) => request.module + "." + request.method).join("|"));
              console.log("overlay.permissions=" + requests.map((request) => request.permissions.join("+")).join("|"));
              console.log("overlay.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-ui-overlay-fake.js>",
            workingDirectory = workDir,
            timeoutMs = 30_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("ui.overlay fake bridge", result)
        assertTrue(result.stdout.contains("overlay.resolve=ui.overlay"))
        assertTrue(result.stdout.contains("overlay.shadow=false"))
        assertTrue(result.stdout.contains("overlay.frozen=true"))
        assertTrue(result.stdout.contains("overlay.handle=overlay-fake-1|overlay|true|true"))
        assertTrue(result.stdout.contains("overlay.updated=overlay-fake-1|true|true"))
        assertTrue(result.stdout.contains("overlay.events=click|stop|true|true"))
        assertTrue(result.stdout.contains("overlay.close=true|false|true"))
        assertTrue(result.stdout.contains("overlay.closeAll=0|0|true"))
        assertTrue(result.stdout.contains("overlay.permission=false|true|true"))
        assertTrue(result.stdout.contains("overlay.settings=true|true"))
        assertTrue(result.stdout.contains("overlay.invalid=${NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST}"))
        assertTrue(result.stdout.contains("overlay.requests=ui.overlay.show|ui.overlay.update|ui.overlay.drainEvents|ui.overlay.close|ui.overlay.closeAll|ui.overlay.hasPermission|ui.overlay.openPermissionSettings"))
        assertTrue(result.stdout.contains("overlay.permissions=ui.overlay|ui.overlay|ui.overlay|ui.overlay|ui.overlay|ui.overlay.permission|ui.overlay.permission"))
        assertTrue(result.stdout.contains("overlay.pending=0"))
    }

    @Test
    fun jsOverlayRequiresManifestCapabilityBeforeDispatch() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node ui.overlay manifest capability regression")

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
                await require("ui.overlay").show({
                  content: { type: "Text", text: "Denied" },
                  disclosure: { title: "Denied", text: "Denied overlay" }
                }, { timeoutMs: 1000 });
              } catch (error) {
                code = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
              }
              console.log("overlay.manifest.denied=" + [code, missing, requests.length, bridge.__test.pendingCount()].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-ui-overlay-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodeEmbeddedTestHarness.assertSucceeded("ui.overlay manifest denial", result)
        assertTrue(
            result.stdout,
            result.stdout.contains("overlay.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|ui.overlay|0|0"),
        )
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        method: String,
        argsJson: String = "[]",
        permissions: List<String> = emptyList(),
    ): NodeBridgeResponse {
        lateinit var response: NodeBridgeResponse
        val latch = CountDownLatch(1)
        provider.dispatch(
            NodeBridgeRequest(
                id = "ui-overlay-$method",
                module = "ui.overlay",
                method = method,
                argsJson = argsJson,
                timeoutMs = 1000L,
                permissions = permissions,
            ),
        ) {
            response = it
            latch.countDown()
        }
        assertTrue("Timed out waiting for ui.overlay.$method response", latch.await(5, TimeUnit.SECONDS))
        return response
    }
}
